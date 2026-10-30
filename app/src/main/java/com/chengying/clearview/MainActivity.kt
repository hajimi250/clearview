package com.chengying.clearview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.chengying.clearview.core.designsystem.theme.ClearViewTheme
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("appearance", MODE_PRIVATE)
        val initialMode = runCatching {
            ThemeMode.valueOf(preferences.getString("theme-mode", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(initialMode) }
            var showLaunchScreen by rememberSaveable { mutableStateOf(true) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(Unit) {
                delay(900)
                showLaunchScreen = false
            }
            ClearViewTheme(darkTheme = darkTheme) {
                val colors = ClearViewTokens.colors
                SideEffect {
                    val style = if (darkTheme) SystemBarStyle.dark(colors.transparent.toArgb())
                    else SystemBarStyle.light(colors.transparent.toArgb(), colors.transparent.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    window.decorView.setBackgroundColor(colors.background.toArgb())
                    if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
                }
                if (showLaunchScreen) {
                    BrandLaunchScreen()
                } else {
                    ClearViewApp(
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            preferences.edit().putString("theme-mode", it.name).apply()
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(
                                    when (it) {
                                        ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                                        ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                                        ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
