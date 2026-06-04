package com.qingmo.app.data.user

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * 设备唯一标识提供者
 * 优先使用 ANDROID_ID，降级到随机 UUID（持久化到 SharedPreferences）
 */
object DeviceIdProvider {
    private const val PREFS_NAME = "qingmo_device"
    private const val KEY_DEVICE_ID = "device_id"

    private var cachedId: String? = null

    fun getDeviceId(context: Context): String {
        cachedId?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_DEVICE_ID, null)
        if (saved != null) {
            cachedId = saved
            return saved
        }

        // 尝试使用 ANDROID_ID（权限受限时降级到随机 UUID）
        val id = try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
                null
            } else {
                "android-${androidId.takeLast(12)}"
            }
        } catch (_: Exception) {
            null
        } ?: UUID.randomUUID().toString()

        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        cachedId = id
        return id
    }
}
