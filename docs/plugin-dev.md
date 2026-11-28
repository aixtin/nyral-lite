# Nyral Lite 插件开发指南

> 仓库地址：https://github.com/aixtin/nyral-lite
> 适用版本：Nyral Lite `0.1.x`（Android，minSdk 31 / targetSdk 36，Kotlin JVM 17）
> 文档目的：让第三方开发者写一个 **Dex APK 插件**，装进 App 后能向模型提供工具（tool calling）。
> 仓库内参考实现：`testplugin/`（最小 HelloPlugin）、`app/src/main/java/.../plugins/`（内置插件）。

> **两条插件通道**：本篇讲 Dex APK 插件（全功能高信任通道，入口默认隐藏：连点插件页标题「插件」15 次解锁）。
> 第三方插件请走 WASM 通道：单个 `.wasm` 文件、沙箱隔离、能力按导入声明，见 **[wasm-plugin-dev.md](wasm-plugin-dev.md)**。

---

## 0. 先搞清楚三件事

1. **插件是一个 APK**，不是 aar/so。App 用 `DexClassLoader` 加载它，和 App 跑在同一进程。
2. **插件通过"契约接口"和宿主交互**。宿主在运行时提供这些接口的实现；插件只依赖接口，不依赖 App 的实现类。
3. **插件最常见的用途是给模型加工具**：实现 `ToolSource`，注册进宿主的服务容器，模型就能调用它。

> 只想对接一个现成的 HTTP 接口？不用写 APK：起一个 MCP 服务器，在 App 的「设置 → MCP 服务器」里填地址即可。

---

## 1. 运行模型与安全边界

| 项目 | 说明 |
|---|---|
| 运行位置 | 宿主 App 进程内，共享同一个 UID、内存与 Android 权限 |
| 加载方式 | `DexClassLoader`，父 ClassLoader 是宿主的（所以插件能直接 import 契约接口、用宿主已带的依赖） |
| 签名信任 | 按插件 APK 文件整体的 SHA-256 信任（绑定插件 id），每次加载前重验，文件被改即拒载 |
| 未签名/陌生签名 | 导入时弹窗显示包名、版本、证书指纹与文件哈希，用户点"信任并安装"后才加载；插件更新后需重新确认 |
| 能做什么 | 注册工具（`ToolSource`）、消费宿主能力（通知/剪贴板/定位/Intent/前台任务）、读写自己的命名空间配置、订阅事件、注册拦截器 |
| 不能做什么 | 修改聊天 UI、绕过安全门禁（工具调用建议主动过 `GateBus`，见 4.4） |

**风险提示**：插件代码以宿主身份运行，可读宿主私有数据、可联网。只安装可信来源的插件；文档里所有示例都不要直接跑来源不明的 APK。

---

## 2. 最小可运行插件（10 分钟跑通）

### 2.1 工程结构

```
now-plugin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/
    │   └── plugin-entry.txt        ← 唯一一行：Plugin-Entry: <入口类全名>
    └── kotlin/
        ├── io/github/aixtin/nyrallite/kernel/   ← 宿主契约副本（原样复制，不改）
        │   ├── Plugin.kt
        │   ├── PluginContext.kt
        │   ├── PluginManifest.kt
        │   ├── Permission.kt
        │   ├── ServiceRegistry.kt
        │   ├── PluginConfig.kt
        │   ├── PluginPanel.kt        ← 需要配置面板才复制
        │   ├── GateBus.kt
        │   ├── EventBus.kt
        │   └── Disposable.kt
        ├── io/github/aixtin/nyrallite/contract/ ← 只复制你要用的
        │   ├── ToolSource.kt
        │   ├── ToolSpec.kt
        │   ├── ToolResult.kt
        │   └── ToolCallRequest.kt
        └── com/example/nowplugin/
            └── NowPlugin.kt
```

> **为什么要复制契约源码？** 插件编译时需要这些类型；运行时 `DexClassLoader` 会优先用宿主里的同名类（parent-first），所以两边包名、类名、方法签名必须完全一致。**不要改包名，不要加/删接口方法。** 宿主侧给接口新增成员时必须带默认实现（`get() = ...` 或默认方法体）并同步契约副本，否则旧插件运行时会抛 `AbstractMethodError`。
> 仓库里的 `testplugin/src/main/kotlin/io/github/aixtin/nyrallite/kernel/` 就是一份可直接抄的副本。

