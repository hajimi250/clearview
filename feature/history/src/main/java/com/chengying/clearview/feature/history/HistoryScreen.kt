package com.chengying.clearview.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chengying.clearview.core.designsystem.component.ActionButton
import com.chengying.clearview.core.designsystem.component.ButtonKind
import com.chengying.clearview.core.designsystem.component.ClearViewTextField
import com.chengying.clearview.core.designsystem.component.DiagnosisThumbnail
import com.chengying.clearview.core.designsystem.component.EmptyState
import com.chengying.clearview.core.designsystem.component.MetaRow
import com.chengying.clearview.core.designsystem.component.PageTitle
import com.chengying.clearview.core.designsystem.component.Panel
import com.chengying.clearview.core.designsystem.component.StatusTag
import com.chengying.clearview.core.designsystem.component.StatusTone
import com.chengying.clearview.core.designsystem.component.ToolButton
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import com.chengying.clearview.core.model.displayDiseaseName

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<DiagnosisReport>,
    loading: Boolean,
    loadingMore: Boolean,
    errorMessage: String?,
    totalCount: Int,
    hasMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenRecord: (DiagnosisReport) -> Unit,
    onOpenConversation: (DiagnosisReport) -> Unit,
    onDeleteRecord: (DiagnosisReport) -> Unit,
    onDeleteRecords: (List<DiagnosisReport>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingDeleteRecord by remember { mutableStateOf<DiagnosisReport?>(null) }
    val filters = listOf("全部", "异常状态", "最近一周", "待分析")
    var selectedFilter by remember { mutableStateOf(filters.first()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingBatchDelete by remember { mutableStateOf<List<DiagnosisReport>?>(null) }
    val normalizedQuery = query.trim()
    val filteredRecords = remember(records, normalizedQuery, selectedFilter) {
        records.filter {
            it.matchesQuery(normalizedQuery) && it.matchesFilter(selectedFilter)
        }
    }
    // 只认当前筛选下仍可见的选中项：筛选/搜索/刷新后不会留下看不见的"幽灵选择"。
    val visibleSelected = remember(filteredRecords, selectedIds) {
        filteredRecords.filter { it.recordId in selectedIds }
    }
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize().background(colors.background),
        indicator = {
            if (loading || refreshState.distanceFraction > 0f) {
                StatusTag(
                    text = if (loading) "同步中" else "下拉刷新",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = spacing.sm),
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                ) {
                    if (selectionMode) {
                        Text(
                            text = "已选 ${visibleSelected.size} 条",
                            style = ClearViewTokens.typography.heading,
                            color = colors.text1,
                            modifier = Modifier.weight(1f),
                        )
                        val allSelected = filteredRecords.isNotEmpty() &&
                            visibleSelected.size == filteredRecords.size
                        ActionButton(
                            text = if (allSelected) "取消全选" else "全选",
                            onClick = {
                                selectedIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    filteredRecords.map { it.recordId }.toSet()
                                }
                            },
                            enabled = filteredRecords.isNotEmpty(),
                            kind = ButtonKind.SECONDARY,
                        )
                        ActionButton(
                            text = if (visibleSelected.isEmpty()) "删除" else "删除(${visibleSelected.size})",
                            onClick = { pendingBatchDelete = visibleSelected },
                            enabled = visibleSelected.isNotEmpty(),
                            kind = ButtonKind.DANGER,
                        )
                        ActionButton(
                            text = "完成",
                            onClick = { selectionMode = false; selectedIds = emptySet() },
                            kind = ButtonKind.SECONDARY,
                        )
                    } else {
                        PageTitle(
                            title = "历史记录",
                            modifier = Modifier.weight(1f),
                        )
                        ToolButton(Icons.Outlined.Refresh, "刷新历史", onRefresh, enabled = !loading)
                        ActionButton(
                            text = "选择",
                            onClick = { selectionMode = true },
                            enabled = records.isNotEmpty(),
                            kind = ButtonKind.SECONDARY,
                        )
                    }
                }
            }
            item {
                ClearViewTextField(
                    value = query,
                    onValueChange = { query = it; selectedIds = emptySet() },
                    placeholder = "搜索病症、日期或记录编号",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    filters.forEach { label ->
                        ActionButton(
                            text = label,
                            onClick = { selectedFilter = label; selectedIds = emptySet() },
                            modifier = Modifier.semantics { selected = selectedFilter == label },
                            kind = if (selectedFilter == label) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                        )
                    }
                }
            }
            item {
                Panel {
                    StatusTag(
                        text = when {
                            errorMessage != null -> "历史同步未完成"
                            loading -> "正在同步历史记录"
                            loadingMore -> "正在加载更多记录"
                            else -> "历史记录已整理"
                        },
                        tone = if (errorMessage != null) StatusTone.WARN else StatusTone.NEUTRAL,
                    )
                    MetaRow("已加载 / 总记录", "${records.size} / ${totalCount.coerceAtLeast(records.size)}")
                    if (loading) HistoryText("正在整理本地与服务端记录，请稍候。")
                    errorMessage?.let { HistoryText(it) }
                }
            }
            if (filteredRecords.isEmpty()) {
                item {
                    EmptyState(
                        title = if (records.isEmpty()) {
                            "导入影像后此处显示分析记录"
                        } else {
                            "更换筛选条件或加载更多记录"
                        },
                    )
                }
            }
            filteredRecords.forEach { record ->
                item(key = record.recordId) {
                    val isSelected = record.recordId in selectedIds
                    val toggleSelection = {
                        selectedIds = if (isSelected) {
                            selectedIds - record.recordId
                        } else {
                            selectedIds + record.recordId
                        }
                    }
                    // QQ 列表单元：白底 12dp 卡片，卡片之间靠间距分隔，不再画整行分隔线。
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClearViewTokens.shapes.card,
                        color = colors.surface1,
                        contentColor = colors.text1,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable(
                                onClickLabel = when {
                                    !selectionMode -> "打开检查记录"
                                    isSelected -> "取消选择该记录"
                                    else -> "选择该记录"
                                },
                                onClick = {
                                    if (selectionMode) toggleSelection() else onOpenRecord(record)
                                },
                            ).semantics {
                                selected = selectionMode && isSelected
                                // 勾选框会让整行合并成一个 checkable 节点，必须自带可读名称。
                                contentDescription = historyRowDescription(record)
                            }
                                .background(if (selectionMode && isSelected) colors.infoContainer else colors.surface1)
                                .padding(spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                                verticalAlignment = Alignment.Top,
                            ) {
                                RecordThumbnail(record, reloadKey = loading)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                    HistoryText(
                                        record.displayDiseaseName(),
                                        style = ClearViewTokens.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                        color = colors.text1,
                                    )
                                    StatusTag(
                                        text = historyStatusLabel(record.status),
                                        tone = when (record.status) {
                                            DiagnosisStatus.SUCCESS -> StatusTone.NEUTRAL
                                            DiagnosisStatus.PENDING_UPLOAD -> StatusTone.NEUTRAL
                                            DiagnosisStatus.FAILED -> StatusTone.CRITICAL
                                        },
                                    )
                                }
                                if (selectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { toggleSelection() },
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        ToolButton(
                                            Icons.Outlined.ChatBubbleOutline,
                                            "打开对话 ${record.recordId}",
                                            { onOpenConversation(record) },
                                            modifier = Modifier.size(44.dp),
                                        )
                                        ToolButton(
                                            Icons.Outlined.DeleteOutline,
                                            "删除记录 ${record.recordId}",
                                            { pendingDeleteRecord = record },
                                            modifier = Modifier.size(44.dp),
                                        )
                                    }
                                }
                            }
                            RecordBody(record = record)
                        }
                    }
                }
            }
            // Pagination must also remain reachable when none of the loaded records match.
            if (hasMore) {
                item {
                    ActionButton(
                        text = if (loadingMore) "正在加载更多…" else "加载更多历史记录",
                        onClick = onLoadMore,
                        enabled = !loading && !loadingMore,
                        kind = ButtonKind.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    pendingDeleteRecord?.let { record ->
        Dialog(onDismissRequest = { pendingDeleteRecord = null }) {
            Surface(
                shape = ClearViewTokens.shapes.card,
                color = colors.surface1,
                contentColor = colors.text1,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    PageTitle("删除检查记录")
                    MetaRow("记录编号", record.recordId)
                    HistoryText("确认后，这条诊断记录将从历史记录中删除。")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        ActionButton("取消", { pendingDeleteRecord = null }, kind = ButtonKind.SECONDARY)
                        ActionButton(
                            "确认删除",
                            {
                                pendingDeleteRecord = null
                                onDeleteRecord(record)
                            },
                            kind = ButtonKind.DANGER,
                        )
                    }
                }
            }
        }
    }

    pendingBatchDelete?.let { batch ->
        Dialog(onDismissRequest = { pendingBatchDelete = null }) {
            Surface(
                shape = ClearViewTokens.shapes.card,
                color = colors.surface1,
                contentColor = colors.text1,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    PageTitle("批量删除检查记录")
                    MetaRow("待删除条数", batch.size.toString())
                    MetaRow(
                        "包含记录",
                        batch.take(3).joinToString("、") { it.recordId } +
                            if (batch.size > 3) " 等" else "",
                    )
                    HistoryText("确认后，这些记录将从本机缓存与后端历史中删除，且不可恢复。")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        ActionButton("取消", { pendingBatchDelete = null }, kind = ButtonKind.SECONDARY)
                        ActionButton(
                            "确认删除",
                            {
                                pendingBatchDelete = null
                                selectionMode = false
                                selectedIds = emptySet()
                                onDeleteRecords(batch)
                            },
                            kind = ButtonKind.DANGER,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordThumbnail(record: DiagnosisReport, reloadKey: Boolean) {
    DiagnosisThumbnail(
        path = record.sourceImagePath,
        contentDescription = "记录 ${record.recordId} 的本地原始影像",
        reloadKey = reloadKey,
        modifier = Modifier.size(80.dp),
    )
}

@Composable
private fun RecordBody(record: DiagnosisReport) {
    val spacing = ClearViewTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        MetaRow("记录编号", record.recordId)
        MetaRow("检查时间", record.createdAt)
    }
}

@Composable
private fun HistoryText(
    text: String,
    style: TextStyle = ClearViewTokens.typography.body,
    color: Color = ClearViewTokens.colors.text2,
) {
    val type = ClearViewTokens.typography
    Text(
        text = buildAnnotatedString {
            var start = 0
            Regex("[A-Za-z]*[0-9][A-Za-z0-9._:/%+°℃-]*").findAll(text).forEach { match ->
                append(text.substring(start, match.range.first))
                withStyle(SpanStyle(fontFamily = type.mono, fontFeatureSettings = "tnum")) { append(match.value) }
                start = match.range.last + 1
            }
            append(text.substring(start))
        },
        style = style,
        color = color,
    )
}

private fun historyRowDescription(record: DiagnosisReport): String =
    "${record.displayDiseaseName()}，${historyStatusLabel(record.status)}，记录 ${record.recordId}"

private fun historyStatusLabel(status: DiagnosisStatus): String = when (status) {
    DiagnosisStatus.SUCCESS -> "已完成"
    DiagnosisStatus.PENDING_UPLOAD -> "待分析"
    DiagnosisStatus.FAILED -> "已失败"
}
