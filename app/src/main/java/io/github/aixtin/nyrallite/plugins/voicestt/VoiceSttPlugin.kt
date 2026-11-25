package io.github.aixtin.nyrallite.plugins.voicestt

import io.github.aixtin.nyrallite.contract.SpeechRecognizer as ContractSpeechRecognizer
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest

/**
 * 语音识别插件：默认向 kernel 注册 [SystemSpeechRecognizer]。
 * 主体只依赖契约，任何替换实现（离线 PocketSphinx、云端 Whisper、讯飞 SDK 等）
 * 只需在新插件里提供同一个 [ContractSpeechRecognizer] 即可。
 *
 * 注意：插件本身不持有具体 recognizer 实例，因为 [ContractSpeechRecognizer.Listener]
 * 由 UI 层（MainActivity）提供，需要跟随 Activity 生命周期创建和释放。
 * 插件只暴露工厂方法，主体按需调用。
 */
class VoiceSttPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "voice-stt",
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        ctx.services.provide(
            SpeechRecognizerFactory::class,
            SpeechRecognizerFactory { listener ->
                SystemSpeechRecognizer(ctx.appContext, listener)
            },
        )
    }
}

/** 工厂：主体（UI 层）创建 recognizer，listener 由其自身提供。 */
fun interface SpeechRecognizerFactory {
    fun create(listener: ContractSpeechRecognizer.Listener): ContractSpeechRecognizer
}
