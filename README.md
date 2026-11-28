# Nyral Lite

<p align="center">
  <img src="docs/screenshots/chat-persona.png" width="180" alt="人设对话" />
  <img src="docs/screenshots/chat-tools.png" width="180" alt="工具调用" />
  <img src="docs/screenshots/plugins.png" width="180" alt="插件管理" />
  <img src="docs/screenshots/persona.png" width="180" alt="人设管理" />
  <img src="docs/screenshots/settings.png" width="180" alt="设置" />
</p>

一个轻量的 Android LLM 聊天客户端：流式对话、Markdown 与表格渲染、工具调用、人设卡和插件扩展。
单模块 Kotlin 工程，不依赖私有服务，填入任意兼容 Chat Completions 的服务地址即可使用。

## 功能

### 聊天与渲染
- 流式输出，思考过程可折叠查看（摘要行 + 全文抽屉）
- Markdown 单引擎渲染：标题、列表、代码块与表格，流式与收尾走同一解析路径
- 消息入场动效、空会话欢迎页、输入草稿自动保存
- 图片附件发送与全屏查看，语音输入（系统语音识别）

### 工具调用
- 内置系统工具：拨号、打开网页、发送通知、分享文本、前台任务等
- 会话历史搜索、长期记忆检索作为工具暴露给模型
- 工具执行卡片展示结果与耗时，失败可一键重试
- 远程 MCP 服务器（Streamable HTTP，兼容 SSE 响应），支持连接测试

### 人设
- 导入 `chara_card_v2` 角色卡（JSON，或图片内嵌数据的 PNG）
- 短人设（指令、性格、场景）与长人设（描述、示例、互动要求）双档
- 开场白、自定义头像；已有消息的会话切换人设时默认新建会话

### 记忆与历史
- 会话结束后自动提取长期记忆，带去重与评分
- 会话历史、全文搜索与自动摘要

### 插件（双通道）
- 内置插件随包分发：配置存储、模型接入、Markdown 渲染、安全门禁、语音识别、附件存储
- **WASM 插件（第三方默认通道）**：单个 `.wasm` 文件，跑在宿主内置沙箱解释器里；读文件、联网、发通知都按导入声明，安装时逐项弹窗、执行逐次审计
- **Dex APK 插件（全功能高信任通道）**：导入外部 Dex APK，向模型注册自定义工具，与宿主同进程运行；导入入口默认隐藏（连点插件页标题「插件」15 次、3 秒内解锁），第三方签名导入时弹窗确认
- **JS 工具（模型自造工具通道，未发版）**：单个 `.js` 文件，跑在 QuickJS 沙箱里；脚本头部 `nyral.tool` 注释声明能力与契约版本，安装确认后按声明放行，运行按需执行、跑完即焚

### 安全门禁
- 三档位：放行 / 自动（高危工具需确认）/ 严格（仅白名单放行）
- 工具白名单与禁止名单（禁止名单优先于档位）；门禁确认弹窗可一键把工具加入白/黑名单
- 插件装载期校验契约版本：大版本不兼容直接拒绝导入
- 工具审计日志，可导出排查

### 安全口径（门禁不是沙箱）

- 门禁管的是「模型能否调用某个工具」，管不了插件代码本身。两类插件边界不同：WASM 插件被沙箱隔离，碰不到 Android API；Dex APK 插件一经装载，就以本应用的全部权限运行。只装可信来源的插件。
- 插件信任绑定插件文件的 SHA-256（按插件 id 记录）：更新或回装旧版本都会因哈希变化重新弹窗确认，卸载即清除记录。证书指纹只用于弹窗展示，不作为信任依据。
- API Key 用 AES-256-GCM 加密存储，密钥生成在 AndroidKeyStore 内、不可导出；换机迁移后需重新填写。
- 更新检查只连 GitHub Releases，失败静默跳过；升级始终由用户手动确认，不做静默下载安装。

### 其他
- 启动时静默检查 GitHub Release 更新
- 前台服务保障长任务执行

## 环境要求

- Android 12 及以上（minSdk 31）
- JDK 17
- Android SDK 36

## 构建与安装