### 2.2 `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.nowplugin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.nowplugin"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug { isMinifyEnabled = false }   // 混淆会改类名，入口类必须 keep
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // 不需要 kotlin-stdlib 打包：宿主已有，运行时用宿主的
    implementation("androidx.core:core-ktx:1.15.0")
}

// 入口声明写在 src/main/assets/plugin-entry.txt，AGP 会在签名前把它打进 APK。
// 不要用 zip 往已签名 APK 里追加文件：那会破坏 v2/v3 签名，宿主哈希校验不过会拒绝加载。
```

`src/main/assets/plugin-entry.txt`（冒号后一个空格）：

```
Plugin-Entry: com.example.nowplugin.NowPlugin
Contract-Version: 1.0
```

`Contract-Version` 是可选行，声明插件编译针对的宿主契约版本（见宿主
`contract/ContractVersion.kt`）。缺省视为 1.0；宿主只校验大版本，大版本
不匹配的插件拒绝加载，小版本差异放行。

构建：

```bash
./gradlew assembleDebug
# 产物：build/outputs/apk/debug/now-plugin-debug.apk
# 自检入口行是否存在：
unzip -p build/outputs/apk/debug/now-plugin-debug.apk assets/plugin-entry.txt
```

### 2.3 `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 只用 aapt 打包；宿主不读插件的 AndroidManifest，权限由宿主 App 统一声明 -->
    <application />
</manifest>
```

### 2.4 入口类

```kotlin
package com.example.nowplugin

import android.util.Log
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest

class NowPlugin : Plugin {
    // id 必须全局唯一，建议用反域名/短横线风格；version 用于展示与升级记录
    override val manifest = PluginManifest(
        id = "com.example.now-plugin",
        version = "0.1.0",
    )

    override fun install(ctx: PluginContext) {
        Log.i("NowPlugin", "installed, id=${ctx.pluginId}")
    }
}
```

要求：

- 类必须是 `public`、有**无参构造函数**、实现 `Plugin`；
- `assets/plugin-entry.txt` 里的 `Plugin-Entry` 写的就是这个类的全限定名；
- `install()` 里只做注册，**不要**做网络/磁盘预热（安装必须快）。

### 2.5 安装到手机

```bash
adb push build/outputs/apk/debug/now-plugin-debug.apk /sdcard/Download/
```

「导入 APK」入口默认隐藏：在「插件」页**连点标题「插件」15 次（3 秒内）**即可显示，解锁一次后永久生效。然后：点「导入 APK」→ 选择文件 →（未信任的插件会弹确认，展示包名、版本、证书指纹与文件哈希）→ 信任并安装。
安装成功后插件列表出现记录，默认启用。

> 仅 debug 构建的宿主对与宿主同签名的插件免弹窗；release 一律弹窗。信任绑定插件 id 与文件哈希，插件每次更新（哈希变化）都会重新弹窗确认。

### 2.6 验证

```bash
adb logcat -s NowPlugin NyralDexLoader
```

能看到 `installed, id=...` 即加载成功。

---

## 3. 契约详解

### 3.1 `Plugin` 与 `PluginManifest`

```kotlin
interface Plugin {
    val manifest: PluginManifest
    fun install(ctx: PluginContext)   // 抛异常 = 安装失败，内核会自动回滚已注册的 effect
}

data class PluginManifest(
    val id: String,                                   // 唯一 id（必填）
    val version: String = "1.0.0",
    val permissions: Set<Permission> = emptySet(),    // NETWORK / FILES / CLIPBOARD
    val dependsOn: Set<String> = emptySet(),          // 依赖的其他插件 id（拓扑排序用）
    val builtin: Boolean = false,                     // 外部插件保持 false
    val disableable: Boolean = true,
)
```

`permissions` 是**声明式**的：插件要联网就写 `NETWORK`、要读写文件就写 `FILES`，并在 `install()` 里 `ctx.require(...)` 自检；未声明时访问受限服务会抛 `SecurityException`。

### 3.2 `PluginContext`

```kotlin
interface PluginContext {
    val pluginId: String
    val appContext: Context     // 用于拿系统服务 / 建 View
    val services: ServiceRegistry
    val events: EventBus
    val gates: GateBus
    val config: PluginConfig
    fun require(permission: Permission)
    fun effect(onDispose: () -> Unit): Disposable
}
```

- `config`：`getString/put/remove`，key 自动加插件命名空间，插件之间互不可见；
- `effect`：注册需要逆操作的资源（线程、定时器、监听器、事件订阅）。**卸载/安装失败时会自动按逆序执行**。

### 3.3 生命周期

```
导入 APK → 验签 → 实例化入口类 → kernel.installOne(plugin)
        → install(ctx) 注册工具/服务/事件
