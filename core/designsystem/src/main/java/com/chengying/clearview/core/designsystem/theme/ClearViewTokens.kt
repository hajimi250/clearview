package com.chengying.clearview.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chengying.clearview.core.designsystem.R

@Immutable
data class ClearViewColors(
    val canvas: Color,
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val lineStrong: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val textDisabled: Color,
    val critical: Color,
    val criticalContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val ok: Color,
    val okContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfo: Color,
    val onCritical: Color,
) {
    val imageCanvas: Color get() = Color.Black
    val imageText: Color get() = Color(0xFFF2F2F2)
    val imageAnnotation: Color get() = Color(0xFF0A84FF)
    val imageCritical: Color get() = Color(0xFFFF453A)
    val imageReference: Color get() = Color(0xFFFFB020)
    val transparent: Color get() = Color.Transparent
    val imageToolbar: Color get() = Color.Black.copy(alpha = 0.4f)
}

internal val DarkClearViewColors = ClearViewColors(
    canvas = Color(0xFF000000), background = Color(0xFF171717),
    surface1 = Color(0xFF232323), surface2 = Color(0xFF2A2A2A), surface3 = Color(0xFF313131),
    line = Color(0xFF2C2C2C), lineStrong = Color(0xFF3A3A3A),
    text1 = Color(0xFFF2F2F2), text2 = Color(0xFF98989D), text3 = Color(0xFF6E6E73),
    textDisabled = Color(0xFF4B4B50),
    critical = Color(0xFFFF5A52), criticalContainer = Color(0xFF3A1A18),
    warn = Color(0xFFE8A33D), warnContainer = Color(0xFF3A2A14),
    ok = Color(0xFF30D158), okContainer = Color(0xFF12331F),
    info = Color(0xFF1AADF9), infoContainer = Color(0xFF0D2B3A),
    onInfo = Color(0xFFFFFFFF), onCritical = Color(0xFFFFFFFF),
)

internal val LightClearViewColors = ClearViewColors(
    canvas = Color(0xFFFFFFFF), background = Color(0xFFF2F3F5),
    surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFF2F3F5), surface3 = Color(0xFFE7E8EA),
    line = Color(0xFFEDEDED), lineStrong = Color(0xFFE0E0E0),
    text1 = Color(0xFF111111), text2 = Color(0xFF7C7C80), text3 = Color(0xFF8E8E93),
    textDisabled = Color(0xFFC7C7CC),
    critical = Color(0xFFFA5151), criticalContainer = Color(0xFFFFECEB),
    warn = Color(0xFFF98E1B), warnContainer = Color(0xFFFFF5E5),
    ok = Color(0xFF2ECB71), okContainer = Color(0xFFE6F7EC),
    info = Color(0xFF12B7F5), infoContainer = Color(0xFFE4F6FF),
    onInfo = Color(0xFFFFFFFF), onCritical = Color(0xFFFFFFFF),
)

internal val LocalClearViewColors = staticCompositionLocalOf { LightClearViewColors }

object ClearViewSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object ClearViewShapes {
    val none = RectangleShape
    val small = RoundedCornerShape(2.dp)
    val medium = RoundedCornerShape(4.dp)
    val card = RoundedCornerShape(12.dp)
    val floatingAction = CircleShape
}

object ClearViewMotion {
    const val status = 100
    const val page = 200
    const val image = 150
    const val toolbarTimeout = 3_000L
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"),
}

object ClearViewType {
    val sans = FontFamily(
        Font(R.font.plex_sans_regular, FontWeight.Normal),
        Font(R.font.plex_sans_medium, FontWeight.Medium),
        Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
    )
    val chinese = FontFamily(
        Font(R.font.plex_sans_sc_regular, FontWeight.Normal),
        Font(R.font.plex_sans_sc_medium, FontWeight.Medium),
        Font(R.font.plex_sans_sc_semibold, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )
    val display = TextStyle(fontFamily = chinese, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val title = TextStyle(fontFamily = chinese, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    val heading = TextStyle(fontFamily = chinese, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    val body = TextStyle(fontFamily = chinese, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    val bodySmall = TextStyle(fontFamily = chinese, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    val label = TextStyle(fontFamily = chinese, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    val caption = TextStyle(fontFamily = chinese, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em)
    val numLarge = TextStyle(fontFamily = mono, fontFeatureSettings = "tnum", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    val num = TextStyle(fontFamily = mono, fontFeatureSettings = "tnum", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    val numSmall = TextStyle(fontFamily = mono, fontFeatureSettings = "tnum", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    val imageOverlay = numSmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
}

object ClearViewTokens {
    val colors: ClearViewColors @Composable get() = LocalClearViewColors.current
    val spacing = ClearViewSpacing
    val shapes = ClearViewShapes
    val typography = ClearViewType
    val motion = ClearViewMotion
}
