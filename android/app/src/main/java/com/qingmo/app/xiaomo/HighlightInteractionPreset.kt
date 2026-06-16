package com.qingmo.app.xiaomo

import com.qingmo.app.data.model.DramaHighlight

data class HighlightInteractionPreset(
    val word: String,
    val theme: String = "",
    val reactionText: String = "",
    val interactionKey: String = "thrill",
)

private val typeMap = mapOf(
    "power_moment" to HighlightInteractionPreset("爽", theme = "爽点时刻", reactionText = "太爽了！", interactionKey = "thrill"),
    "face_slap" to HighlightInteractionPreset("爽", theme = "打脸时刻", reactionText = "这脸打得啪啪响！", interactionKey = "thrill"),
    "slapback" to HighlightInteractionPreset("爽", theme = "打脸时刻", reactionText = "觉得爽到了！！", interactionKey = "thrill"),
    "cliffhanger" to HighlightInteractionPreset("急", theme = "悬念卡点", reactionText = "不要停啊！", interactionKey = "hype"),
    "suspense" to HighlightInteractionPreset("急", theme = "紧张悬念", reactionText = "紧张到窒息...", interactionKey = "hype"),
    "reversal" to HighlightInteractionPreset("惊", theme = "反转时刻", reactionText = "完全没想到！", interactionKey = "shock"),
    "sad_moment" to HighlightInteractionPreset("泪", theme = "泪点时刻", reactionText = "破防了😭", interactionKey = "tears"),
    "heartbreak" to HighlightInteractionPreset("泪", theme = "虐心时刻", reactionText = "刀子来了💔", interactionKey = "tears"),
    "emotional_burst" to HighlightInteractionPreset("泪", theme = "情感爆发", reactionText = "绷不住了😭", interactionKey = "tears"),
    "romance" to HighlightInteractionPreset("甜", theme = "甜蜜时刻", reactionText = "磕到了🥰", interactionKey = "swoon"),
    "sweet_moment" to HighlightInteractionPreset("甜", theme = "甜蜜时刻", reactionText = "太甜了吧！", interactionKey = "swoon"),
    "touching" to HighlightInteractionPreset("暖", theme = "感人时刻", reactionText = "好温暖~", interactionKey = "swoon"),
    "anger" to HighlightInteractionPreset("怒", theme = "愤怒时刻", reactionText = "气死我了！", interactionKey = "rage"),
    "comedy" to HighlightInteractionPreset("哈", theme = "搞笑时刻", reactionText = "笑不活了😂", interactionKey = "happy"),
)

fun getHighlightInteractionPreset(highlight: DramaHighlight): HighlightInteractionPreset {
    return typeMap[highlight.highlightType] ?: HighlightInteractionPreset("爽")
}