卸载/禁用 → 内核按逆序回收插件注册的服务与 effect → 释放 ClassLoader
同 id 再导入 → 先卸载旧版本（回收它的服务与 effect）→ 装新版本
冷启动 → App 读取「已启用」记录 → 重新加载 APK 并 install
```

`install()` 只跑一次；不要把一次性初始化写成 `object {}` 里的静态块（可能早于上下文就绪）。

---

## 4. 给模型提供工具（核心）

### 4.1 三个类型

```kotlin
interface ToolSource {
    suspend fun list(): List<ToolSpec>
    suspend fun callOrNull(name: String, argumentsJson: String): ToolResult?  // 不认识的名字返回 null
}

data class ToolSpec(
    val name: String,             // 建议 snake_case + 前缀，如 demo_now
    val description: String,      // 模型据此决定何时调用，要写清用途与边界
    val parametersJson: String,   // JSON Schema 字符串
)

data class ToolResult(
    val content: String,          // 回填给模型的内容
    val isError: Boolean = false, // 业务失败用 true，不要抛异常
)
```

宿主的 `MultiToolRegistry` 会把所有 `ToolSource` 聚合起来：**同名工具先注册者优先**，所以名字务必带前缀，避免和内置工具（`send_notification`、`read_clipboard` 等）或 MCP 工具撞名。

### 4.2 完整示例：提供 `demo_now` 工具

```kotlin
package com.example.nowplugin

import io.github.aixtin.nyrallite.contract.ToolCallRequest
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.contract.ToolSpec
import io.github.aixtin.nyrallite.kernel.GateResult
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NowPlugin : Plugin {
    override val manifest = PluginManifest(id = "com.example.now-plugin", version = "0.1.0")

    override fun install(ctx: PluginContext) {
        ctx.services.provide(ToolSource::class, NowTools(ctx))
    }
}

private class NowTools(private val ctx: PluginContext) : ToolSource {

    private val spec = ToolSpec(
        name = "demo_now",
        description = "返回手机当前的本地日期与时间。用户问现在几点、今天几号时使用；不用于计算时间差。",
        parametersJson = """{"type":"object","properties":{},"additionalProperties":false}""",
    )

    override suspend fun list(): List<ToolSpec> = listOf(spec)

    override suspend fun callOrNull(name: String, argumentsJson: String): ToolResult? {
        if (name != spec.name) return null
        // 安全门禁：让用户在「自动」档下确认；被拦截直接返回错误结果
        when (val gate = ctx.gates.check(ToolCallRequest(ctx.pluginId, name, argumentsJson))) {
            is GateResult.Block -> return ToolResult("安全策略拦截：${gate.reason}", isError = true)
            GateResult.Allow -> Unit
        }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return ToolResult(now)
    }
}
```

### 4.3 参数 Schema 与描述写法

```json
{
  "type": "object",
  "properties": {
    "query":  { "type": "string",  "description": "要搜索的关键词" },
    "limit":  { "type": "integer", "description": "最多返回几条，默认 5" }
  },
  "required": ["query"],
  "additionalProperties": false
}
```

- `parametersJson` 原样透传给模型端点；解析失败会退化成空对象，所以不要写注释、不要写尾逗号。
- `description` 决定模型会不会调用。写"做什么 + 什么时候用 + 不适用于什么"。
- 参数在 `callOrNull` 里用 `org.json.JSONObject(argumentsJson)` 自己解析，缺参返回 `ToolResult("缺少参数 xxx", isError = true)`。

### 4.4 必须过安全门禁

宿主的安全策略挂在 `GateBus` 的 `tool.before-call` 上：**"自动"档下会弹窗让用户确认，超时/拒绝则拦截**。
内置的 MCP 工具已经这样做了；**插件工具必须自己调用 `ctx.gates.check(ToolCallRequest(...))`**，否则会绕过门禁（也是审计日志的记录点）。

### 4.5 线程与超时

- `list()` / `callOrNull()` 由宿主在 IO 线程调用，可以放心做阻塞 IO；
- 但请自己设超时（例如 10–30 秒），失败返回 `isError = true`；
- 一次模型回复可能连续发起多个工具调用，单轮最多 5 个工具轮次（宿主限制）。

---

## 5. 调用宿主能力

宿主已经把这些能力注册成服务，插件直接 `ctx.services.get(...)` 使用。它们注册时 **不需要额外权限**（`requires = emptySet()`），但对应的 Android 运行时权限仍需用户在系统里授予。

| 契约 | 典型用途 | 注意 |
|---|---|---|
| `NotificationContract` | 发/撤通知 | Android 13+ 需要用户授予通知权限 |
| `ClipboardContract` | 读写剪贴板 | Android 10+ 只有前台 App 能读 |
| `LocationContract` | 获取定位 | 需要定位权限；`hasPermission()` 先判断 |
| `IntentContract` | 调起 Activity / 拨号 / 开浏览器 / 分享 | 宿主统一做安全校验 |
| `ForegroundContract` | 跑长任务（定时、轮询） | 宿主用预埋的前台服务托管，插件不用声明 Service |

示例（发通知 + 起前台任务）：

```kotlin
val notifier = ctx.services.get<NotificationContract>()
notifier.send(title = "插件示例", body = "任务已启动")

