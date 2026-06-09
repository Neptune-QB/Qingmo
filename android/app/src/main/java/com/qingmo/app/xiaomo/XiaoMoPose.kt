package com.qingmo.app.xiaomo

import androidx.annotation.RawRes
import com.qingmo.app.R

/**
 * 小墨姿态枚举 — 所有姿态统一使用 xiaomo.gif
 */
enum class XiaoMoPose(@RawRes val resId: Int) {
    Idle(R.raw.xiaomo),
    Greet(R.raw.xiaomo),
    Thinking(R.raw.xiaomo),
    Playing(R.raw.xiaomo),
    Shaking(R.raw.xiaomo),
    ThumbsUp(R.raw.xiaomo),
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