```bash
./gradlew :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次启动后在「设置 → 模型接入」填写兼容 Chat Completions 的服务地址、密钥与模型名；
需要远程工具时在「设置 → MCP 服务器」填写 MCP 地址。

## 目录

| 路径 | 说明 |
|---|---|
| `app/` | 主应用（界面、会话、聊天流、内置插件） |
| `testplugin/` | 外部 Dex 插件的最小示例工程 |
| `stressplugin/` | 真实复杂度 Dex 插件（120 类 + 4 工具），装载性能回归用 |
| `examples/wasm/` | WASM 插件示例（WAT 与 Rust no_std 两版，均有测试覆盖） |
| `docs/plugin-dev.md` | Dex APK 插件开发指南 |
| `docs/wasm-plugin-dev.md` | WASM 插件开发指南 |
| `docs/regression-checklist.md` | 发版前真机回归清单 |
| `AGENTS.md` | 本项目的开发约定 |

## 插件开发

插件向模型注册工具、调用宿主能力，有两条通道：

- **WASM 插件（第三方默认）**：单个 `.wasm` 文件，沙箱隔离，能力按导入声明。适合结构化计算、数据处理、受控外部请求。见 [docs/wasm-plugin-dev.md](docs/wasm-plugin-dev.md)。
- **Dex APK 插件（高信任）**：完整 Android 能力与原生性能，适合自用或审计过的插件。入口默认隐藏，解锁方式见文档。见 [docs/plugin-dev.md](docs/plugin-dev.md)。
- 只想对接一个现成的 HTTP 接口？不用写插件：起一个 MCP 服务器，在「设置 → MCP 服务器」里填地址即可。

## 版本记录

### v0.1.5 · 2026-11-29 · versionCode 87

- 修复：http_fetch 对超过 4KB 的响应由「整体拒收」改为「截断头部交付」——真实网页远超 4KB，此前除极小页面外全部抓取失败；模型侧工具结果本就按 4000 字截断，截断交付不损失有效信息


### v0.1.4 · 2026-11-28 · versionCode 86

- 修复：http_fetch 跟随 https 跳转（此前 301 一律「无响应」，博客等带跳转的站点抓不到），并携带常规 User-Agent / Accept 请求头


### v0.1.3 · 2026-11-28 · versionCode 84

- 模型自造工具：AI 可在对话中编写并安装自己的 JS 工具（草稿落盘 → 定稿确认 → 调用闭环），信任登记表 + 运行前哈希校验，篡改即拒绝；门禁放行档下定稿免确认
- JS 中间档 M2：脚本落盘（filesDir/js_tools/）、安装确认流、插件页「JS 工具」区（导入/运行/移除）、契约版本校验（只拦大版本）、能力声明逐项校验；引擎分发器改为句柄级注入，移除全局静态回调
- 工具轮数上限默认放宽为 8，可在模型配置页自定义（1～99）
- 新增关于页：版本与构建信息（提交哈希/构建时间）打包时自动注入，项目仓库、MIT 许可与社区入口（官网占位、博客、交流群）
- 版本号改为构建时从 git 自动取值：版本名读最近标签，版本号取提交总数

### v0.1.2 · 2026-11-28 · versionCode 4

- WASM 插件通道：能力网关（log / read_file / notify / http_fetch）、安装确认流、插件列表与卸载、工具执行入口；WAT / Rust 双示例带回归测试
- Dex APK 导入入口默认隐藏，连点插件页标题 15 次（3 秒内）解锁；插件页空区与 WASM 卡片样式修复
- 门禁：禁止名单优先于档位；确认弹窗一键加入白/黑名单；契约版本装载期校验（只拦大版本）
- 新增 `stressplugin` 真实复杂度测试插件与回归清单

### v0.1.1 · 2026-11-28 · versionCode 3

- 安全：工具门禁前移到统一分发口；API Key 改 Keystore 加密存储并自动迁移；http 端点保存前明文风险警告；插件信任绑定 APK 文件哈希；插件 ID 白名单校验
- 修复：门禁确认移到 IO 线程杜绝 ANR；审批宿主 onResume/onPause 对称登记；前台服务任务登记竞态
- 打磨：门禁弹窗说明化——工具中文名、一句用途说明、参数可读化；只读工具预置白名单
- 界面：全套视觉打磨——卡片质感、入场动效、底排图标化、门禁胶囊等
- 健壮性：Skill 注入长度预算，角色卡解压加上限
### v0.1.0 · 2026-11-27 · versionCode 1

首个公开版本。

- 流式聊天与可折叠思考过程
- Markdown、代码块与表格渲染
- 工具调用：内置系统工具 + 远程 MCP
- 人设卡导入（chara_card_v2 JSON / PNG）
- 会话历史、摘要与长期记忆
- 动态 Dex 插件扩展
- 三档安全门禁与工具审计日志
- 启动时静默检查 GitHub Release 更新
- 动态插件：release 包保留 Kotlin 标准库，debug 构建的插件可直接导入

## 许可证

MIT，见 [LICENSE](LICENSE)。安全策略见 [SECURITY.md](SECURITY.md)，隐私说明见 [PRIVACY.md](PRIVACY.md)。
