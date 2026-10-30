package com.chengying.clearview.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chengying.clearview.core.designsystem.R
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.model.SeverityLevel

enum class StatusTone { NEUTRAL, INFO, WARN, CRITICAL, CONFIRMED }
enum class ButtonKind { PRIMARY, SECONDARY, TEXT, DANGER }

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ClearViewTokens.colors
    // QQ 卡片：无边框、无描边、无阴影的纯色圆角矩形（12dp）。
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ClearViewTokens.shapes.card,
        color = colors.surface1,
        contentColor = colors.text1,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

// 产品硬规则：页面与区块标题只允许一行，标题下不得再叠副标题或说明行。
// QQ 页面标题：20sp 半粗、纯黑，位于灰色页面底之上，不放进卡片。
@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        style = ClearViewTokens.typography.title,
        color = ClearViewTokens.colors.text1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// 产品硬规则：页面与区块标题只允许一行，标题下不得再叠副标题或说明行。
// QQ 分组标签：卡片外上方、13sp 常规字重、灰字；缩进由调用方容器对齐卡片内文字起始线。
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = ClearViewTokens.typography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = ClearViewTokens.colors.text2,
        )
    }
}

@Composable
fun StatusTag(text: String, tone: StatusTone = StatusTone.NEUTRAL, modifier: Modifier = Modifier) {
    val colors = ClearViewTokens.colors
    val container = when (tone) {
        // surface2 与浅色页面底同值（#F2F3F5），落在灰底上的中性胶囊会整个消失。
        StatusTone.NEUTRAL -> colors.surface3
        StatusTone.INFO -> colors.infoContainer
        StatusTone.WARN -> colors.warnContainer
        StatusTone.CRITICAL -> colors.criticalContainer
        StatusTone.CONFIRMED -> colors.okContainer
    }
    Surface(modifier, shape = ClearViewTokens.shapes.small, color = container, contentColor = colors.text1) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = ClearViewTokens.typography.label)
    }
}

@Composable
fun SeverityRow(level: SeverityLevel, modifier: Modifier = Modifier) {
    val colors = ClearViewTokens.colors
    val color = when (level) {
        SeverityLevel.HIGH -> colors.critical
        SeverityLevel.MEDIUM -> colors.warn
        SeverityLevel.LOW, SeverityLevel.UNKNOWN -> colors.text2
    }
    val label = when (level) {
        SeverityLevel.HIGH -> "◆ 高等级"
        SeverityLevel.MEDIUM -> "▲ 中等级"
        SeverityLevel.LOW -> "— 低等级"
        SeverityLevel.UNKNOWN -> "— 分级不可用"
    }
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = ClearViewTokens.typography.heading, color = colors.text1)
        }
    }
}

@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier, numeric: Boolean = true, valueColor: Color? = null) {
    val type = ClearViewTokens.typography
    val displayValue = buildAnnotatedString {
        append(value)
        if (numeric) {
            Regex("[^\\x00-\\x7F]+").findAll(value).forEach { match ->
                addStyle(SpanStyle(fontFamily = type.chinese), match.range.first, match.range.last + 1)
            }
        }
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        // 标签按内容占宽且强制单行，值占余量右对齐；固定 1:2 分栏会让中文长标签（如"已加载 / 总记录"）在窄屏折行。
        Text(label, maxLines = 1, style = type.body.copy(fontSize = 16.sp, lineHeight = 22.sp), color = ClearViewTokens.colors.text1)
        Text(displayValue, Modifier.weight(1f), style = if (numeric) type.num.copy(fontSize = 15.sp, lineHeight = 21.sp) else type.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = valueColor ?: ClearViewTokens.colors.text3, textAlign = TextAlign.End)
    }
}

@Composable
fun ToolBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = ClearViewTokens.colors.surface1, border = BorderStroke(1.dp, ClearViewTokens.colors.line)) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
) {
    val colors = ClearViewTokens.colors
    // QQ 次按钮：白底 + 0.5~1dp 发丝描边；页面灰底与卡片白底上都靠描边/白底界定轮廓。
    val container = when (kind) {
        ButtonKind.PRIMARY -> colors.info
        ButtonKind.DANGER -> colors.criticalContainer
        ButtonKind.SECONDARY -> colors.surface1
        ButtonKind.TEXT -> colors.transparent
    }
    val content = when (kind) {
        ButtonKind.PRIMARY -> colors.onInfo
        ButtonKind.DANGER -> colors.critical
        ButtonKind.TEXT -> colors.info
        else -> colors.text1
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (kind == ButtonKind.PRIMARY) 48.dp else 44.dp),
        enabled = enabled,
        shape = ClearViewTokens.shapes.medium,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.surface2,
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = if (kind == ButtonKind.SECONDARY) BorderStroke(1.dp, colors.line) else null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
            Text(text, style = ClearViewTokens.typography.label)
        }
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val colors = ClearViewTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        enabled = enabled,
        shape = ClearViewTokens.shapes.small,
        color = if (selected) colors.infoContainer else colors.transparent,
        contentColor = if (enabled) colors.text1 else colors.textDisabled,
    ) {
        Box(Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun ClearViewTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    placeholder: String = "",
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = ClearViewTokens.colors
    val type = ClearViewTokens.typography
    val numericTransformation = remember {
        VisualTransformation { input ->
            TransformedText(buildAnnotatedString {
                append(input)
                Regex("[0-9]+(?:[.:/%+-][0-9]+)*").findAll(input.text).forEach { match ->
                    addStyle(SpanStyle(fontFamily = type.mono, fontFeatureSettings = "tnum"), match.range.first, match.range.last + 1)
                }
            }, OffsetMapping.Identity)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotBlank()) Text(label, style = type.label, color = colors.text2)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .semantics { contentDescription = label.ifBlank { placeholder } },
            enabled = enabled,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines,
            textStyle = type.body.copy(color = if (enabled) colors.text1 else colors.textDisabled),
            cursorBrush = SolidColor(colors.info),
            visualTransformation = numericTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Row(
                    Modifier.background(if (enabled) colors.surface3 else colors.surface2, ClearViewTokens.shapes.card)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.text3)
                        innerTextField()
                    }
                    trailingIcon?.invoke()
                }
            },
        )
    }
}

@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = ClearViewTokens.shapes.medium, color = ClearViewTokens.colors.warnContainer) {
        Text(text, Modifier.padding(12.dp), style = ClearViewTokens.typography.bodySmall, color = ClearViewTokens.colors.text1)
    }
}

@Composable
fun ActionableGuidanceRow(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = ClearViewTokens.colors.text2, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ClearViewTokens.typography.heading, color = ClearViewTokens.colors.text1)
            Text(detail, style = ClearViewTokens.typography.body, color = ClearViewTokens.colors.text2)
        }
    }
}

// 产品硬规则：空状态只允许一行文案，不提供副标题参数。
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier) {
    Panel(modifier, contentPadding = 24.dp) {
        Text(title, style = ClearViewTokens.typography.heading, color = ClearViewTokens.colors.text1)
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ds_ic_clearview_logo), contentDescription = null,
        modifier = modifier.size(48.dp), contentScale = ContentScale.Fit)
}