val fg = ctx.services.get<ForegroundContract>()
fg.startTask(
    taskId = "now-plugin-timer",
    title = "示例任务运行中",
    body = "每 15 分钟提醒一次",
    params = mapOf("interval" to "15"),
    task = object : ForegroundContract.Task {
        override suspend fun run(params: Map<String, String>) {
            // 长任务逻辑；注意自行处理取消与异常
        }
    },
)
ctx.effect { fg.stopTask("now-plugin-timer") }  // 卸载时自动停
```

---

## 6. 配置持久化与配置面板

### 6.1 读写配置

```kotlin
val endpoint = ctx.config.getString("endpoint", "")
ctx.config.put("endpoint", "https://example.com/api")
```

key 自动带插件命名空间（`<pluginId>/<key>`），不同插件互不干扰，卸载不会自动清理（需要时自己 `remove`）。

> 约定：**每次使用时重新读配置**（不要在 `install()` 里读一次缓存），这样面板保存后下一次调用立即生效，无需重启。

### 6.2 声明式配置面板（不用写 UI）

外部插件不能自带 Activity，配置界面由宿主统一渲染。插件只要额外实现 `ConfigurablePlugin`：

```kotlin
class NowPlugin : Plugin, ConfigurablePlugin {
    // ... manifest / install 同前

    override fun panel(): PanelSpec = PanelSpec(
        title = "Now 插件配置",
        fields = listOf(
            PanelField.Text(key = "api_base", label = "API 地址", placeholder = "https://example.com/api"),
            PanelField.Secret(key = "api_key", label = "API Key"),
            PanelField.Switch(key = "verbose", label = "详细日志", default = false),
            PanelField.Select(
                key = "lang",
                label = "结果语言",
                options = listOf(
                    PanelField.Select.Option("zh", "中文"),
                    PanelField.Select.Option("en", "英文"),
                ),
                default = "zh",
            ),
        ),
    )
}
```

| 字段类型 | 宿主控件 | 存储值 |
|---|---|---|
| `Text` | 单行输入框 | 原文 |
| `Secret` | 密码输入框（默认打码，可勾选显示明文） | 原文 |
| `Switch` | 开关 | `"true"` / `"false"` |
| `Select` | 下拉单选 | 选中项的 `value` |

规则与坑：

- 「设置」按钮只在插件**已安装（运行中）**时出现；禁用后按钮隐藏，先启用再改配置。
- 未实现 `ConfigurablePlugin` 或 `panel()` 抛异常时不显示按钮，也不影响插件本身运行（渲染崩溃被隔离）。
- `key` 与 `ctx.config` 共用同一个命名空间：面板存进去的值，插件代码直接 `getString` 就能读到。
- **不要在 `install()` 里因为配置为空就抛异常**：那会导致安装失败、连面板入口都进不去。读到空值时用默认值，等工具被调用时再提示用户去配置。
- `Secret` 字段不要回显到工具结果或日志里（示例插件只回显 API 地址是否配置）。
- 复杂交互（地图选点、文件选择等）暂不开放自定义 View，只有 `Text/Secret/Switch/Select` 四种；确实需要时再和宿主一起设计。

---

## 7. 事件与拦截（进阶）

```kotlin
// 广播事实（不可拦截）
ctx.effect { ctx.events.on(SomeEvent::class) { e -> /* ... */ }.dispose() }

