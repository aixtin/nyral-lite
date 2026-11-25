package io.github.aixtin.nyrallite.plugins.personastore

import android.content.Context
import java.io.File

/** 人设头像：PNG 角色卡导入时把原图按 id 存到 files/personas/avatars。 */
object PersonaAvatar {

    fun file(app: Context, id: String): File = File(dir(app), "$id.png")

    fun save(app: Context, id: String, bytes: ByteArray) {
        val target = file(app, id)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    fun delete(app: Context, id: String) {
        file(app, id).delete()
    }

    private fun dir(app: Context) = File(app.filesDir, "personas/avatars")
}
