package com.qingmo.app.xiaomo

import android.content.Context
import android.content.SharedPreferences

/** 小墨功能开关持久化管理 */
object XiaoMoSettings {

    private const val PREFS = "xiaomo_prefs"

    /** 所有可开关功能 */
    val FEATURES = listOf(
        "auto_danmaku" to "AI替身自动发弹幕",
        "watch_greeting" to "陪看播报",
        "streak_achievement" to "连看成就",
        "danmaku_chain" to "弹幕接龙",
        "highlight_bubble" to "高光对话气泡",
        "on_tap_danmaku" to "一键弹幕面板",
        "danmaku_easteregg" to "弹幕彩蛋",
        "highlight_vote" to "剧情投票",
        "at_xiaomo_reply" to "@小墨评论区回复",
    )

    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 读取开关状态，默认全部开启 */
    fun isEnabled(key: String): Boolean {
        return prefs?.getBoolean(key, true) ?: true
    }

    /** 设置开关状态 */
    fun setEnabled(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    /** 重置所有开关为默认开启 */
    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
    }
}
