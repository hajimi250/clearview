package com.chengying.clearview.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chengying.clearview.core.designsystem.component.ActionButton
import com.chengying.clearview.core.designsystem.component.ActionableGuidanceRow
import com.chengying.clearview.core.designsystem.component.ButtonKind
import com.chengying.clearview.core.designsystem.component.EmptyState
import com.chengying.clearview.core.designsystem.component.MedicalImageViewer
import com.chengying.clearview.core.designsystem.component.MetaRow
import com.chengying.clearview.core.designsystem.component.PageTitle
import com.chengying.clearview.core.designsystem.component.Panel
import com.chengying.clearview.core.designsystem.component.SectionHeader
import com.chengying.clearview.core.designsystem.component.StatusTag
import com.chengying.clearview.core.designsystem.component.StatusTone
import com.chengying.clearview.core.designsystem.component.ToolButton
import com.chengying.clearview.core.designsystem.component.WarningBanner
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import com.chengying.clearview.core.model.displayDiseaseName
import com.chengying.clearview.core.model.isValidRatio

/** Submission metadata shown while the result page waits for the analysis response. */
data class ResultAnalysisProgress(
    val recordId: String,
    val createdAt: String,
    val sourceImagePath: String?,
)

@Composable
fun ResultScreen(
    result: DiagnosisReport?,
    onRetake: () -> Unit = {},
    onOpenConversation: (DiagnosisReport) -> Unit = {},
    analysisProgress: ResultAnalysisProgress? = null,
    analysisErrorMessage: String? = null,
    saveWarning: String? = null,
) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    var helpVisible by rememberSaveable(result?.recordId) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (analysisProgress != null) {
            item { AnalysisProgressPanel(analysisProgress) }
            analysisProgress.sourceImagePath?.takeIf { it.isNotBlank() }?.let { path ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SectionHeader("本次影像")
                        MedicalImageViewer(
                            sourceImagePath = path,
                            showLayers = false,
                            contentDescription = "正在分析中的本次影像",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "模型标记将在分析完成后显示。",
                            style = ClearViewTokens.typography.caption,
                            color = colors.text3,
                        )
                    }
                }
            }
        } else if (result == null) {
            item { PageTitle("影像分析结果") }
            if (analysisErrorMessage != null) {
                item { AnalysisFailurePanel(analysisErrorMessage, onRetake) }
            } else {
                item { EmptyState("导入影像并提交分析后显示结果") }
                item {
                    ActionButton("导入影像", onRetake, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera)
                }
            }
        } else {
            item { ResultHero(result, onHelp = { helpVisible = true }) }
            if (!saveWarning.isNullOrBlank()) {
                item { WarningBanner(saveWarning) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SectionHeader("影像与病灶分割")
                    MedicalImageViewer(
                        sourceImagePath = result.sourceImagePath,
                        maskImagePath = result.maskImagePath,
                        showLayers = true,
                        contentDescription = "报告原始影像与模型病灶分割",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SectionHeader("模型诊断")
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                            if (result.candidates.firstOrNull() == null) {
                                Text("未返回有效分类候选；这不等于排除病变。", style = ClearViewTokens.typography.body, color = colors.text2)
                            } else {
                                val hasLesion = result.lesionImageRatio.isValidRatio() && result.lesionImageRatio > 0f
                                Text(
                                    "SigLIP 分类模型基于整幅影像给出良性病变、恶性病变或正常乳腺组织的主分类，" +
                                        "显示于上方标题；分类是模型倾向，不是病理诊断。",
                                    style = ClearViewTokens.typography.body,
                                    color = colors.text2,
                                )
                                Text(
                                    if (hasLesion) "nnU-Net 已在本图检出可疑病灶范围，可切换「标记」「叠加」查看；病灶性质须由医师结合超声与病理判断。"
                                    else "nnU-Net 未在本图检出可疑病灶范围，「标记」「叠加」暂不可用；未见病灶不等同于排除病变。",
                                    style = ClearViewTokens.typography.body,
                                    color = colors.text2,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ActionButton(
                        text = "向助手提问",
                        onClick = { onOpenConversation(result) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.ChatBubbleOutline,
                    )
                    ActionButton(
                        text = when (result.status) {
                            DiagnosisStatus.PENDING_UPLOAD -> "继续分析"
                            DiagnosisStatus.FAILED -> "重新分析"
                            DiagnosisStatus.SUCCESS -> "重新拍摄"
                        },
                        onClick = onRetake,
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.SECONDARY,
                        icon = Icons.Outlined.PhotoCamera,
                    )
                }
            }
        }
    }
    if (helpVisible && result != null && analysisProgress == null) {
        ResultHelpDialog(
            suggestions = result.suggestions,
            onDismiss = { helpVisible = false },
        )
    }
}

@Composable
private fun AnalysisProgressPanel(progress: ResultAnalysisProgress) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text("影像分析 · 进行中", style = ClearViewTokens.typography.label, color = colors.text2)
                    Text("正在分析影像", style = ClearViewTokens.typography.heading, color = colors.text1)
                }
                StatusTag("分析中", StatusTone.INFO)
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.info,
                trackColor = colors.surface2,
            )
            Text(
                "已提交至分析服务，通常需要数十秒。完成后本页自动填入候选与影像标记，请勿重复提交。",
                style = ClearViewTokens.typography.bodySmall,
                color = colors.text2,
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                MetaRow("提交编号", progress.recordId)
                MetaRow("提交时间", progress.createdAt)
            }
            Text(
                "完成前不展示模型结果；请保持应用在前台。",
                style = ClearViewTokens.typography.caption,
                color = colors.text3,
            )
        }
    }
}

