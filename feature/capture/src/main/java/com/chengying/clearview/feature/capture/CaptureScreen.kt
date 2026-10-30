package com.chengying.clearview.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chengying.clearview.core.designsystem.component.ActionButton
import com.chengying.clearview.core.designsystem.component.ButtonKind
import com.chengying.clearview.core.designsystem.component.MedicalImageLoadState
import com.chengying.clearview.core.designsystem.component.MedicalImageViewer
import com.chengying.clearview.core.designsystem.component.PageTitle
import com.chengying.clearview.core.designsystem.component.Panel
import com.chengying.clearview.core.designsystem.component.SectionHeader
import com.chengying.clearview.core.designsystem.component.StatusTag
import com.chengying.clearview.core.designsystem.component.StatusTone
import com.chengying.clearview.core.designsystem.component.ToolButton
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.model.AcquisitionInfo
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureScreen(
    selectedImagePath: String?,
    statusMessage: String?,
    isAnalyzing: Boolean,
    isImporting: Boolean,
    onPickFromGallery: () -> Unit,
    onCapturePhoto: () -> Unit,
    onOpenLatestResult: () -> Unit,
    onSubmitAnalysis: () -> Unit,
    onRemoveImage: () -> Unit,
    onSaveForLater: () -> Unit = {},
    acquisition: AcquisitionInfo = AcquisitionInfo(),
    onAcquisitionChange: (AcquisitionInfo) -> Unit = {},
    analysisProgressVisible: Boolean = false,
) {
    val colors = ClearViewTokens.colors
    val spacing = ClearViewTokens.spacing
    var imageState by remember(selectedImagePath) {
        mutableStateOf(if (selectedImagePath.isNullOrBlank()) MedicalImageLoadState.MISSING else MedicalImageLoadState.LOADING)
    }
    val busy = isAnalyzing || isImporting
    var checkTimeInput by rememberSaveable(selectedImagePath) { mutableStateOf(acquisition.acquiredAt.orEmpty()) }
    val parsedCheckTime = remember(checkTimeInput) { parseCheckTime(checkTimeInput) }
    val invalidCheckTime = checkTimeInput.isNotBlank() && parsedCheckTime == null
    // Invalid visible dates block saving/submission; unsupported previews still do not.
    val actionEnabled = selectedImagePath != null && !busy && !invalidCheckTime
    val status = when {
        isImporting -> "正在导入影像"
        isAnalyzing -> "正在分析"
        imageState == MedicalImageLoadState.LOADING -> "正在读取影像"
        imageState == MedicalImageLoadState.READY -> "影像已就绪"
        imageState == MedicalImageLoadState.DECODE_ERROR -> "影像无法预览"
        !selectedImagePath.isNullOrBlank() -> "本地影像缺失"
        else -> "待导入影像"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PageTitle(
                    title = "导入乳腺超声影像",
                    modifier = Modifier.weight(1f),
                )
                if (selectedImagePath != null) {
                    ToolButton(
                        icon = Icons.Outlined.DeleteOutline,
                        description = "移除已选影像",
                        onClick = onRemoveImage,
                        enabled = !busy,
                    )
                }
            }
        }
        item {
            StatusTag(
                text = status,
                tone = when {
                    busy || imageState == MedicalImageLoadState.LOADING -> StatusTone.INFO
                    imageState == MedicalImageLoadState.DECODE_ERROR -> StatusTone.WARN
                    else -> StatusTone.NEUTRAL
                },
            )
        }
        item {
            MedicalImageViewer(
                sourceImagePath = selectedImagePath,
                contentDescription = "已选择的乳腺超声影像",
                onSourceStateChange = { imageState = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SectionHeader("影像来源")
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        ActionButton(
                            text = "从相册导入",
                            onClick = onPickFromGallery,
                            icon = Icons.Outlined.Collections,
                            kind = ButtonKind.SECONDARY,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ActionButton(
                            text = "拍摄影像",
                            onClick = onCapturePhoto,
                            icon = Icons.Outlined.PhotoCamera,
                            kind = ButtonKind.SECONDARY,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ActionButton(
                            text = if (analysisProgressVisible) "查看分析进度" else "查看最近结果",
                            onClick = onOpenLatestResult,
                            icon = Icons.Outlined.ImageSearch,
                            kind = ButtonKind.TEXT,
                            enabled = !isImporting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "使用原始影像，拍摄保留完整边界，避免反光",
                            style = ClearViewTokens.typography.caption,
                            color = colors.text3,
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SectionHeader("影像分析")
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        statusMessage?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = ClearViewTokens.typography.body, color = colors.text2)
                        }
                        ActionButton(
                            text = if (isAnalyzing) "分析进行中…" else "提交分析",
                            onClick = onSubmitAnalysis,
                            enabled = actionEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (invalidCheckTime) {
                            Text(
                                text = "检查时间格式无效，请先在下方修正。",
                                style = ClearViewTokens.typography.caption,
                                color = colors.warn,
                            )
                        }
                        ActionButton(
                            text = "保存待分析",
                            onClick = onSaveForLater,
                            // Saving preserves the selected original even when its preview format is unsupported.
                            enabled = actionEnabled,
                            kind = ButtonKind.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "模型输出用于科研与教学参考，不代替医师诊断。",
                            style = ClearViewTokens.typography.caption,
                            color = colors.text3,
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SectionHeader("检查时间")
                Panel {
                    // 默认态不再放"本地时间将保存为…"说明小字；仅当日期格式无效时显示纠正提示。
                    val checkTimeSupport: (@Composable () -> Unit)? = if (invalidCheckTime) {
                        { Text("日期无效，请按 yyyy-MM-dd HH:mm 输入或清空；也支持带时区的 ISO 时间。") }
                    } else {
                        null
                    }
                    OutlinedTextField(
                        value = checkTimeInput,
                        onValueChange = { value ->
                            checkTimeInput = value
                            val parsed = parseCheckTime(value)
                            // Keep the last valid declaration while editing, never silently send an invalid date as unknown.
                            if (value.isBlank() || parsed != null) {
                                onAcquisitionChange(acquisition.copy(acquiredAt = parsed, declaredBy = "USER"))
                            }
                        },
                        label = { Text("检查时间（可选）") },
                        placeholder = { Text("yyyy-MM-dd HH:mm") },
                        supportingText = checkTimeSupport,
                        isError = invalidCheckTime,
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Strict parsing without a new dependency; a nonblank invalid value is never an unknown date. */
private fun parseCheckTime(input: String): String? {
    val value = input.trim()
    if (value.isEmpty()) return null
    val local = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}").matches(value)
    val offset = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})").matches(value)
    if (!local && !offset) return null
    val pattern = if (local) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd'T'HH:mm:ssXXX"
    val formatter = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
    val parseValue = if (local) value else value.replace(Regex("\\.\\d+(?=Z|[+-])"), "")
    val position = ParsePosition(0)
    val date = formatter.parse(parseValue, position) ?: return null
    if (position.index != parseValue.length) return null
    if (!local) return value
    if (formatter.format(date) != value) return null
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(date)
}
