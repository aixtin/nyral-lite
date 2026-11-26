# Nyral Lite

一个轻量的 Android LLM 聊天客户端：流式对话、Markdown 与表格渲染、工具调用、人设卡和插件扩展。

## 功能

- 流式聊天，思考过程可折叠
- Markdown、代码块与表格渲染
- 工具调用：内置系统工具 + 远程 MCP（Streamable HTTP，兼容 SSE 响应）
- 人设：导入 `chara_card_v2` 角色卡（JSON，或图片内嵌数据的 PNG），支持短/长档与开场白
- 会话历史、会话摘要与长期记忆
- 动态插件：外部 Dex APK，可向模型注册自定义工具
- 安全门禁：工具执行前确认、白名单与审计日志

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
| `docs/plugin-dev.md` | 插件开发指南 |
| `AGENTS.md` | 本项目的开发约定 |

## 版本记录

### v0.1.0 · 2026-11-27 · versionCode 1

首个公开版本。

- 流式聊天与可折叠思考过程
- Markdown、代码块与表格渲染
- 工具调用：内置系统工具 + 远程 MCP
- 人设卡导入（SillyTavern v2 JSON / PNG）
- 会话历史、摘要与长期记忆
- 动态 Dex 插件扩展
- 三档安全门禁与工具审计日志
- 启动时静默检查 GitHub Release 更新
- 动态插件：release 包保留 Kotlin 标准库，debug 构建的插件可直接导入

## 插件开发

插件是一个装进 App 的动态 Dex APK，可以向模型注册工具、调用宿主能力。
完整的契约说明、最小工程、示例工具与排错表见 [docs/plugin-dev.md](docs/plugin-dev.md)。

## 许可证

MIT，见 [LICENSE](LICENSE)。安全策略见 [SECURITY.md](SECURITY.md)，隐私说明见 [PRIVACY.md](PRIVACY.md)。
