package io.github.aixtin.nyrallite.testplugin

import android.util.Log
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginConfig
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest

/**
 * 最小测试插件：验证 DexClassLoader 热加载管线。
 * install 啥都不注册，只打印一行 log。
 */
class HelloPlugin : Plugin {
    override val manifest = PluginManifest(
        id = "hello-test-plugin",
        version = "0.0.1",
        builtin = false,
    )

    override fun install(ctx: PluginContext) {
        Log.i("NyralDexTest", "✅ HelloPlugin installed via DexClassLoader!")
    }
}
