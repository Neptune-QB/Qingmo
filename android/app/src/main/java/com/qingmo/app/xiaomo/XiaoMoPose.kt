package com.qingmo.app.xiaomo

import androidx.annotation.DrawableRes
import com.qingmo.app.R

/**
 * 小墨姿态枚举 — 对应 4 张核心设计稿姿态
 *
 * 每项映射到 drawable/ 目录下的对应 PNG 资源，
 * V1.0 通过 Crossfade 在姿态之间无缝切换。
 */
enum class XiaoMoPose(@DrawableRes val resId: Int) {
    /** 站立微笑 — 默认闲置态（设计稿左侧大版） */
    Idle(R.drawable.xiaomo_idle),

    /** 挥手打招呼 — 首次进入引导、高光触发（设计稿右上角） */
    Greet(R.drawable.xiaomo_greet),

    /** 托水球思考 — 加载态、播放暂停态（设计稿右中部） */
    Thinking(R.drawable.xiaomo_thinking),

    /** 眨眼 + 托举播放按钮 — 互动完成、播放成功（设计稿右下角） */
    Playing(R.drawable.xiaomo_playing),

    /** 剧烈摇晃 — 高光时刻强烈提醒动画状态 */
    Shaking(R.drawable.xiaomo_idle),

    /** 比耶点赞 — 弹幕发送成功反馈 */
    ThumbsUp(R.drawable.xiaomo_playing),
}

/**
 * 小墨情绪枚举 — V1.0 通过 Emoji 气泡 + 姿态组合表达，
 * 后续 V2.0 如有独立表情图则扩展为 DrawableRes
 */
enum class XiaoMoEmotion(val emoji: String) {
    Neutral(""),
    Excited("\uD83C\uDF1F"),
    Shy("\uD83D\uDC96"),
    Laugh("\uD83D\uDE02"),
    Surprised("\uD83D\uDE2E"),
    Worship("\uD83D\uDC4F"),
    Confused("\u2753"),
}
