package io.github.aixtin.nyrallite.contract

/**
 * 附件字节的读写接口。实现自己决定怎么存（私有目录、SAF、以后的网盘插件），
 * 去重、GC、缓存都是实现内部的事，不进契约。
 */
interface AttachmentStore {
    val id: String

    /** 写入并返回引用；同一份字节重复写入应复用已有文件。 */
    fun put(bytes: ByteArray, mime: String, name: String): AttachmentRef

    /** 读取；文件缺失或授权失效返回 null，由 UI 显示"附件已丢失"。 */
    fun open(ref: AttachmentRef): ByteArray?

    fun delete(ref: AttachmentRef)
}

/**
 * 按 [AttachmentRef.store] 分发的小注册表：老引用走各自的实现，
 * 新附件统一写入 [default]。
 */
class AttachmentStores(
    private val default: AttachmentStore,
    private val all: List<AttachmentStore> = listOf(default),
) {
    private val byId = all.associateBy { it.id }

    fun store(id: String): AttachmentStore? = byId[id]

    fun put(bytes: ByteArray, mime: String, name: String): AttachmentRef =
        default.put(bytes, mime, name)

    fun open(ref: AttachmentRef): ByteArray? = byId[ref.store]?.open(ref)

    fun delete(ref: AttachmentRef) {
        byId[ref.store]?.delete(ref)
    }
}
