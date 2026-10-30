package com.chengying.clearview.feature.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AssistantConversationMessage(
    val text: String,
    val isUser: Boolean,
)

object AssistantConversationStore {
    private const val PREFS_NAME = "assistant_conversations"
    private const val KEY_THREADS = "threads"
    private const val FIELD_TEXT = "text"
    private const val FIELD_IS_USER = "isUser"

    const val GENERAL_THREAD_KEY = "__general__"
    const val CHAT_THREAD_PREFIX = "chat-"

    fun threadKey(recordId: String?): String = recordId?.takeIf { it.isNotBlank() } ?: GENERAL_THREAD_KEY

    fun newChatThreadKey(): String = "$CHAT_THREAD_PREFIX${System.currentTimeMillis()}"

    fun isFreeChatThread(key: String): Boolean =
        key == GENERAL_THREAD_KEY || key.startsWith(CHAT_THREAD_PREFIX)

    fun readAll(context: Context): Map<String, List<AssistantConversationMessage>> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THREADS, null)
            ?: return emptyMap()

        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val items = root.optJSONArray(key)?.toConversationMessages().orEmpty()
                    if (items.isNotEmpty()) {
                        put(key, items)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun readThread(context: Context, key: String): List<AssistantConversationMessage> = readAll(context)[key].orEmpty()

    fun writeThread(
        context: Context,
        key: String,
        messages: List<AssistantConversationMessage>,
    ) {
        val root = readRoot(context)
        if (messages.isEmpty()) {
            root.remove(key)
        } else {
            root.put(
                key,
                JSONArray().apply {
                    messages.forEach { message ->
                        put(
                            JSONObject().apply {
                                put(FIELD_TEXT, message.text)
                                put(FIELD_IS_USER, message.isUser)
                            },
                        )
                    }
                },
            )
        }
        persistRoot(context, root)
    }

    fun removeThread(context: Context, key: String) {
        val root = readRoot(context)
        if (!root.has(key)) return
        root.remove(key)
        persistRoot(context, root)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_THREADS)
            .apply()
    }

    private fun readRoot(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THREADS, null)
            ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun persistRoot(context: Context, root: JSONObject) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THREADS, root.toString())
            .apply()
    }
}

private fun JSONArray.toConversationMessages(): List<AssistantConversationMessage> = buildList {
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val text = item.optString("text").trim()
        if (text.isEmpty()) continue
        add(
            AssistantConversationMessage(
                text = text,
                isUser = item.optBoolean("isUser", false),
            ),
        )
    }
}
