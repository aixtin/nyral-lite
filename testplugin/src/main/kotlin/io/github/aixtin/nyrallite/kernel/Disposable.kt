package io.github.aixtin.nyrallite.kernel

/** 可逆副作用：撤销一次注册（服务/事件/拦截器/资源）。 */
fun interface Disposable {
    fun dispose()
}

/**
 * 单个插件的副作用回收盒。
 * 卸载时按注册的逆序撤销（后注册的先撤）；单条失败不影响其余。
 */
class DisposableGroup : Disposable {
    private val items = ArrayDeque<Disposable>()
    private var disposed = false

    fun add(item: Disposable) {
        if (disposed) {
            // 已回收后仍被注册：立即撤销，避免泄漏
            item.dispose()
            return
        }
        items.addLast(item)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        while (items.isNotEmpty()) {
            try {
                items.removeLast().dispose()
            } catch (_: Exception) {
                // 卸载是尽力而为：一条失败不能卡住其余回收
            }
        }
    }
}
