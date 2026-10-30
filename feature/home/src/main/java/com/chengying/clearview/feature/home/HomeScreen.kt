package com.chengying.clearview.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chengying.clearview.core.designsystem.component.ActionButton
import com.chengying.clearview.core.designsystem.component.ButtonKind
import com.chengying.clearview.core.designsystem.component.DiagnosisThumbnail
import com.chengying.clearview.core.designsystem.component.PageTitle
import com.chengying.clearview.core.designsystem.component.Panel
import com.chengying.clearview.core.designsystem.component.SectionHeader
import com.chengying.clearview.core.designsystem.component.StatusTag
import com.chengying.clearview.core.designsystem.component.StatusTone
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onStartDiagnosis: () -> Unit,
    recentDiagnoses: List<DiagnosisReport>,
    onOpenHistory: () -> Unit,
    onOpenRecord: (DiagnosisReport) -> Unit,
    onRetryRecord: (DiagnosisReport) -> Unit,
    onOpenSettings: () -> Unit,
    serviceOnline: Boolean?,
    serviceChecking: Boolean,
) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    val actionable = recentDiagnoses.firstOrNull {
        it.status == DiagnosisStatus.PENDING_UPLOAD || it.status == DiagnosisStatus.FAILED
    }
    val latest = recentDiagnoses.firstOrNull()
    val pendingCount = recentDiagnoses.count { it.status == DiagnosisStatus.PENDING_UPLOAD }
    val failedCount = recentDiagnoses.count { it.status == DiagnosisStatus.FAILED }
    val featuredRecordId = when {
        recentDiagnoses.isEmpty() -> null
        actionable != null -> actionable.recordId
        serviceOnline == false -> null
        else -> latest?.recordId
    }
    val otherRecords = recentDiagnoses.filter { it.recordId != featuredRecordId }.take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        HomeHeader(
            serviceOnline = serviceOnline,
            serviceChecking = serviceChecking,
            onOpenSettings = onOpenSettings,
        )
        NewAnalysisCta(onClick = onStartDiagnosis)
        if (recentDiagnoses.isEmpty()) {
            // 首次使用空状态：用引导与模型说明填充，不再只放单行空提示；知识库入口只在底部导航。
            if (serviceOnline == false) {
                ServiceOfflineCard(onOpenSettings = onOpenSettings)
            }
            FirstUseGuidePanel()
            ModelScopePanel()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RecentSectionHeader(onOpenHistory = onOpenHistory)
                when {
                    actionable != null -> PendingWorkCard(
                        record = actionable,
                        pendingCount = pendingCount,
                        failedCount = failedCount,
                        onOpenRecord = { onOpenRecord(actionable) },
                        onContinue = { onRetryRecord(actionable) },
                    )
                    serviceOnline == false -> ServiceOfflineCard(onOpenSettings = onOpenSettings)
                    else -> latest?.let { record ->
                        LatestStudyCard(record = record, onOpen = { onOpenRecord(record) })
                    }
                }
                if (otherRecords.isNotEmpty()) {
                    Panel(contentPadding = 0.dp) {
                        Column {
                            otherRecords.forEachIndexed { index, record ->
                                if (index > 0) {
                                    // QQ 内缩分隔线：0.5dp，起点对齐缩略图之后的文字起始线。
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        thickness = 0.5.dp,
                                        color = colors.line,
                                    )
                                }
                                CompactRecordRow(
                                    record = record,
                                    onClick = { onOpenRecord(record) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    serviceOnline: Boolean?,
    serviceChecking: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PageTitle(
            title = "影像工作台",
            modifier = Modifier.weight(1f),
        )
        ServiceStatusBadge(
            serviceOnline = serviceOnline,
            serviceChecking = serviceChecking,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun ServiceStatusBadge(
    serviceOnline: Boolean?,
    serviceChecking: Boolean,
    onClick: () -> Unit,
) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    val label = when {
        serviceChecking -> "检测中"
        serviceOnline == true -> "服务在线"
        serviceOnline == false -> "服务未连接"
        else -> "检测中"
    }
    val dotColor = when {
        serviceChecking || serviceOnline == null -> colors.text3
        serviceOnline == true -> colors.ok
        else -> colors.critical
    }
    Row(
        modifier = Modifier
            .clip(ClearViewTokens.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "打开服务设置", onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(label, style = ClearViewTokens.typography.label, color = colors.text2)
    }
}

@Composable
private fun NewAnalysisCta(onClick: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    // QQ 主按钮：实心天蓝 + 白字，无描边、无阴影，圆角与卡片同档。
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClearViewTokens.shapes.card,
        color = colors.info,
        contentColor = colors.onInfo,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onInfo, modifier = Modifier.size(24.dp))
            Text("新建分析", style = ClearViewTokens.typography.heading, color = colors.onInfo)
        }
    }
}

@Composable
private fun RecentSectionHeader(onOpenHistory: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionHeader("最近检查", modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(ClearViewTokens.shapes.small)
                .clickable(role = Role.Button, onClickLabel = "查看全部检查记录", onClick = onOpenHistory)
                .heightIn(min = 44.dp)
                .padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("全部", style = ClearViewTokens.typography.label, color = colors.info)
            Text(
                text = "›",
                style = ClearViewTokens.typography.label,
                color = colors.info,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun LatestStudyCard(record: DiagnosisReport, onOpen: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    val status = homeStatusDisplay(record)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        // §4.7 影像保真红线：影像容器零圆角、不进卡片裁切，前景 Fit 完整显示，边缘信息不缺失。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            // 底图：同一影像裁切铺满宽度并虚化压暗，避免前景 Fit 露出大片空白。
            DiagnosisThumbnail(
                path = record.sourceImagePath,
                contentDescription = "最近一次检查的影像背景",
                modifier = Modifier.fillMaxSize().blur(24.dp),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
            // 前景：完整显示整幅影像；空掩码记录不再绘制任何东西。
            DiagnosisThumbnail(
                path = record.sourceImagePath,
                contentDescription = "最近一次检查的影像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                containerColor = Color.Transparent,
            )
            MaskOutlineOverlay(
                maskPath = record.maskImagePath,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "最近一次检查的分割轮廓",
            )
        }
        Panel(contentPadding = 0.dp) {
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = homeRecordTitle(record),
                        modifier = Modifier.weight(1f),
                        style = ClearViewTokens.typography.heading,
                        color = colors.text1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusTag(status.label, status.tone)
                }
                HomeContentText(
                    text = homeRecordMetaLine(record),
                    style = ClearViewTokens.typography.bodySmall,
                    secondary = true,
                )
                ActionButton(
                    text = "查看结果",
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.SECONDARY,
                )
            }
        }
    }
}

@Composable
private fun PendingWorkCard(
    record: DiagnosisReport,
    pendingCount: Int,
    failedCount: Int,
    onOpenRecord: () -> Unit,
    onContinue: () -> Unit,
) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ClearViewTokens.shapes.card,
        color = colors.surface1,
        contentColor = colors.text1,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = homePendingSummary(pendingCount, failedCount),
                    modifier = Modifier.weight(1f),
                    style = ClearViewTokens.typography.heading,
                    color = colors.text1,
                )
                StatusTag("待处理", StatusTone.WARN)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ClearViewTokens.shapes.small)
                    .clickable(role = Role.Button, onClickLabel = "打开这条检查记录", onClick = onOpenRecord)
                    .padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                DiagnosisThumbnail(
                    path = record.sourceImagePath,
                    contentDescription = "${homeRecordTitle(record)}的影像缩略图",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(ClearViewTokens.shapes.small),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = homeRecordTitle(record),
                        style = ClearViewTokens.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                        color = colors.text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HomeContentText(
                        text = homeRecordMetaLine(record, includeStatus = true),
                        style = ClearViewTokens.typography.bodySmall,
                        secondary = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "›",
                    style = ClearViewTokens.typography.title,
                    color = colors.textDisabled,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            ActionButton(
                text = if (record.status == DiagnosisStatus.PENDING_UPLOAD) "继续分析" else "重试分析",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServiceOfflineCard(onOpenSettings: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "服务未连接",
                    modifier = Modifier.weight(1f),
                    style = ClearViewTokens.typography.heading,
                    color = colors.text1,
                )
                StatusTag("离线", StatusTone.WARN)
            }
            Text(
                text = "分析服务暂时无法连接，可前往设置检查服务地址与网络。已保存的影像仍可查看。",
                style = ClearViewTokens.typography.bodySmall,
                color = colors.text2,
            )
            ActionButton(
                text = "去设置检查",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.SECONDARY,
            )
        }
    }
}

@Composable
private fun MaskOutlineOverlay(
    maskPath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    var outline by remember(maskPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(context, maskPath) {
        outline = withContext(Dispatchers.IO) { decodeMaskOutline(context, maskPath) }
    }
    val bitmap = outline ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

private fun decodeMaskOutline(context: Context, path: String?): Bitmap? = runCatching {
    if (path.isNullOrBlank()) return null
    val uri = Uri.parse(path)
    fun open(): InputStream? = when (uri.scheme) {
        null -> File(path).inputStream()
        "file" -> uri.path?.let { File(it).inputStream() }
        "content" -> context.contentResolver.openInputStream(uri)
        else -> null
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val source = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    for (index in pixels.indices) {
        pixels[index] = source.getPixel(index % width, index / width)
    }
    source.recycle()
    fun foreground(index: Int): Boolean =
        (pixels[index] ushr 24) != 0 && (pixels[index] and 0x00FFFFFF) != 0
    val outline = IntArray(width * height)
    // 极淡的分割轮廓：约 40% 不透明度的红色描边，仅作视觉焦点，不遮蔽影像。
    val stroke = (0x66 shl 24) or 0x00FF453A
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            if (!foreground(index)) continue
            val isEdge = x == 0 || y == 0 || x == width - 1 || y == height - 1 ||
                !foreground(index - 1) || !foreground(index + 1) ||
                !foreground(index - width) || !foreground(index + width)
            if (isEdge) outline[index] = stroke
        }
    }
    Bitmap.createBitmap(outline, width, height, Bitmap.Config.ARGB_8888)
}.getOrNull()

@Composable
private fun CompactRecordRow(record: DiagnosisReport, onClick: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "打开检查记录", onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        DiagnosisThumbnail(
            path = record.sourceImagePath,
            contentDescription = "${homeRecordTitle(record)}的影像缩略图",
            modifier = Modifier
                .size(44.dp)
                .clip(ClearViewTokens.shapes.small),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = homeRecordTitle(record),
                style = ClearViewTokens.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = colors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HomeContentText(
                text = homeRecordMetaLine(record, includeStatus = true),
                style = ClearViewTokens.typography.bodySmall,
                secondary = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            style = ClearViewTokens.typography.title,
            color = colors.textDisabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun FirstUseGuidePanel() {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader("第一次使用")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                GuideStepRow("①", "选择一张原始乳腺超声图")
                GuideStepRow("②", "提交后等待主分类与病灶分割")
                GuideStepRow("③", "查看结果和适用范围")
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                Text(
                    text = "暂不支持截图拼接、滤镜或翻拍图。",
                    style = ClearViewTokens.typography.caption,
                    color = colors.text3,
                )
                Text(
                    text = "完成首次分析后，记录会显示在这里。",
                    style = ClearViewTokens.typography.caption,
                    color = colors.text3,
                )
            }
        }
    }
}

@Composable
private fun GuideStepRow(number: String, text: String) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Top) {
        Text(number, style = ClearViewTokens.typography.body, color = colors.info)
        Text(text, style = ClearViewTokens.typography.body, color = colors.text1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelScopePanel() {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader("模型输出")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    listOf("良性病变", "恶性病变", "正常乳腺组织").forEach { label ->
                        StatusTag(label)
                    }
                }
                Text(
                    text = "结果页仅显示主分类和可疑病灶范围；不构成病理诊断，正常也不等于排除病变。",
                    style = ClearViewTokens.typography.bodySmall,
                    color = colors.text2,
                )
            }
        }
    }
}
