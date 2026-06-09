package com.qingmo.app.xiaomo

import com.qingmo.app.data.model.HighlightItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 小墨核心框架 — 管理角色生命周期、状态切换、模块调度
 */
object XiaoMoCore {

    private val _state = MutableStateFlow(XiaoMoStateData())
    val state: StateFlow<XiaoMoStateData> = _state.asStateFlow()

    private var _hintDismissTimer: Job? = null

    // ---- 高光时刻一键弹幕互动 ----
    private val bubbleMap = mapOf(
        "conflict" to "哇塞这也太刺激了！",
        "twist" to "完全没想到啊！",
        "sweet" to "磕到了磕到了🥰",
        "funny" to "笑不活了家人们😂",
        "famous" to "名场面来了！盯紧屏幕！",
    )

    fun triggerDanmakuHint(highlight: HighlightItem) {
        _hintDismissTimer?.cancel()
        val bubble = bubbleMap[highlight.type] ?: highlight.title
        _state.value = _state.value.copy(
            pose = XiaoMoPose.Shaking,
            pendingDanmakuHighlight = highlight,
            bubbleText = bubble,
        )
        _hintDismissTimer = GlobalScope.launch(Dispatchers.Main) {
            delay(12000L)
            setPose(XiaoMoPose.Idle)
            _state.value = _state.value.copy(pendingDanmakuHighlight = null, bubbleText = "")
        }
    }

    fun onDanmakuSentSuccess() {
        _hintDismissTimer?.cancel()
        _state.value = _state.value.copy(pose = XiaoMoPose.ThumbsUp, pendingDanmakuHighlight = null, bubbleText = "")
        GlobalScope.launch(Dispatchers.Main) {
            delay(1200L)
            setPose(XiaoMoPose.Idle)
        }
    }

    fun skipToNextHighlight() {
        _hintDismissTimer?.cancel()
        _state.value = _state.value.copy(pose = XiaoMoPose.Idle, pendingDanmakuHighlight = null)
    }

    // ---- 状态切换 ----

    /** 进入播放器时调用：显示 Peek 状态 */
    fun onEnterPlayer() {
        _state.value = XiaoMoStateData(
            state = XiaoMoState.Peek,
            pose = XiaoMoPose.Greet,
            emotion = XiaoMoEmotion.Neutral,
        )
        // 2000ms 后回到默认闲置姿态
        _greetTimer?.cancel()
        _hintDismissTimer?.cancel()
        _greetTimer = GlobalScope.launch(Dispatchers.Main) {
            delay(2000L)
            setPose(XiaoMoPose.Idle)
        }
    }

    private var _greetTimer: kotlinx.coroutines.Job? = null

    /** 退出播放器时调用：隐藏小墨 */
    fun onExitPlayer() {
        _greetTimer?.cancel()
        _greetTimer = null
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

    /** 切换当前姿态 */
    fun setPose(pose: XiaoMoPose) {
        _state.value = _state.value.copy(pose = pose)
    }

    /** 切换当前情绪 */
    fun setEmotion(emotion: XiaoMoEmotion) {
        _state.value = _state.value.copy(emotion = emotion)
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
            pose = XiaoMoPose.Playing,
        )
    }

    // ---- 互动结果回调 ----
    private var _moduleResultCallback: ((InteractionResult) -> Unit)? = null

    fun onInteractionResult(callback: (InteractionResult) -> Unit) {
        _moduleResultCallback = callback
    }
}