// 拦截（可阻止），例如给所有工具调用加审计
ctx.effect {
    ctx.gates.intercept(ToolCallRequest::class, priority = 100) { req ->
        GateResult.Allow   // 或 GateResult.Block("原因")
    }.dispose()
}
```

> 谨慎使用拦截器：优先级高、返回 Block 会直接改变其他工具的行为。

---

## 8. 安装 / 升级 / 卸载

| 操作 | 行为 |
|---|---|
| 导入 | 复制到 `files/plugins/<pluginId>.apk`，验签，实例化入口类，热加载 |
| 启用/禁用 | 禁用 = 从内核卸载并回收该插件注册的服务，保留 APK；启用 = 重新加载 |
| 移除 | 卸载并删除 APK 与记录 |
| 冷启动 | 只恢复 `enabled = true` 的记录 |
| **同 id 重复导入（升级）** | 自动按升级处理：先卸载旧版本（回收它的服务与 effect），再装新版本、覆盖版本记录；无需手动移除，无需重启 |
| 服务回收 | 插件 `provide` 的实例与 `effect` 都归属该插件，卸载/禁用/升级时逆序移除，不会残留在工具清单里 |

迭代插件的流程：

```
改代码 → 构建（版本号递增）→「插件」页再次导入同 id 的 APK → 聊天里让模型再调一次工具
```

> 升级时插件会被完整重装：`install()` 会重新执行，上一版本注册的工具/服务/事件都会被回收，不需要你自己清理。

---

## 9. 调试与排错

| 现象 | 原因 / 处理 |
|---|---|
| 导入提示"不是有效的插件 APK" | APK 小于 1KB、或读不到签名 |
| 导入提示"APK 未受信任或入口类未找到" | 先看 logcat `NyralDexLoader` 的 `Caused by`：① `NoClassDefFoundError: kotlin.*` 说明宿主 release 包把标准库裁掉了（宿主已 `keep kotlin.**`，旧版本会出）；② `entry class instantiate failed` 多为插件复制的契约与宿主版本不一致；③ 都没有则是 `assets/plugin-entry.txt` 缺失或入口类名拼错 |
| 导入提示"入口类未找到" | `assets/plugin-entry.txt` 缺失或 `Plugin-Entry:` 拼错（旧版兼容 `META-INF/MANIFEST.MF`），或入口类不在 APK 里 |
| 导入提示"install() 抛异常" | 插件 `install()` 内部报错，logcat 有堆栈；修复后重新导入即可 |
| 弹"未信任的插件" | 该插件（按 id + 文件哈希）尚未信任；核对弹窗里的包名、版本、证书指纹与文件哈希后信任即可。插件更新后哈希变化会再次弹窗；debug 宿主对与宿主同签名的插件免弹窗，release 一律弹窗 |
| 插件装了但工具没出现 | 没 `provide(ToolSource::class, ...)`；或 `list()` 抛异常（看 logcat）；或工具被同名工具覆盖 |
| 调用时报 `NoSuchMethodError` / `AbstractMethodError` | 插件里复制的契约与宿主版本不一致（宿主升级过接口） |
| 调用时报 `ClassNotFoundException` | 插件里引用了宿主没有的类；把依赖打进 APK，或改用宿主自带能力 |
| 插件的工具被门禁拦截 | 正常行为；用户点允许，或在门禁设置里加白名单 |
| 禁用/移除后工具还在 | 正常版本会立即回收；若仍出现，说明 App 版本过旧或插件重复 `provide`，抓 logcat 反馈 |

调试命令：

```bash
adb logcat -s NyralDexLoader NowPlugin          # 加载与插件日志
adb shell dumpsys package io.github.aixtin.nyrallite | head -5
```

> App 的「插件 → 自测」页只覆盖**内置系统工具**，不会列出插件工具。验证插件工具要靠聊天里让模型调用（例如"调用 demo_now 告诉我现在几点"）。

---

## 10. 兼容性与打包纪律

1. **契约签名不可改**：新增能力由宿主加方法/新接口，插件跟随升级；不要在插件里改接口。
2. **入口类必须 keep**：如果开混淆，加规则：
   ```proguard
   -keep class com.example.nowplugin.NowPlugin { <init>(); }
   ```
3. **避免打进宿主已有的依赖**：`kotlin-stdlib`、`androidx.*` 宿主已有，重复打包可能引发类版本冲突（父加载器优先，行为以宿主版本为准）。
4. **包名随意，插件 id 唯一**；APK 的 `applicationId` 不参与插件身份判定。
5. **targetSdk 与宿主一致（36）、minSdk ≥ 31**；Android 13+ 要求 APK 文件只读（宿主已处理）。
6. **APK 必须 > 1KB**。
7. 插件内不要长期持有 `Activity` 引用；需要 UI 用 `appContext` 或申请前台任务。

---

## 11. 安全须知（给插件作者和安装者）

- 插件与宿主同 UID：**它能读你的聊天记录、人设、配置**；
- 只安装可信来源；首次导入务必核对证书指纹；
- 工具调用前请过 `GateBus`，让用户有确认与拦截的机会；
- 不要在插件里做静默联网上报。

---

## 附录 A：仓库内可直接参考的文件

仓库地址：https://github.com/aixtin/nyral-lite

| 用途 | 路径 |
|---|---|
| 最小插件工程（含契约副本与 `assets/plugin-entry.txt`） | `testplugin/` |
| 契约定义 | `app/src/main/java/io/github/aixtin/nyrallite/kernel/` |
| 配置面板契约（`ConfigurablePlugin` / `PanelSpec` / `PanelField`） | `app/src/main/java/io/github/aixtin/nyrallite/kernel/PluginPanel.kt` |
| 工具契约 | `app/src/main/java/io/github/aixtin/nyrallite/contract/ToolSource.kt` 等 |
| 内置工具示例（系统能力包装成工具） | `app/src/main/java/io/github/aixtin/nyrallite/plugins/system/SystemToolSource.kt` |
| 远程 MCP 工具示例（含门禁与超时） | `app/src/main/java/io/github/aixtin/nyrallite/plugins/mcpclient/McpToolRegistry.kt` |
| 动态加载与验签 | `app/src/main/java/io/github/aixtin/nyrallite/plugins/dex/` |
| 导入流程与信任弹窗 | `app/src/main/java/io/github/aixtin/nyrallite/PluginApkImporter.kt` |

## 附录 B：契约最小集（原样复制，勿改）

```kotlin
// kernel/Plugin.kt
interface Plugin {
    val manifest: PluginManifest
    fun install(ctx: PluginContext)
}

