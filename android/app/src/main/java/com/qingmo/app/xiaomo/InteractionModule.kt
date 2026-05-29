package com.qingmo.app.xiaomo

import androidx.compose.runtime.Composable
import com.qingmo.app.data.model.HighlightItem

/**
 * 互动结果，由各模块定义具体数据
 */
data class InteractionResult(
    val moduleId: String,
    val highlightId: Int,
    val data: Map<String, Any> = emptyMap(),
)

/**
 * 小墨互动模块统一接口 — 所有互动模块必须实现此接口
 */
interface InteractionModule {
    /** 模块唯一标识 */
    val moduleId: String

    /** 模块展示名称 */
    val moduleName: String

    /** 优先级（越小越高，决定多个模块竞争时的选择顺序） */
    val priority: Int

    /** 模块注册回调 */
    fun onRegister() {}

    /** 模块注销回调 */
    fun onUnregister() {}

    /**
     * 判断是否能处理该高光点
     * @return true 表示匹配该高光点的 widget_type
     */
    fun canHandle(highlight: HighlightItem): Boolean

    /**
     * 渲染互动 UI
     */
    @Composable
    fun RenderInteraction(
        highlight: HighlightItem,
        onInteract: (InteractionResult) -> Unit,
        onDismiss: () -> Unit,
    )

    /** 处理互动结果（上报、统计、缓存等） */
    fun processResult(result: InteractionResult) {}
}
