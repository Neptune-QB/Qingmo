package com.qingmo.app.data.chat

/**
 * 单条聊天消息
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false,
) {
    enum class Role { User, XiaoMo }
}
