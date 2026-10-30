package com.chengying.clearview.feature.knowledge

import android.content.Context
import org.json.JSONArray

internal object KnowledgeRecentSearchStore {
    private const val PREFS_NAME = "knowledge_recent_searches"
    private const val KEY_RECENT_SEARCHES = "recent_searches"

    fun read(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_SEARCHES, null)
            ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val value = jsonArray.optString(index).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun write(context: Context, items: List<String>) {
        val payload = JSONArray().apply {
            items.forEach(::put)
        }.toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_SEARCHES, payload)
            .apply()
    }
}
