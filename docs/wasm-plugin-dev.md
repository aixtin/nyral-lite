# Nyral Lite WASM 插件开发指南

> 仓库地址：https://github.com/aixtin/nyral-lite
> 适用版本：Nyral Lite `0.1.x`（Android，minSdk 31 / targetSdk 36）
> 文档目的：让第三方开发者写一个 **WASM 插件**（单个 `.wasm` 文件），装进 App 后能向模型提供工具（tool calling）。
> 仓库内参考实现：`examples/wasm/echo_tool.wat`（WAT 版示例）、`examples/wasm/echo_tool_rs/`（Rust 版示例），两者均有测试覆盖。

---

## 0. 先搞清楚三件事

1. **插件是一个 `.wasm` 文件**，不是 APK。它跑在宿主内置的沙箱解释器（Chasm）里，不接触 Android API。
2. **插件没有任何隐式能力**。读文件、发网络请求、发通知，都必须通过 `nyral.*` 导入函数请求——**导入即声明，声明即弹窗**：没声明的能力在模块层面不存在，网关之外无路可走。
3. **插件最常见的用途是给模型加工具**：带 `nyral.tool` 元数据段的插件会被注册成模型可调用的工具，名字是 `wasm_<插件id>_<工具名>`。

> 只想对接一个现成的 HTTP 接口？不用写插件：起一个 MCP 服务器，在 App 的「设置 → MCP 服务器」里填地址即可。

## 1. WASM 插件还是 Dex APK 插件？

| 项目 | WASM 插件（本篇） | Dex APK 插件 |
|---|---|---|
| 文件形态 | 单个 `.wasm` 文件 | 完整 Android APK |
| 运行方式 | 宿主内置沙箱解释器，与宿主隔离 | `DexClassLoader`，与宿主同进程同权限 |
| 能力获取 | 只能用声明的 `nyral.*` 导入，逐项弹窗、逐项审计 | 宿主身份运行，可读宿主私有数据、可联网 |
| 信任模型 | 文件 SHA-256 + 安装期能力声明弹窗 | 文件 SHA-256 + 证书指纹弹窗 |
| 适用场景 | **第三方默认通道**：结构化计算、数据处理、受控外部请求 | 全功能高信任通道（自用/审计过的插件） |
| 入口 | 插件页「导入 WASM」按钮 | 默认隐藏：连点插件页标题「插件」15 次（3 秒内）解锁 |

拿不准就选 WASM：能力最小化、审计可见、出事也炸不出沙箱。

## 2. 运行模型与安全边界

| 项目 | 说明 |
|---|---|
| 运行位置 | 宿主 App 进程内的 Chasm 解释器，插件代码碰不到 Android API |
| 数据通道 | 插件线性内存是唯一通道；宿主函数持有读写权，参数与结果都从这里过 |
| 信任对象 | 插件文件整体的 SHA-256，绑定插件 id（文件名去扩展名），每次加载前重验 |
| 未信任文件 | 导入时弹窗展示工具信息、能力声明与文件哈希，用户确认后才登记；**更新后哈希变化需重新确认** |
| 卸载 | 删文件 + 吊销信任登记；重装同一文件会因哈希对不上重新弹窗 |
| 执行超时 | 单次工具执行 10 秒；插件死循环会被放弃，不能拖死对话 |
| 能力网关 | 四个能力导入（`log`/`read_file`/`notify`/`http_fetch`）+ 两个调用通道导入（`get_args`/`put_result`）；宿主回调缺省时对应能力一律拒绝并审计 |

**性能提示**（A14 真机实测）：热执行约为原生解释器开销的 1.3–1.5 倍，首次实例化慢约 9 倍。插件适合"参数进、结果出"的短任务，不适合常驻循环。测耗时须亮屏且前台——后台进程被系统冻结时计时同样被冻结。

## 3. 最小可运行插件（10 分钟跑通）

### 3.1 工具链

