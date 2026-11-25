package io.github.aixtin.nyrallite.contract

/**
 * 渲染插件产出的不透明分段。
 *
 * 主体只读 [stableKey] 做增量 diff，然后把 segment 原样传回渲染插件；
 * [payload] 的内容（commonmark 节点、表格行等）只有渲染插件自己认识，
 * 因此主体不会被迫依赖 Markwon / commonmark。
 */
class RenderSegment(val stableKey: String, val payload: Any)
