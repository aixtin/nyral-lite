package io.github.aixtin.nyrallite

import android.app.Application
import io.github.aixtin.nyrallite.contract.AttachmentStores
import io.github.aixtin.nyrallite.contract.MultiToolRegistry
import io.github.aixtin.nyrallite.contract.SecurityMode
import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.contract.SessionStore
import io.github.aixtin.nyrallite.contract.ToolApproval
import io.github.aixtin.nyrallite.contract.ToolRegistry
import io.github.aixtin.nyrallite.contract.ToolSource
import io.github.aixtin.nyrallite.kernel.Kernel
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.get
import io.github.aixtin.nyrallite.kernel.getAll
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.kernel.provide
import io.github.aixtin.nyrallite.plugins.attachmentstore.PrivateAttachmentStore
import io.github.aixtin.nyrallite.plugins.markdown.MarkdownPlugin
import io.github.aixtin.nyrallite.plugins.mcpclient.McpPlugin
import io.github.aixtin.nyrallite.plugins.memorystore.MemoryPlugin
import io.github.aixtin.nyrallite.plugins.modelopenai.OpenAiPlugin
import io.github.aixtin.nyrallite.plugins.personastore.PersonaPlugin
import io.github.aixtin.nyrallite.plugins.security.SecurityPolicyPlugin
import io.github.aixtin.nyrallite.plugins.skills.SkillPlugin
import io.github.aixtin.nyrallite.plugins.storageprefs.PrefsPlugin
import io.github.aixtin.nyrallite.plugins.storageprefs.SharedPrefsConfigBackend
import io.github.aixtin.nyrallite.plugins.system.SystemToolsPlugin
import io.github.aixtin.nyrallite.plugins.dex.DexPluginLoader
import io.github.aixtin.nyrallite.plugins.dex.ExternalPluginStore
import io.github.aixtin.nyrallite.plugins.dex.TrustedApkStore
import io.github.aixtin.nyrallite.plugins.voicestt.VoiceSttPlugin
import io.github.aixtin.nyrallite.plugins.js.JsToolSource
import io.github.aixtin.nyrallite.plugins.js.ToolForgeSource
import io.github.aixtin.nyrallite.plugins.wasm.WasmHttpFetcher
import io.github.aixtin.nyrallite.plugins.wasm.WasmToolSource
import io.github.aixtin.nyrallite.plugins.system.NotificationSenderImpl

/**
 * 组合根：全 App 唯一装配 kernel + 内置插件的地方。
 * 主体与插件之间不互相 import 具体实现，只认 kernel / contract。
 */
class App : Application() {

    lateinit var kernel: Kernel
        private set

    /** 工具执行前的用户确认界面；由 MainActivity 挂上前台界面后生效。 */
    val approval = ToolApprovalDialog()

    /** 内置插件清单：插件状态页只读展示，不暴露插件实例。 */
    lateinit var pluginManifests: List<PluginManifest>
        private set

    /** 用户显式信任的插件 APK 文件哈希。 */
    val trustedApks: TrustedApkStore by lazy { TrustedApkStore(this) }

    /** Dex 动态插件加载器：宿主单例，信任对象是插件 APK 文件本身。 */
    val dexLoader: DexPluginLoader by lazy {
        DexPluginLoader(this, trustedApks)
    }

    /** 外部插件清单持久化：记录哪些插件已安装、是否启用。 */
    val externalPlugins: ExternalPluginStore by lazy { ExternalPluginStore(this) }

    /** 附件主存储：私有目录 + 内容寻址，公共目录只做显式导出。 */
    val attachmentStore: PrivateAttachmentStore by lazy {
        PrivateAttachmentStore(java.io.File(filesDir, "attachments"))
    }

    override fun onCreate() {
        super.onCreate()
        val plugins = listOf(
            PrefsPlugin(),
            OpenAiPlugin(),
            MarkdownPlugin(),
            SecurityPolicyPlugin(),
            McpPlugin(),
            SystemToolsPlugin(),
            SkillPlugin(),
            MemoryPlugin(),
            PersonaPlugin(),
            VoiceSttPlugin(),
        )
        pluginManifests = plugins.map { it.manifest }
        kernel = Kernel(this, SharedPrefsConfigBackend(this)).apply {
            // 附件字节读写：必须早于插件安装，model-openai 安装时就要解析它
            services.provide(AttachmentStores::class, AttachmentStores(attachmentStore))
            // 主体提供、插件消费：安全门禁弹确认对话框时从服务容器取
            services.provide(ToolApproval::class, approval)
            // 标准插件按序接入：storage-prefs → model-openai → render-markdown → security-policy → mcp-client → system-tools
            install(plugins)
            // WASM 插件工具来源：已安装且带工具元数据的插件注册为模型可调用工具
            services.provide(
                ToolSource::class,
                WasmToolSource(
                    appContext = this@App,
                    notifier = { title, body ->
                        runCatching { NotificationSenderImpl(this@App).send(title, body, "nyral_generic") }.isSuccess
                    },
                    httpFetcher = { url -> WasmHttpFetcher.fetch(url) },
                ),
                requires = emptySet(),
            )
            // JS 工具来源：已装 JS 工具（含模型自造草稿）注册为模型可调用工具
            services.provide(
                ToolSource::class,
                JsToolSource(
                    appContext = this@App,
                    httpFetcher = { url, offset -> WasmHttpFetcher.fetch(url, offset) },
                ),
                requires = emptySet(),
            )
            // 模型自造工具铸造器：模型写脚本落盘（草稿）+ 定稿确认（复用主体审批通道）；
            // 门禁放行档下定稿免弹窗，档位每次现查
            services.provide(
                ToolSource::class,
                ToolForgeSource(
                    appContext = this@App,
                    approval = approval,
                    securityMode = {
                        services.getOrNull<SecuritySettingsStore>()?.load()?.mode ?: SecurityMode.AUTO
                    },
                ),
                requires = emptySet(),
            )
            // 聚合所有 ToolSource（MCP 远程工具 + 系统原生工具 + 以后的 WebView 工具）
            services.provide(
                ToolRegistry::class,
                MultiToolRegistry(gates) { services.getAll<ToolSource>() },
            )
            start()
        }
        // 必须在 kernel 赋值完成后调用：apply 块内 kernel 属性尚未初始化
        restoreExternalPlugins()
        pruneAttachments()
    }

    /**
     * 附件 GC：异步扫一遍所有会话，删掉无人引用的图片。
     * 删会话不直接删文件，靠这里兜底；扫描失败时宁可不动。
     */
    private fun pruneAttachments() {
        val sessions = kernel.services.getOrNull<SessionStore>() ?: return
        Thread { pruneNow(sessions) }.apply { isDaemon = true }.start()
    }

    private fun pruneNow(sessions: SessionStore) {
        val live = runCatching {
            sessions.list()
                .mapNotNull { sessions.load(it.id) }
                .flatMap { session -> session.messages }
                .flatMap { msg -> msg.attachments }
                .filter { it.store == PrivateAttachmentStore.STORE_ID }
                .map { it.key }
                .toSet()
        }.getOrNull() ?: return
        attachmentStore.prune(live)
    }

    /**
     * 冷启动恢复外部插件：只恢复 enabled 的记录。
     * 加载失败（APK 被删/签名变化）只记日志，不阻塞启动，也不自动改记录。
     */
    private fun restoreExternalPlugins() {
        for (record in externalPlugins.all()) {
            if (!record.enabled) continue
            val loaded = runCatching { dexLoader.loadFromFile(record.id) }.getOrNull() ?: continue
            kernel.installOne(loaded.plugin)
        }
    }
}
