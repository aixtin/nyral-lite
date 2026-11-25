package io.github.aixtin.nyrallite.plugins.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import io.github.aixtin.nyrallite.contract.LocationContract
import io.github.aixtin.nyrallite.contract.LocationFix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 基于系统 LocationManager 的定位实现，不依赖 Google Play Services。
 *
 * 优先 GPS，其次网络定位；单次请求超时后回退到系统缓存的 lastKnownLocation，
 * 避免在室内等 GPS 不可用场景下长时间阻塞工具调用。
 */
class LocationProviderImpl(private val context: Context) : LocationContract {

    private val locationManager: LocationManager? =
        context.getSystemService(LocationManager::class.java)

    override fun hasPermission(): Boolean {
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        return coarse == PackageManager.PERMISSION_GRANTED ||
            fine == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun current(timeoutMs: Long): LocationFix? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        for (provider in providers) {
            val fix = withTimeoutOrNull(timeoutMs) { awaitSingleUpdate(manager, provider) }
            if (fix != null) return fix
        }
        return lastKnown()
    }

    override fun lastKnown(): LocationFix? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.toFix()
    }

    /** 向单个 provider 请求一次位置更新；取消时移除监听，避免泄漏。 */
    private suspend fun awaitSingleUpdate(
        manager: LocationManager,
        provider: String,
    ): LocationFix? = suspendCancellableCoroutine { cont ->
        val looper = Looper.getMainLooper()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { manager.removeUpdates(this) }
                if (cont.isActive) cont.resume(location.toFix())
            }
        }
        if (!cont.isActive) return@suspendCancellableCoroutine
        val requested = runCatching {
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, listener, looper)
            true
        }.getOrDefault(false)
        if (!requested) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
    }

    private fun Location.toFix(): LocationFix = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else -1f,
        provider = provider ?: "unknown",
        timeMillis = time,
    )
}