装 [WABT](https://github.com/WebAssembly/wabt)（提供 `wat2wasm`）：

```bash
# Debian/Ubuntu
apt install wabt
# macOS
brew install wabt
```

### 3.2 完整示例：`echo_tool.wat`

把模型传入的参数原样回显。仓库里就是这份文件：`examples/wasm/echo_tool.wat`。

```wat
;; echo_tool.wat —— Nyral Lite WASM 插件最小示例：把模型传入的参数原样回显。
;; 构建：wat2wasm --enable-annotations echo_tool.wat -o echo_tool.wasm
(module
  ;; ── 工具元数据：安装期硬校验，模型侧工具清单从这里来 ──
  (@custom "nyral.tool"
    "{\"name\":\"echo\",\"description\":\"把调用参数原样返回（文档示例）\",\"parameters\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\",\"description\":\"要回显的文本\"}},\"required\":[\"text\"]},\"entry\":\"tool_call\"}")

  ;; ── 能力导入：声明了才会出现在安装确认弹窗；未声明的能力在模块层面不存在 ──
  (import "nyral" "log" (func $log (param i32 i32)))
  (import "nyral" "get_args" (func $get_args (param i32) (result i32)))
  (import "nyral" "put_result" (func $put_result (param i32 i32) (result i32)))

  ;; ── 线性内存：必须导出，是插件与宿主之间唯一的数据通道 ──
  (memory (export "memory") 1)

  ;; 偏移 0 放日志文本；偏移 1024 起是 read_file / http_fetch 的约定结果缓冲区
  (data (i32.const 0) "echo_tool called")

  (func (export "tool_call") (result i32)
    (local $n i32)
    ;; 1. 把调用参数 JSON 读进内存 1024 处，返回字节数
    (local.set $n (call $get_args (i32.const 1024)))
    ;; 2. 记一条日志（会出现在插件审计里）
    (call $log (i32.const 0) (i32.const 16))
    ;; 3. 把结果交回宿主：示例直接原样回显参数。
    ;;    真实插件在这里解析参数、执行逻辑、拼出自己的结果 JSON
    (drop (call $put_result (i32.const 1024) (local.get $n)))
    (i32.const 0)))
```

构建：

```bash
wat2wasm --enable-annotations echo_tool.wat -o echo_tool.wasm
```

> `--enable-annotations` 是必须的：`(@custom ...)` 语法靠它开启。

### 3.3 装进手机

1. 把 `echo_tool.wasm` 传到手机（下载、网盘都行）。
2. App → 插件页 → **导入 WASM** → 选中文件。
3. 弹窗会显示：工具名（`wasm_echo_tool_echo`）、工具描述、能力声明（本例是 `nyral.log`；`get_args`/`put_result` 是调用通道，不进能力声明）、文件哈希。点确认。
4. 对模型说一句话触发工具调用，比如"用 echo 工具回显 hello"。模型侧看到的工具名是 `wasm_echo_tool_echo`。

## 4. `nyral` 导入函数详解

所有函数都在模块 `nyral` 下。**只导入你需要的**——安装弹窗按导入展示能力声明，多声明一行就多一行要向用户解释的能力。

### 4.1 `log(ptr: i32, len: i32)`

把插件内存 `[ptr, ptr+len)` 的 UTF-8 文本记进插件审计日志。无返回值。调试和留痕都用它。

### 4.2 `read_file(path_ptr: i32, path_len: i32) -> i32`

请求读取一个文件。**真实效果由宿主策略决定**：路径必须在宿主配置的允许列表内，且宿主提供了文件源。

| 返回值 | 含义 |
|---|---|
| `>= 0` | 成功，内容长度；内容在内存偏移 **1024** 处 |
| `-1` | 路径不在允许列表（拒绝） |
| `-2` | 内容超过 4096 字节（拒绝） |
| `-3` | 宿主未提供文件源（拒绝） |

### 4.3 `notify(title_ptr, title_len, body_ptr, body_len) -> i32`

请求发一条系统通知。返回 `0` 成功；`-1` 被宿主拒绝或未授权。

### 4.4 `http_fetch(url_ptr, url_len) -> i32`

请求发起一次 HTTP GET。真实请求由宿主发起（含策略与超时），插件拿不到 socket。

| 返回值 | 含义 |
|---|---|
| `>= 0` | 成功，响应体长度；内容在内存偏移 **1024** 处 |
| `-1` | 被宿主拒绝或请求失败 |
| `-2` | 响应超过 4096 字节（拒绝） |

> **`read_file` 与 `http_fetch` 共用偏移 1024 的结果缓冲区**，后写覆盖先写。要同时用两者，先取走的先消费。

### 4.5 `get_args(dest: i32) -> i32`

把**本次工具调用的参数 JSON** 写进插件内存 `dest` 处，返回字节数。返回 `0` 表示无参数；`-1` 表示参数超过 8192 字节（不会写入）。

### 4.6 `put_result(src: i32, len: i32) -> i32`

把执行结果交给宿主：结果 JSON 在插件内存 `[src, src+len)` 处。返回 `0` 成功；`-1` 表示 `len` 非法或超过 4096——此时结果整体作废，宁可失败也不交付半截结果。

**入口函数必须调用过 `put_result`**，否则模型收到"未交回执行结果"错误。

## 5. 工具元数据（`nyral.tool` 自定义段）

自定义段 `nyral.tool` 的内容是一段 JSON，安装期硬校验，**视为不可信输入**：

```json
{
  "name": "echo",
  "description": "把调用参数原样返回（文档示例）",
  "parameters": {
    "type": "object",
    "properties": {
      "text": { "type": "string", "description": "要回显的文本" }
    },
    "required": ["text"]
  },
  "entry": "tool_call"
}
```

| 字段 | 必填 | 规则 |
|---|---|---|
| `name` | 是 | 工具名：小写字母开头，只含小写字母、数字、下划线，最长 32 字符 |
| `description` | 是 | 给模型看的工具说明，非空且不超过 500 字符。写清楚"做什么、参数含义、返回什么" |
| `parameters` | 否 | 参数 JSON Schema，必须是 JSON 对象；缺省视为 `{}`（无参数） |
| `entry` | 否 | 入口导出名：字母或下划线开头，只含字母数字下划线，最长 64 字符；缺省 `tool_call`。**必须真实存在于模块导出**，否则拒绝安装 |

校验是严格的：JSON 必须符合 RFC 8259（无引号字符串、单引号键都会被拒）；`entry` 指向的导出不存在也拒绝安装。**没有 `nyral.tool` 段的插件是纯计算插件**：装得上，但不会出现在模型工具列表里。

模型侧工具名是 `wasm_<插件id>_<工具名>`（插件 id 即文件名去扩展名）。插件 id 与工具名都可能含下划线，名字不靠分隔符还原——调用时查表分发，模型编造的名字会得到明确的"工具未找到"。

## 6. 内存约定

- **必须导出 `memory`**。宿主与插件的所有数据交换都经过它。
- 偏移 `0–1023`：插件自由使用（放常量、日志文本等）。
- 偏移 `1024` 起：`read_file` / `http_fetch` 的约定结果缓冲区，上限 4096 字节。
- `get_args` 的 `dest`、`put_result` 的 `src` 由插件自定，建议避开 1024 起的缓冲区（示例用 1024 是因为不用那两个能力）。
- 内存按 WASM 页（64 KiB）申请，`(memory 1)` 即 64 KiB，按需加大。

## 7. 用 Rust 写插件（进阶）

WAT 适合小插件；逻辑复杂了建议用 Rust 编译到 `wasm32-unknown-unknown`（`no_std`）。完整可构建的示例在仓库里：`examples/wasm/echo_tool_rs/`（与第 3.2 节的 WAT 示例行为一致，测试覆盖）。

`Cargo.toml` 关键项：

```toml
[lib]
crate-type = ["cdylib"]

[profile.release]
panic = "abort"
```

`src/lib.rs` 骨架：

```rust
#![no_std]

use core::panic::PanicInfo;

/// 工具元数据：安装期硬校验，模型侧工具清单从这里来
const META: &str = r#"{"name":"echo","description":"把调用参数原样返回（文档示例）","parameters":{},"entry":"tool_call"}"#;

#[link_section = "nyral.tool"]
#[used]
static META_BYTES: [u8; META.len()] = { /* 逐字节拷贝 META.as_bytes()，见示例 */ };

/// 能力导入：声明了才会出现在安装确认弹窗；未声明的能力在模块层面不存在
#[link(wasm_import_module = "nyral")]
extern "C" {
    fn log(ptr: u32, len: u32);
    fn get_args(dest: u32) -> i32;
    fn put_result(src: u32, len: u32) -> i32;
}

#[no_mangle]
pub extern "C" fn tool_call() -> i32 {
    let n = unsafe { get_args(4096) };
    if n > 0 {
        unsafe { put_result(4096, n as u32) };
    }
    0
}

#[panic_handler]
fn panic(_: &PanicInfo) -> ! {
    loop {}
}
```

要点：

- **导入**：`#[link(wasm_import_module = "nyral")]` 的 `extern "C"` 块，函数签名与第 4 节一致。
- **元数据**：`#[link_section = "nyral.tool"]` + `#[used]` 的静态字节数组放元数据 JSON；段内容就是纯 JSON 文本，不要补结尾 `\0`。
- **入口**：`#[no_mangle] pub extern "C" fn tool_call() -> i32`。
- **内存**：`wasm32-unknown-unknown` 默认导出名为 `memory` 的线性内存，无需额外配置（已验证）。
- **panic 处理**：`no_std` 必须自带 `#[panic_handler]`；配 `panic = "abort"` 更省体积。

构建与安装：

```bash
cargo build --target wasm32-unknown-unknown --release
# 产物：target/wasm32-unknown-unknown/release/echo_tool.wasm
```

之后走第 3.3 节的安装流程。

## 8. 安装、更新与卸载

| 操作 | 行为 |
|---|---|
| 安装 | 选文件 → 解析能力声明与元数据 → 弹窗确认 → 登记哈希并保存到 `wasm_plugins/` |
| 重复导入同一文件 | 哈希一致，直接安装（不再弹窗） |
| 更新 | 重新导入同名文件，哈希变化 → 重新弹窗确认后才生效 |
| 卸载 | 插件列表里移除：删文件 + 吊销信任登记 |
| 调用审计 | 每次能力请求（允许与拒绝）和 `log` 都进插件审计日志 |

## 9. 排错

| 现象 | 原因与处理 |
|---|---|
| 导入失败：不是有效的 WASM 插件 | 文件损坏或不是 WASM；用 `wat2wasm` 重新构建 |
| 导入失败：自定义段不是合法 JSON | `nyral.tool` 段内容不是严格 RFC 8259 JSON；检查引号与转义 |
| 导入失败：工具名/入口导出名不合法 | 不符合第 5 节的正则；改名 |
| 导入失败：入口导出 X 不存在于模块 | `entry` 写错或忘了 `(export ...)` |
| 执行超时（>10000ms） | 插件死循环或等待不可能到来的外部事件；插件内不要自旋等待 |
| 未交回执行结果 | 入口函数没调 `put_result`，或 `put_result` 因超限被拒 |
| 插件 X 文件哈希与登记不符 | 文件更新过但没重新确认；重新导入确认 |
| 模型说工具未找到 | 工具名是 `wasm_<插件id>_<工具名>`；确认插件已安装且元数据校验通过 |

## 附录 A：仓库内可直接参考的文件

| 文件 | 内容 |
|---|---|
| `examples/wasm/echo_tool.wat` | WAT 版示例，测试覆盖（`WasmDocExampleTest`） |
| `examples/wasm/echo_tool_rs/` | Rust 版示例，测试覆盖（`WasmDocExampleTest`） |
| `app/src/main/java/.../plugins/wasm/WasmPluginGateway.kt` | 能力网关：导入函数的权威语义与返回码 |
| `app/src/main/java/.../plugins/wasm/WasmPluginMeta.kt` | 元数据解析与校验规则 |
| `app/src/main/java/.../plugins/wasm/WasmToolExecutor.kt` | 执行流程与超时保护 |
| `app/src/main/java/.../PluginWasmImporter.kt` | 安装流程与信任确认弹窗 |
| `app/src/test/java/.../plugins/wasm/` | 全部 WASM 插件行为的测试用例 |
