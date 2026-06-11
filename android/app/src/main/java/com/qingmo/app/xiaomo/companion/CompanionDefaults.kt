package com.qingmo.app.xiaomo.companion

import com.qingmo.app.data.model.DramaHighlight

/** 互动类型默认映射 */
val DEFAULT_INTERACTION_MAP = mapOf(
    "cliffhanger" to "support_button",
    "power_moment" to "support_button",
    "suspense" to "support_button",
    "slapback" to "support_button",
    "emotional_burst" to "reaction_panel",
    "comedy" to "reaction_panel",
    "heartbreak" to "reaction_panel",
    "sweet_moment" to "reaction_panel",
    "reversal" to "reaction_panel",
    "choice_point" to "choice_panel",
)

/** support_button 默认文案 */
fun defaultButtonText(highlightType: String): String = when (highlightType) {
    "cliffhanger" -> "继续看"
    "power_moment" -> "爽到了"
    "suspense" -> "屏住呼吸"
    "slapback" -> "帮她反击"
    else -> "互动一下"
}

fun defaultClickedText(highlightType: String): String = when (highlightType) {
    "cliffhanger" -> "✓ 继续"
    "power_moment" -> "✓ 爽到了"
    "suspense" -> "✓ 已屏住"
    "slapback" -> "✓ 已反击"
    else -> "✓ 已互动"
}

/** reaction_panel 默认选项 */
fun defaultReactionOptions(highlightType: String): List<Pair<String, String>> = when (highlightType) {
    "emotional_burst" -> listOf("t1" to "心疼她", "t2" to "太压抑", "t3" to "绷不住了")
    "comedy" -> listOf("c1" to "太好笑", "c2" to "笑发财了", "c3" to "再来一次")
    "heartbreak" -> listOf("h1" to "心疼她", "h2" to "破防了", "h3" to "不敢看")
    "sweet_moment" -> listOf("s1" to "磕到了", "s2" to "好甜", "s3" to "锁死")
    "reversal" -> listOf("r1" to "没想到", "r2" to "反转了", "r3" to "太狠了")
    else -> listOf("d1" to "太上头了", "d2" to "有感觉", "d3" to "再看一遍")
}

/** choice_panel 默认选项 */
fun defaultChoiceOptions(): List<Pair<String, String>> =
    listOf("c1" to "立刻说出真相", "c2" to "继续隐瞒", "c3" to "转身离开")

/** 从 interaction_config 解析标题 */
fun resolveTitle(hl: DramaHighlight): String {
    val cfg = hl.interactionConfig
    val title = cfg["title"] as? String
    if (!title.isNullOrEmpty()) return title
    return hl.title.ifEmpty { hl.highlightType }
}

/** 从 interaction_config 解析选项 */
fun resolveOptions(hl: DramaHighlight): List<Pair<String, String>> {
    val raw = hl.interactionConfig["options"] as? List<*>
    if (raw != null) {
        return raw.mapNotNull { opt ->
            when (opt) {
                is Map<*, *> -> {
                    val k = opt["key"]?.toString() ?: ""
                    val v = opt["label"]?.toString() ?: ""
                    if (k.isNotEmpty() && v.isNotEmpty()) Pair(k, v) else null
                }
                is String -> Pair(opt, opt)
                else -> null
            }
        }
    }
    return defaultReactionOptions(hl.highlightType)
}

/** 获取 interaction_type，缺失时兜底 */
fun resolveInteractionType(hl: DramaHighlight): String {
    val it = hl.interactionType
    if (it.isNotEmpty()) return it
    return DEFAULT_INTERACTION_MAP[hl.highlightType] ?: "support_button"
}
