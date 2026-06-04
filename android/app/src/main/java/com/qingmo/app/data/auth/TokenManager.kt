package com.qingmo.app.data.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Token 持久化管理
 * 存储 JWT token 和用户基本信息到 SharedPreferences
 */
object TokenManager {
    private const val PREFS_NAME = "qingmo_auth"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_AVATAR = "avatar"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveAuth(token: String, userId: Int, username: String, nickname: String) {
        prefs?.edit()?.apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putString(KEY_NICKNAME, nickname)
            apply()
        }
    }

    fun getToken(): String? = prefs?.getString(KEY_TOKEN, null)

    fun getUserId(): Int = prefs?.getInt(KEY_USER_ID, 0) ?: 0

    fun getUsername(): String? = prefs?.getString(KEY_USERNAME, null)

    fun getNickname(): String? = prefs?.getString(KEY_NICKNAME, null)

    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
