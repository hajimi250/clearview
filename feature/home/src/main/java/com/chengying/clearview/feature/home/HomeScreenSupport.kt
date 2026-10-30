package com.chengying.clearview.feature.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.chengying.clearview.core.designsystem.component.StatusTone
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import com.chengying.clearview.core.model.displayDiseaseName
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal data class HomeStatusDisplay(val label: String, val tone: StatusTone)

internal fun homeStatusDisplay(record: DiagnosisReport): HomeStatusDisplay = when (record.status) {
    DiagnosisStatus.PENDING_UPLOAD -> HomeStatusDisplay("待分析", StatusTone.INFO)
    DiagnosisStatus.FAILED -> HomeStatusDisplay("已失败", StatusTone.CRITICAL)
    DiagnosisStatus.SUCCESS -> HomeStatusDisplay("已完成", StatusTone.NEUTRAL)
}

internal fun homeCompactStatusLabel(record: DiagnosisReport): String = when (record.status) {
    DiagnosisStatus.PENDING_UPLOAD -> "待分析"
    DiagnosisStatus.FAILED -> "已失败"
    DiagnosisStatus.SUCCESS -> "已完成"
}

internal fun homeRecordTitle(record: DiagnosisReport): String = when (record.status) {
    DiagnosisStatus.PENDING_UPLOAD -> "等待分析"
    DiagnosisStatus.FAILED -> "未获得有效结果"
    DiagnosisStatus.SUCCESS -> record.displayDiseaseName()
}

private val homeCompactTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val homeStoredTimeFormatters = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
)

internal fun homeRecordTime(createdAt: String): String {
    val value = createdAt.trim()
    if (value.isBlank()) return "时间未提供"
    return runCatching { OffsetDateTime.parse(value).format(homeCompactTimeFormatter) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).format(homeCompactTimeFormatter) }.getOrNull()
        ?: homeStoredTimeFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value, formatter).format(homeCompactTimeFormatter) }.getOrNull()
        }
        ?: value
}

/** Prefer the user-declared exam time; fall back to the record time without relabeling it. */
internal fun homeStudyTime(record: DiagnosisReport): String =
    record.acquisition?.acquiredAt?.takeIf { it.isNotBlank() }?.let(::homeRecordTime)
        ?: homeRecordTime(record.createdAt)

internal fun homeRecordMetaLine(record: DiagnosisReport, includeStatus: Boolean = false): String = buildList {
    add(homeStudyTime(record))
    if (includeStatus) add(homeCompactStatusLabel(record))
}.joinToString(" · ")

internal fun homePendingSummary(pending: Int, failed: Int): String = buildList {
    if (pending > 0) add("$pending 条待分析")
    if (failed > 0) add("$failed 条失败")
}.joinToString(" · ")

private val numericRuns = Regex("[+-]?\\d+(?:[./:%°℃-]\\d+)*[%°℃]?")

/** Keep measurements in prose tabular without changing the supplied copy. */
@Composable
internal fun HomeContentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ClearViewTokens.typography.body,
    secondary: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val numericStyle = if (style == ClearViewTokens.typography.bodySmall || style == ClearViewTokens.typography.label) {
        ClearViewTokens.typography.numSmall
    } else {
        ClearViewTokens.typography.num
    }
    Text(
        text = buildAnnotatedString {
            append(text)
            numericRuns.findAll(text).forEach { match ->
                addStyle(numericStyle.toSpanStyle(), match.range.first, match.range.last + 1)
            }
        },
        modifier = modifier,
        style = style,
        color = if (secondary) ClearViewTokens.colors.text2 else ClearViewTokens.colors.text1,
        maxLines = maxLines,
        overflow = overflow,
    )
}