@Composable
private fun AnalysisFailurePanel(message: String, onRetake: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本次分析未完成",
                    Modifier.weight(1f),
                    style = ClearViewTokens.typography.heading,
                    color = colors.text1,
                )
                StatusTag("未完成", StatusTone.WARN)
            }
            WarningBanner(message)
            Text(
                "未完成不代表影像正常。请检查服务与网络后返回重新提交。",
                style = ClearViewTokens.typography.bodySmall,
                color = colors.text2,
            )
            ActionButton(
                text = "返回重新提交",
                onClick = onRetake,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.PhotoCamera,
            )
        }
    }
}

@Composable
private fun ResultHero(result: DiagnosisReport, onHelp: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = when (result.status) {
                            DiagnosisStatus.SUCCESS -> "分析结果"
                            DiagnosisStatus.PENDING_UPLOAD -> "影像等待分析"
                            DiagnosisStatus.FAILED -> "本次分析未完成"
                        },
                        style = ClearViewTokens.typography.label,
                        color = colors.text2,
                    )
                    Text(
                        text = when (result.status) {
                            DiagnosisStatus.SUCCESS -> result.displayDiseaseName()
                            DiagnosisStatus.PENDING_UPLOAD -> "等待分析"
                            DiagnosisStatus.FAILED -> "未获得有效结果"
                        },
                        style = if (result.status == DiagnosisStatus.SUCCESS) {
                            ClearViewTokens.typography.display
                        } else {
                            ClearViewTokens.typography.title
                        },
                        color = colors.text1,
                    )
                }
                HelpButton(onHelp)
            }
            when (result.status) {
                DiagnosisStatus.SUCCESS -> Unit
                DiagnosisStatus.PENDING_UPLOAD -> WarningBanner("影像已保存，尚无有效分析结论；连接分析服务后可继续提交。")
                DiagnosisStatus.FAILED -> {
                    WarningBanner("服务未返回有效分析结果。请检查服务与网络后重试；失败不表示正常。")
                    result.failureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(reason, style = ClearViewTokens.typography.bodySmall, color = colors.text2)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                MetaRow("记录时间", result.createdAt.ifBlank { "未提供" })
                MetaRow("报告编号", result.recordId.ifBlank { "未提供" })
                if (result.examDescription.isNotBlank()) {
                    MetaRow("检查说明", result.examDescription, numeric = false)
                }
            }
            Text(
                text = "仅供教学，不替代医师诊断。",
                style = ClearViewTokens.typography.caption,
                color = colors.text3,
            )
        }
    }
}

@Composable
private fun HelpButton(onClick: () -> Unit) {
    val colors = ClearViewTokens.colors
    // Keep the previous 40dp layout slot so the hero row does not shift; only the visible circle shrinks.
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp).semantics { contentDescription = "阅片与使用提示" },
        shape = ClearViewTokens.shapes.floatingAction,
        color = colors.transparent,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = ClearViewTokens.shapes.floatingAction,
                color = colors.surface3,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("?", style = ClearViewTokens.typography.heading, color = colors.text2)
                }
            }
        }
    }
}

@Composable
private fun ResultHelpDialog(
    suggestions: List<String>,
    onDismiss: () -> Unit,
) {
    val colors = ClearViewTokens.colors
    val spacing = ClearViewTokens.spacing
    val tips = suggestions.ifEmpty {
        listOf("建议配合医师复核原始影像，并用「标记」「叠加」核对病灶范围。")
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ClearViewTokens.shapes.card,
            color = colors.surface1,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageTitle(
                        "阅片与使用提示",
                        Modifier.weight(1f),
                    )
                    ToolButton(Icons.Outlined.Close, "关闭提示", onDismiss)
                }
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    Text("如何阅读本次结果", style = ClearViewTokens.typography.label, color = colors.text2)
                    // Preserve every returned suggestion, not just the first three.
                    tips.forEachIndexed { index, tip ->
                        ActionableGuidanceRow(
                            icon = when (index) {
                                0 -> Icons.Outlined.Healing
                                1 -> Icons.Outlined.PhotoCamera
                                else -> Icons.Outlined.Science
                            },
                            title = when (index) {
                                0 -> "复核超声表现"
                                1 -> "检查影像与分析条件"
                                2 -> "了解模型局限"
                                else -> "补充使用提示"
                            },
                            detail = tip.ifBlank { "此条提示未提供内容。" },
                        )
                    }
                }
            }
        }
    }
}
