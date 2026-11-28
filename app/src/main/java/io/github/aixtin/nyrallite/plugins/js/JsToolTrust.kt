package io.github.aixtin.nyrallite.plugins.js

/**
 * JS 工具运行前信任校验：只认登记哈希与实际文件哈希的关系，不认来源。
 * 无论谁改了文件——用户、root、adb 还是 AI 自己——内容一变哈希就对不上，一视同仁。
 */
sealed class JsToolTrustResult {

    /** 已登记且哈希一致，或未登记的草稿（模型自造调试期）——放行。 */
    data object Pass : JsToolTrustResult()

    /** 已登记但文件哈希对不上：拒绝运行，需重新走信任确认。 */
    data object Changed : JsToolTrustResult()
}

internal object JsToolTrust {

    /**
     * 登记哈希为空视为草稿（尚未定稿登记），放行；
     * 有登记则实际哈希必须与登记一致（忽略大小写），否则视为已变更。
     */
    fun check(registeredSha256: String?, actualSha256: String): JsToolTrustResult =
        if (registeredSha256 == null || registeredSha256.equals(actualSha256, ignoreCase = true)) {
            JsToolTrustResult.Pass
        } else {
            JsToolTrustResult.Changed
        }
}
