package com.qingmo.app.data.chat

import com.qingmo.app.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 小墨对话服务 — 通过 OkHttp 实现流式聊天和剧情续写
 */
object ChatService {

    /** 流式对话专用客户端：容忍 LLM 推理间隔较长 */
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 普通请求客户端（剧情续写） */
    private val normalClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 流式对话，返回 Flow<String> 逐块推送 LLM 回复
     */
    fun streamChat(
        userMessage: String,
        userId: String = "android-demo",
        history: List<Map<String, String>>? = null,
        dramaContext: Map<String, Any>? = null,
    ): Flow<String> = callbackFlow {
        val json = JSONObject().apply {
            put("user_id", userId)
            put("message", userMessage)
            if (history != null) {
                put("history", org.json.JSONArray(history.map {
                    JSONObject(it)
                }))
            }
            if (dramaContext != null) {
                put("context", JSONObject(dramaContext))
            }
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}api/v1/agent/chat")
            .post(body)
            .build()

        streamClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                trySend("小墨网络出问题啦，稍后再试试~")
                close(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    trySend("小墨现在有点忙，换个方式问问我？")
                    close()
                    return
                }
                val reader = response.body?.charStream()?.buffered() ?: run {
                    trySend("小墨不知道说什么了~")
                    close()
                    return
                }
                var total = 0
                try {
                    // 逐字缓冲：取1个字符模拟逐字输出效果
                    val buf = CharArray(8)
                    var len: Int
                    while (reader.read(buf).also { len = it } != -1) {
                        for (i in 0 until len) {
                            trySend(buf[i].toString())
                        }
                        total += len
                        if (total > 50_000) {
                            trySend("\n\n(回复太长，小墨帮你截断啦~)")
                            break
                        }
                    }
                } catch (e: Exception) {
                    trySend("\n\n(小墨说话被打断了...)")
                } finally {
                    try { reader.close() } catch (_: Exception) {}
                    response.close()
                    close()
                }
            }
        })

        awaitClose()
    }

    /**
     * 剧情续写，一次性返回
     */
    suspend fun getStoryExtension(
        dramaTitle: String,
        dramaDesc: String,
        latestEpisodes: List<String>,
    ): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("drama_title", dramaTitle)
                put("drama_desc", dramaDesc)
                put("latest_episodes", org.json.JSONArray(latestEpisodes))
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${RetrofitClient.BASE_URL}api/v1/agent/story-extension")
                .post(body)
                .build()

            val response = normalClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: "续写失败"
            response.close()
            val result = org.json.JSONObject(responseBody)
            result.optString("extension", "续写失败，请重试。")
        } catch (e: Exception) {
            "续写失败：${e.message}"
        }
    }
}
