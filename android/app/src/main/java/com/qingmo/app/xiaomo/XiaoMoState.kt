package com.qingmo.app.xiaomo

import com.qingmo.app.data.model.HighlightItem

/**
 * 小墨角色状态枚举
 */
enum class XiaoMoState {
    /** 不可见（退出播放器/未登录） */
    Hidden,
    /** 半挂在屏幕右侧边缘，仅露出头部+手 */
    Peek,
    /** 完整展开，显示互动面板 */
    Expanded,
    /** 正在展示互动模块（情绪弹幕/投票/问答） */
    Interacting,
}

/**
 * 小墨核心状态机数据类
 */
data class XiaoMoStateData(
    val state: XiaoMoState = XiaoMoState.Hidden,
    val currentModuleId: String? = null,
    val currentHighlight: HighlightItem? = null,
    val lastInteractTime: Long = 0L,
    /** 当前姿态（控制展示哪张 PNG） */
    val pose: XiaoMoPose = XiaoMoPose.Idle,
    /** 当前情绪（控制 Emoji 气泡叠加） */
    val emotion: XiaoMoEmotion = XiaoMoEmotion.Neutral,
    /** 待一键发送弹幕的高光点，非空代表高光提示已触发 */
    val pendingDanmakuHighlight: HighlightItem? = null,
) {
    val isVisible: Boolean get() = state != XiaoMoState.Hidden
    val isInteracting: Boolean get() = state == XiaoMoState.Interacting
}
