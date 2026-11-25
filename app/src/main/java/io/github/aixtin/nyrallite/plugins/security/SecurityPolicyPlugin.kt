package io.github.aixtin.nyrallite.plugins.security

import io.github.aixtin.nyrallite.contract.SecuritySettingsStore
import io.github.aixtin.nyrallite.contract.ToolApproval
import io.github.aixtin.nyrallite.contract.ToolAuditLog
import io.github.aixtin.nyrallite.contract.ToolCallRequest
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.kernel.provide
import java.io.File

/**
 * 特权级标准插件：安全策略。
 *
 * 随包分发、用户不可禁用。在 tool.before-call 拦截点上按配置档位做
 * 白名单/禁止名单判定，并把每次判定写入应用私有目录的审计日志。
 * manifest 权限声明由内核 CapabilityGate 负责，不在这里重复实现。
 */
class SecurityPolicyPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "security-policy",
        builtin = true,
        disableable = false,
        permissions = setOf(Permission.FILES),
    )

    override fun install(ctx: PluginContext) {
        ctx.require(Permission.FILES)
        val audit = FileToolAuditLog(File(File(ctx.appContext.filesDir, AUDIT_DIR), AUDIT_FILE))
        ctx.services.provide(SecuritySettingsStore::class, PluginConfigSecuritySettingsStore(ctx.config))
        ctx.services.provide(ToolAuditLog::class, audit)
        val table = PolicyTable(ctx.config, ctx.services.getOrNull<ToolApproval>(), audit)
        ctx.gates.intercept(ToolCallRequest::class, priority = PRIORITY) { request ->
            table.decide(request)
        }
    }

    private companion object {
        /** 安全拦截器优先级高于普通业务拦截器。 */
        const val PRIORITY = 100

        /** 审计日志目录与文件名，需与 file_paths.xml 的导出路径保持一致。 */
        const val AUDIT_DIR = "security"
        const val AUDIT_FILE = "nyral_security_audit.log"
    }
}