// kernel/PluginContext.kt
interface PluginContext {
    val pluginId: String
    val appContext: android.content.Context
    val services: ServiceRegistry
    val events: EventBus
    val gates: GateBus
    val config: PluginConfig
    fun require(permission: Permission)
    fun effect(onDispose: () -> Unit): Disposable
}

// kernel/ServiceRegistry.kt
interface ServiceRegistry {
    fun <T : Any> provide(type: kotlin.reflect.KClass<T>, instance: T, requires: Set<Permission> = emptySet())
    fun <T : Any> get(type: kotlin.reflect.KClass<T>): T
    fun <T : Any> getOrNull(type: kotlin.reflect.KClass<T>): T?
    fun <T : Any> getAll(type: kotlin.reflect.KClass<T>): List<T>
}

// contract/ToolSource.kt
interface ToolSource {
    suspend fun list(): List<ToolSpec>
    suspend fun callOrNull(name: String, argumentsJson: String): ToolResult?
}
```

---

## 交付检查清单

- [ ] 入口类实现 `Plugin`，public + 无参构造
- [ ] APK 内 `assets/plugin-entry.txt` 有 `Plugin-Entry: <类全名>`（旧版兼容 `META-INF/MANIFEST.MF`）
- [ ] `PluginManifest.id` 唯一、版本号递增
- [ ] `ToolSpec.name` 带前缀、不与内置/MCP 工具重名
- [ ] `ToolSpec.description` 写清"什么时候用"
- [ ] `callOrNull` 只处理自己的名字，其他返回 `null`
- [ ] 业务失败返回 `ToolResult(isError = true)`，不抛异常
- [ ] 调用前过 `ctx.gates.check(ToolCallRequest(...))`
- [ ] 长任务/线程用 `ctx.effect { ... }` 注册回收
- [ ] 需要用户填参数时实现 `ConfigurablePlugin`，`install()` 不因配置为空而失败
- [ ] `Secret` 字段不回显到工具结果与日志
- [ ] 安装到手机，聊天里让模型调用一次，确认工具卡片成功
