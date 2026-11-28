package io.github.aixtin.nyrallite

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.aixtin.nyrallite.contract.ToolRegistry
import io.github.aixtin.nyrallite.contract.ToolResult
import io.github.aixtin.nyrallite.kernel.getOrNull
import io.github.aixtin.nyrallite.plugins.js.JsSandboxSelfTest
import io.github.aixtin.nyrallite.plugins.wasm.WasmHttpFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 工具自测的执行侧：权限检查 → 调 ToolRegistry → 回调结果。 */
internal class ToolTestRunner(
    private val activity: AppCompatActivity,
    private val scope: CoroutineScope,
    private val onResult: (String, ToolResult, Boolean) -> Unit,
) {
    /** 权限申请期间缓存的目标工具，拿到结果后再执行。 */
    private var pending: Pair<String, String>? = null

    private val notifLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pending ?: return@registerForActivityResult
        pending = null
        if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) execute(request)
        else onResult(request.first, ToolResult(content = "通知权限未授予", isError = true), false)
    }

    private val locationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val request = pending ?: return@registerForActivityResult
        pending = null
        if (grants.values.any { it }) execute(request)
        else onResult(request.first, ToolResult(content = "定位权限未授予", isError = true), false)
    }

    fun run(spec: TestSpec) {
        val request = spec.name to spec.args
        if (needsNotifPermission(spec.name) && !hasNotifPermission()) {
            pending = request
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (spec.name == "get_location" && !hasLocationPermission()) {
            pending = request
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        execute(request)
    }

    private fun execute(request: Pair<String, String>) {
        val (name, args) = request
        if (name == "js_sandbox") {
            // JS 沙箱自测（M1）：不进 ToolRegistry，直接跑固定脚本
            scope.launch {
                try {
                    onResult(name, ToolResult(content = "运行中…"), true)
                    val result = withContext(Dispatchers.IO) {
                        JsSandboxSelfTest.run(httpFetcher = { url, offset -> WasmHttpFetcher.fetch(url, offset) })
                    }
                    onResult(name, result, false)
                } catch (t: Throwable) {
                    onResult(name, ToolResult(content = t.message ?: "unknown", isError = true), false)
                }
            }
            return
        }
        val registry = activity.kernel.services.getOrNull<ToolRegistry>()
        if (registry == null) {
            onResult(name, ToolResult(content = "ToolRegistry 未注册", isError = true), false)
            return
        }
        scope.launch {
            try {
                onResult(name, ToolResult(content = "运行中…"), true)
                onResult(name, registry.call(name, args), false)
            } catch (t: Throwable) {
                onResult(name, ToolResult(content = t.message ?: "unknown", isError = true), false)
            }
        }
    }

    /** 有些工具依赖通知权限：send_notification / start_foreground_task。 */
    private fun needsNotifPermission(toolName: String): Boolean =
        toolName == "send_notification" || toolName == "start_foreground_task"

    private fun hasNotifPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}
