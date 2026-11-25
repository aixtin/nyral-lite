package io.github.aixtin.nyrallite.plugins.personastore

import io.github.aixtin.nyrallite.contract.PersonaStore
import io.github.aixtin.nyrallite.kernel.Permission
import io.github.aixtin.nyrallite.kernel.Plugin
import io.github.aixtin.nyrallite.kernel.PluginContext
import io.github.aixtin.nyrallite.kernel.PluginManifest
import io.github.aixtin.nyrallite.kernel.provide
import java.io.File

/** 人设插件：角色卡解析与私有文件存储，不引入 JavaScript 引擎。 */
class PersonaPlugin : Plugin {

    override val manifest = PluginManifest(
        id = "persona-store",
        permissions = setOf(Permission.FILES),
        builtin = true,
    )

    override fun install(ctx: PluginContext) {
        ctx.require(Permission.FILES)
        val store = FilePersonaStore(File(ctx.appContext.filesDir, "personas/personas.json"))
        ctx.services.provide(PersonaStore::class, store, requires = setOf(Permission.FILES))
    }
}
