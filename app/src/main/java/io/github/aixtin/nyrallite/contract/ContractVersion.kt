package io.github.aixtin.nyrallite.contract

/**
 * 宿主插件契约版本：APK 插件装载期校验用。
 *
 * - 改 contract/ 接口的破坏性变更（改签名、删成员）→ [MAJOR] +1，装载期强校验，
 *   不匹配的插件直接拒绝加载；
 * - 新增成员（必须带默认实现）→ [MINOR] +1，仅记录不拦截。
 *
 * 插件在 assets/plugin-entry.txt 里用 `Contract-Version: <MAJOR>.<MINOR>` 声明
 * 自己编译针对的版本；缺省视为 1.0（历史插件均按当前大版本处理）。
 */
object ContractVersion {
    /** 大版本：破坏性变更时 +1，装载期强校验。 */
    const val MAJOR = 1

    /** 小版本：新增成员（带默认实现）时 +1，仅提示不拦截。 */
    const val MINOR = 0
}
