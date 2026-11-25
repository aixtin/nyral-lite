package io.github.aixtin.nyrallite.kernel

/**
 * 插件注册表：id 去重 + 依赖拓扑排序。
 * 排序保证被依赖的插件先安装；循环依赖/依赖缺失/重复 id 直接拒绝整个批次。
 */
internal object PluginRegistry {

    fun order(plugins: List<Plugin>): List<Plugin> {
        val byId = LinkedHashMap<String, Plugin>()
        for (plugin in plugins) {
            val id = plugin.manifest.id
            require(id.isNotBlank()) { "插件 id 不能为空" }
            require(byId.put(id, plugin) == null) { "插件 id 重复: $id" }
        }
        for (plugin in plugins) {
            for (dep in plugin.manifest.dependsOn) {
                require(byId.containsKey(dep)) {
                    "插件 ${plugin.manifest.id} 依赖不存在的插件: $dep"
                }
            }
        }

        val ordered = ArrayList<Plugin>(plugins.size)
        // 0=未访问 1=访问中 2=已完成
        val state = HashMap<String, Int>()

        fun visit(plugin: Plugin) {
            when (state[plugin.manifest.id]) {
                2 -> return
                1 -> throw IllegalStateException("插件循环依赖: ${plugin.manifest.id}")
            }
            state[plugin.manifest.id] = 1
            for (dep in plugin.manifest.dependsOn) visit(byId.getValue(dep))
            state[plugin.manifest.id] = 2
            ordered.add(plugin)
        }

        plugins.forEach(::visit)
        return ordered
    }
}
