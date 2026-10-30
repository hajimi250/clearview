package com.chengying.clearview.feature.history

import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import com.chengying.clearview.core.model.displayDiseaseName
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal fun DiagnosisReport.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val haystack = buildString {
        append(recordId).append(' ').append(createdAt).append(' ').append(inferenceSource.name).append(' ')
        candidates.forEach { candidate ->
            append(candidate.displayDiseaseName()).append(' ')
            append(candidate.rawLabel.orEmpty()).append(' ')
            append(candidate.diseaseNameZh.orEmpty()).append(' ')
        }
    }
    return haystack.contains(query, ignoreCase = true)
}

internal fun DiagnosisReport.matchesFilter(filter: String): Boolean = when (filter) {
    "异常状态" -> status == DiagnosisStatus.FAILED
    "最近一周" -> createdAt.toRecordDate()?.isAfter(LocalDate.now().minusDays(7)) ?: false
    "待分析" -> status == DiagnosisStatus.PENDING_UPLOAD
    else -> true
}

private fun String.toRecordDate(): LocalDate? =
    runCatching { OffsetDateTime.parse(this).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).toLocalDate() }.getOrNull()
