package com.qingmo.app.xiaomo

import com.qingmo.app.data.model.HighlightItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 小墨核心框架 — 管理角色生命周期、状态切换、模块调度
 */
object XiaoMoCore {

    private val _state = MutableStateFlow(XiaoMoStateData())
    val state: StateFlow<XiaoMoStateData> = _state.asStateFlow()

    // ---- 状态切换 ----

    /** 进入播放器时调用：显示 Peek 状态 */
    fun onEnterPlayer() {
        _state.value = XiaoMoStateData(state = XiaoMoState.Peek)
    }

    /** 退出播放器时调用：隐藏小墨 */
    fun onExitPlayer() {
        _state.value = XiaoMoStateData()
        _moduleResultCallback = null
    }

    /** 用户点击小墨 / 高光点触发：展开面板 */
    fun expand(trigger: String = "click") {
        _state.value = _state.value.copy(state = XiaoMoState.Expanded)
    }

    /** 用户关闭面板 / 超时：收回 Peek */
    fun collapse() {
        _state.value = _state.value.copy(
            state = XiaoMoState.Peek,
            currentModuleId = null,
            currentHighlight = null,
        )
    }

    /** 触发互动模块 */
    fun triggerInteraction(highlight: HighlightItem, moduleId: String) {
        _state.value = _state.value.copy(
            state = XiaoMoState.Interacting,
            currentModuleId = moduleId,
            currentHighlight = highlight,
            lastInteractTime = System.currentTimeMillis(),
        )
    }

    /** 互动完成，返回 Expanded 状态 */
    fun completeInteraction(result: InteractionResult) {
        _moduleResultCallback?.invoke(result)
        _state.value = _state.value.copy(
            state = XiaoMoState.Expanded,
            currentModuleId = null,
            currentHighlight = null,
        )
    }

    // ---- 互动结果回调 ----
    private var _moduleResultCallback: ((InteractionResult) -> Unit)? = null

    fun onInteractionResult(callback: (InteractionResult) -> Unit) {
        _moduleResultCallback = callback
    }
}
