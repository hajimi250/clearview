package com.chengying.clearview

import android.content.Context

data class ServiceEndpointConfig(
    val backendBaseUrl: String,
)

class ServiceEndpointConfigStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    /** Null when the user never filled an address, so the caller can keep its own default. */
    fun loadOrNull(): ServiceEndpointConfig? = ServiceEndpointConfig(
        backendBaseUrl = prefs.getString(KEY_BACKEND_BASE_URL, null)?.trim().orEmpty(),
    ).takeIf { it.backendBaseUrl.isNotEmpty() }

    fun save(config: ServiceEndpointConfig) {
        prefs.edit()
            .putString(KEY_BACKEND_BASE_URL, config.backendBaseUrl.trim())
            .apply()
    }
}

private const val PREFS_NAME = "service-endpoint-config"
private const val KEY_BACKEND_BASE_URL = "backend-base-url"
