package com.chengying.clearview.core.network

import android.content.Context
import java.util.UUID

private const val INSTALL_ID_PREFS = "network-install-identity"
private const val INSTALL_ID_KEY = "install-id"

class InstallIdentityStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun getOrCreate(): String {
        val prefs = appContext.getSharedPreferences(INSTALL_ID_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(INSTALL_ID_KEY, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(INSTALL_ID_KEY, generated).apply()
        return generated
    }
}
