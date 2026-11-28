# nyral-lite 开发规范

nyral-lite 是 Nyral 重构方案的验证支线：一个轻量 Android LLM 聊天客户端（流式对话、Markdown 渲染、工具调用、人设、长期记忆），插件走双通道——WASM 沙箱（第三方默认）+ Dex APK（高信任，入口隐藏）。
任何 AI 或人修改本项目前，先读完本文件。目标：保持小而清晰，不重蹈母体多路径失控的覆辙。

## 构建与装机

```bash
./gradlew :app:assembleDebug --console=plain
adb -s <device-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device-ip>:5555 shell am start -n io.github.aixtin.nyrallite/.MainActivity
```

（`<device-ip>` 换成 `adb devices` 里无线调试设备的实际地址。）

- 启动一律用 `am start`，禁止用 `monkey`（monkey 会打开系统自动旋转设置）。
- 连接手机只做只读或安装操作（install / logcat / dumpsys / settings get），禁止写系统设置。
- 每次改动必须编译并装机验证，不允许只改不测。回归范围见 docs/regression-checklist.md。

## 架构纪律（硬规则）

1. **渲染只有一条路**：所有 Markdown 渲染只进 `plugins/markdown/`（单引擎，流式 = 收尾同一解析路径）。禁止在 UI 层新增第二条渲染路径。
2. **SSE 解析只在一个目录**：厂商字段兼容全部集中在 `plugins/modelopenai/`（SseLineReader / OpenAiDeltaParser / OpenAiToolCallAccumulator），禁止在 Adapter / Activity 里做厂商判断。
3. **UI 状态进状态机**：思考区四态（NONE / PENDING / STREAMING / DONE）收敛在 `ChatAdapter` 的 phase 状态机，新增状态先进状态机，禁止散落 if/else。
4. **表格上限常量联动**：`TableFiller` 的行/列上限改动必须同步 `PromptDefaults.SYSTEM` 里的约束文案数值，有断言测试守护。
5. **文件行数红线约 400 行**：超出按职责拆（渲染 / 状态 / 动画分离），不按层次拆。
6. **结构性重构绑定触发条件**：工具调用已用扁平 `Message` + `toolCard` 落地；`Message` 改为有序 parts 模型（Thinking/Text/ToolCall）只在接入交错思考模型（thinking 与正文交错返回）时做，不提前。
7. **契约变更多处同步**：改 `contract/` 任何接口，必须同步 `testplugin`、`stressplugin` 的契约副本和 `docs/plugin-dev.md` 的契约说明；给接口新增成员必须带默认实现（`-Xjvm-default=all` 已启用），禁止破坏性变更。破坏性变更（改签名、删成员）必须递增 `contract/ContractVersion.kt` 的 MAJOR，插件在 `plugin-entry.txt` 用 `Contract-Version:` 声明，装载期强校验（只拦大版本）。
8. **WASM 能力网关单一入口**：插件的一切宿主能力（log / read_file / notify / http_fetch）只经 `plugins/wasm/` 的网关导入，禁止在网关外新增宿主调用路径。改网关语义、`nyral.tool` 校验规则或内存约定，必须同步 `docs/wasm-plugin-dev.md`，并保证 `WasmDocExampleTest` 通过（两个示例插件是文档的可执行副本）。
9. **WASM 与 R8**：Chasm 解释器、网关与元数据解析相关类必须保留在混淆规则里；发版前必须在 release 包真机验证 WASM 全链路（导入 → 执行 → 卸载），debug 包过了不算数。

## 已知坑（别再踩）

- markwon 依赖必须 `exclude(group = "com.atlassian.commonmark")`，与 org.commonmark 0.21 同包名冲突。
- commonmark 必须显式 `IncludeSourceSpans.BLOCKS_AND_INLINES`，否则表格 cell 取不到源码。
- `optString` 遇到 JSON null 返回字面量 `"null"`，一律先 `opt()` 判空。
- VectorDrawable 禁止空 `<path android:pathData="" />`，Android 14+ 直接闪退。
- `scrollToPosition` / `smoothScrollToPosition` 对比视口还高的条目会吸附到**条目顶部**而非底部：流式贴底跟随、回到底部按钮一律用 `scrollBy` 增量补偿（见 MainActivity.pinToBottom），禁止对末尾条目直接 scrollToPosition。
- 气泡根容器必须是 FrameLayout；LinearLayout(horizontal) 会给 WRAP_CONTENT 子 View 传 UNSPECIFIED 宽度，表格列会被挤成一字宽。
- `Message.thinkingCollapsed` 默认 true（折叠）。
- WAT 示例里的 `(@custom ...)` 元数据段必须用 `wat2wasm --enable-annotations` 编译，否则自定义段静默丢失。
- 真机测 WASM 执行耗时必须亮屏且前台：后台进程被系统冻结时计时同样被冻结。
