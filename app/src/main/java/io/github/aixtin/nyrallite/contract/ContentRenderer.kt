package io.github.aixtin.nyrallite.contract

import android.content.Context
import android.view.View

/**
 * 消息内容渲染接口：分段、建 View、原地更新。
 * 流式前缀与收尾走同一套实现，保证"流式中看到什么，结束后就是什么"。
 */
interface ContentRenderer {
    fun segments(text: String): List<RenderSegment>

    fun renderSegment(
        context: Context,
        segment: RenderSegment,
        fullText: String,
        streaming: Boolean,
    ): View

    /** 原地更新成功返回 true（调用方保留该 View），类型不匹配返回 false（调用方重建）。 */
    fun updateSegmentView(
        context: Context,
        view: View,
        segment: RenderSegment,
        fullText: String,
        streaming: Boolean,
    ): Boolean

    /** 该段渲染成本是否偏高（如表格），调用方据此降帧。 */
    fun isHeavySegment(segment: RenderSegment): Boolean
}
