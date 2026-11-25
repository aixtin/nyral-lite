package io.github.aixtin.nyrallite.contract

/** 一次定位结果（WGS-84）。 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timeMillis: Long,
)

/**
 * 定位能力：宿主实现，工具层消费。
 * 需要 ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION 运行时权限。
 */
interface LocationContract {

    /** 是否已获得定位权限。 */
    fun hasPermission(): Boolean

    /** 请求一次定位（最多等待 timeoutMs 毫秒）；无权限或失败返回 null。 */
    suspend fun current(timeoutMs: Long = 10_000L): LocationFix?

    /** 最近一次已知位置，不做新请求；没有缓存时返回 null。 */
    fun lastKnown(): LocationFix?
}
