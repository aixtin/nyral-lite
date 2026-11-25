package io.github.aixtin.nyrallite.contract

/**
 * Intent 调度契约：让模型能安全调起其他 App / 发送广播 / 拨号。
 *
 * 宿主实现这个接口，内部统一做安全检查（必须显式 Intent 或白名单 Action），
 * 插件只调契约，不碰 Android Intent API——避免在插件里乱写 Intent 引发安全问题。
 *
 * 权限声明：插件在 PluginManifest.permissions 里加 Permission.NETWORK 或新增系统级权限。
 */
interface IntentContract {

    /**
     * 安全调起其他 App 的 Activity。
     *
     * @param packageName 目标 App 包名
     * @param activityClass 目标 Activity 类全名（可选，null 时用 Action + Category 匹配）
     * @param action  Intent action（如 Intent.ACTION_VIEW）
     * @param dataUri URI 数据（如 tel:123456 / https://example.com）
     * @param extras 可选键值对（String 类型，其他类型宿主拒绝）
     * @return 是否成功调起（false 表示目标 App 不存在或被宿主安全策略拦截）
     */
    fun startActivity(
        packageName: String? = null,
        activityClass: String? = null,
        action: String? = null,
        dataUri: String? = null,
        extras: Map<String, String> = emptyMap(),
    ): Boolean

    /**
     * 拨号（会触发系统拨号器，用户确认后才真正拨出）。
     *
     * @param phoneNumber 手机号/座机号（宿主负责格式清洗）
     */
    fun dial(phoneNumber: String): Boolean

    /**
     * 打开浏览器访问 URL。
     *
     * @param url 必须是 http(s) URL，宿主负责校验协议
     */
    fun openBrowser(url: String): Boolean

    /**
     * 发分享 Intent（系统选择器）。
     *
     * @param text 要分享的文本
     * @param title 分享选择器标题
     */
    fun shareText(text: String, title: String = "分享给"): Boolean
}
