package io.github.aixtin.nyrallite.contract

/**
 * 系统通知发送契约：让模型能主动发通知给用户。
 *
 * 宿主（Nyral Lite 主包）实现这个接口，插件注册实现或调用都只认这个契约，
 * 不碰 Android NotificationManager——避免在插件里重复创建 channel、权限请求等样板代码。
 *
 * 权限声明：插件在 PluginManifest.permissions 里加 Permission.FILES 即可。
 */
interface NotificationContract {

    /**
     * 发送一条通知。
     *
     * @param title 通知标题（会被截断到 64 字，避免垃圾字符炸 UI）
     * @param body  通知正文（会被截断到 400 字）
     * @param channelId 通知渠道 ID，宿主负责创建和管理默认渠道
     * @param tag   可选 tag，用于后续 cancel 指定通知
     * @param id    可选 id，用于后续 cancel 指定通知
     */
    fun send(
        title: String,
        body: String,
        channelId: String = "nyral_generic",
        tag: String? = null,
        id: Int = System.currentTimeMillis().toInt(),
    )

    /** 取消指定通知。 */
    fun cancel(
        tag: String? = null,
        id: Int = System.currentTimeMillis().toInt(),
    )
}
