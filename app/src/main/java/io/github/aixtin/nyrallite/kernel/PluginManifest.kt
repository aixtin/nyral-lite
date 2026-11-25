package io.github.aixtin.nyrallite.kernel

/**
 * 插件清单：内核唯一认可的插件元信息。
 *
 * - permissions：插件可使用的系统能力，未声明的能力运行时会被拒绝；
 * - dependsOn：安装顺序依赖，内核按拓扑排序；
 * - builtin：随包分发（编译期 Kotlin），区别于以后从索引安装的插件；
 * - disableable：是否允许用户关闭（security-policy 固定 false）。
 *
 * [needsColdStart] 返回 true 表示插件声明了 Android 组件级能力（BroadcastReceiver /
 * ForegroundService 等），热加载无法注册这些组件，必须冷启动进程才能生效。
 * 纯 Service / Tool / Event 注册类插件 needsColdStart = false，热加载立即可用。
 */
data class PluginManifest(
    val id: String,
    val version: String = "1.0.0",
    val permissions: Set<Permission> = emptySet(),
    val dependsOn: Set<String> = emptySet(),
    val builtin: Boolean = false,
    val disableable: Boolean = true,
) {
    /**
     * 是否需要冷启动进程才能完全生效。
     * 当前 Permission 类型都是运行时能力（NETWORK/FILES/CLIPBOARD），
     * 未来新增 BroadcastReceiver / ForegroundService / ContentProvider 声明时，
     * 在 [Permission] 里加组件级类型，这里同步扩展判断。
     */
    val needsColdStart: Boolean get() = false
}
