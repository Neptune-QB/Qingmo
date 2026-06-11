package com.qingmo.app.xiaomo

import com.qingmo.app.data.model.DramaHighlight
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

    // ---- GIF displayCode 系统（code-based，替代 URL） ----
    private val _displayCode = MutableStateFlow("idle")
    val displayCode: StateFlow<String> = _displayCode.asStateFlow()
    private var effectLockUntil = 0L
    private var _effectResetJob: Job? = null

    /** 设置高光特效 GIF code，锁定 4000ms 后自动回 idle */
    fun triggerEffect(code: String) {
        val now = System.currentTimeMillis()
        if (now >= effectLockUntil || _displayCode.value == "idle") {
            _displayCode.value = code
            effectLockUntil = now + 4000
            android.util.Log.d("XiaoMoGif", "triggerEffect code=$code lockUntil=$effectLockUntil")
            // 锁过期后自动回 idle
            _effectResetJob?.cancel()
            _effectResetJob = GlobalScope.launch(Dispatchers.Main) {
                delay(effectLockUntil - now)
                setIdle()
            }
        }
    }

    /** 回到 idle，仅在锁过期后生效 */
    fun setIdle() {
        val now = System.currentTimeMillis()
        if (now < effectLockUntil) {
            android.util.Log.d("XiaoMoGif", "setIdle BLOCKED locked until=$effectLockUntil now=$now")
            return
        }
        _effectResetJob?.cancel()
        _effectResetJob = null
        _displayCode.value = "idle"
        android.util.Log.d("XiaoMoGif", "setIdle OK displayCode=idle")
    }

    // ---- 旧版 gifUrl（保留向后兼容） ----
    private val _gifUrl = MutableStateFlow<String?>(null)
    val gifUrl: StateFlow<String?> = _gifUrl.asStateFlow()
    private var gifLockUntil = 0L
    fun setGifUrl(url: String?) {
        if (url == null && System.currentTimeMillis() < gifLockUntil) return
        _gifUrl.value = url
        if (url != null) gifLockUntil = System.currentTimeMillis() + 4000
    }

    private var _hintDismissTimer: Job? = null

    // ---- 高光时刻一键弹幕互动 ----
    private val bubbleMap = mapOf(
        "cliffhanger" to "悬念拉满了！",
        "choice_point" to "你会怎么选？",
        "emotional_burst" to "破防了破防了😭",
        "power_moment" to "燃起来了🔥",
        "comedy" to "笑不活了家人们😂",
        "suspense" to "紧张到窒息...",
        "heartbreak" to "刀子来得太快💔",
        "sweet_moment" to "磕到了磕到了🥰",
        "reversal" to "完全没想到啊！",
        "slapback" to "打脸来得太快！",
    )

    private var _onHintDanmaku: ((DramaHighlight) -> Unit)? = null
    fun setOnHintDanmaku(callback: (DramaHighlight) -> Unit) { _onHintDanmaku = callback }

    fun triggerDanmakuHint(highlight: DramaHighlight) {
        _hintDismissTimer?.cancel()
        // 直接发飘屏弹幕，不走气泡
        _onHintDanmaku?.invoke(highlight)
    }

    fun dismissHint() {
        _hintDismissTimer?.cancel()
        _hintDismissTimer = null
        setPose(XiaoMoPose.Idle)
        _state.value = _state.value.copy(pendingDanmakuHighlight = null, bubbleText = "")
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
        _effectResetJob?.cancel()
        _effectResetJob = null
        _displayCode.value = "idle"
        effectLockUntil = 0L
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
    fun triggerInteraction(highlight: DramaHighlight, moduleId: String) {
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
