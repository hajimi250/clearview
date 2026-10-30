package com.chengying.clearview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.chengying.clearview.core.designsystem.component.BrandMark
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens

@Composable
fun BrandLaunchScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(ClearViewTokens.colors.background)
            .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()
        Text("澄影", style = ClearViewTokens.typography.display, color = ClearViewTokens.colors.text1)
        Text("CLEARVIEW", style = ClearViewTokens.typography.caption.copy(fontFamily = ClearViewTokens.typography.sans), color = ClearViewTokens.colors.text2)
        Text("乳腺超声影像 · 科研与教学", style = ClearViewTokens.typography.body, color = ClearViewTokens.colors.text2)
        Text("正在准备医学影像工作台", style = ClearViewTokens.typography.bodySmall, color = ClearViewTokens.colors.text2)
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp), color = ClearViewTokens.colors.info,
            trackColor = ClearViewTokens.colors.surface3, strokeCap = StrokeCap.Butt,
        )
    }
}
