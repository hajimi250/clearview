package com.chengying.clearview.core.network

data class AssistantMessage(
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

data class AssistantReply(
    val text: String,
    val model: String,
)

interface AssistantGateway {
    suspend fun chat(messages: List<AssistantMessage>): AssistantReply
}
