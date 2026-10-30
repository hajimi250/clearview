package com.chengying.clearview.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chengying.clearview.core.designsystem.component.ActionButton
import com.chengying.clearview.core.designsystem.component.ActionableGuidanceRow
import com.chengying.clearview.core.designsystem.component.ButtonKind
import com.chengying.clearview.core.designsystem.component.MetaRow
import com.chengying.clearview.core.designsystem.component.PageTitle
import com.chengying.clearview.core.designsystem.component.SectionHeader
import com.chengying.clearview.core.designsystem.component.StatusTag
import com.chengying.clearview.core.designsystem.component.StatusTone
import com.chengying.clearview.core.designsystem.component.ToolButton
import com.chengying.clearview.core.designsystem.component.WarningBanner
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.designsystem.theme.ThemeMode

private data class NoticeSection(val title: String, val detail: String)
private data class HelpTopic(val icon: ImageVector, val title: String, val detail: String)
private data class HelpFaq(val question: String, val answer: String)

data class ServiceHealthStatus(
    val checking: Boolean = false,
    val inferenceOnline: Boolean? = null,
    val backendBaseUrl: String = "",
)

private val privacyNoticeSections = listOf(
    NoticeSection(
        title = "适用范围",
        detail = "本须知适用于 ClearView 中的拍照、相册导入、智能分析、历史记录和辅助问答等功能。",
    ),
    NoticeSection(
        title = "我们会处理哪些内容",
        detail = "当你主动拍摄或导入图片时，应用会处理原始图像、本次分析时间、分类候选与病灶分割信息等分析相关数据。",
    ),
    NoticeSection(
        title = "助手说明",
        detail = "带入选中的诊断记录后，助手会把结构化文本摘要和你输入的问题发送至后端服务，由已配置的模型生成回答；不会上传影像。模型地址与访问凭据仅保存在服务端，不会保存到手机。",
    ),
    NoticeSection(
        title = "本地存储与网络传输",
        detail = "拍摄或导入的图片可能先进入应用缓存目录；已保存的诊断样本、掩膜图和历史记录会保存在应用本地。触发智能分析时，相关图片会按功能需要发送到项目配置的服务端；助手问答仅发送文本摘要，不包含影像文件。",
    ),
    NoticeSection(
        title = "使用目的",
        detail = "这些信息仅用于分析、结果展示、历史回看、知识比对和辅助建议生成，不用于与本项目无关的营销用途。",
    ),
    NoticeSection(
        title = "你的控制权",
        detail = "你可以自行决定是否拍照、是否从相册导入、是否提交分析、是否保存待分析记录，以及是否把当前诊断记录带入助手解读。",
    ),
)

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    appVersion: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onClearLocalCache: () -> Unit,
    onOpenDiagnosisHistory: () -> Unit = {},
    serviceHealthStatus: ServiceHealthStatus = ServiceHealthStatus(),
    onCheckServiceStatus: () -> Unit = {},
    localCacheStatusMessage: String? = null,
    onSaveBackendBaseUrl: (String) -> Unit = {},
) {
    var showPrivacyNotice by rememberSaveable { mutableStateOf(false) }
    var showHelpFeedbackPage by rememberSaveable { mutableStateOf(false) }
    var showClearLocalCacheDialog by rememberSaveable { mutableStateOf(false) }
    var showServiceStatusDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var showDeveloperDialog by rememberSaveable { mutableStateOf(false) }
    val spacing = ClearViewTokens.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ClearViewTokens.colors.background),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { PageTitle("设置与偏好") }
        item {
            SettingsGroup("常规设置") {
                Text(
                    "显示主题",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
                    style = ClearViewTokens.typography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = ClearViewTokens.colors.text2,
                )
                Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        val selected = themeMode == mode
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onThemeModeChange(mode) })
                                .heightIn(min = 52.dp)
                                .padding(horizontal = spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            Text(
                                mode.label,
                                modifier = Modifier.weight(1f),
                                style = ClearViewTokens.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                color = ClearViewTokens.colors.text1,
                            )
                            Icon(
                                imageVector = if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = mode.label,
                                tint = if (selected) ClearViewTokens.colors.info else ClearViewTokens.colors.textDisabled,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (index != ThemeMode.entries.lastIndex) SettingsDivider()
                    }
                }
                SettingsDivider()
                SettingActionRow("诊断历史", onClick = onOpenDiagnosisHistory)
            }
        }
        item {
            SettingsGroup("隐私与存储") {
                SettingActionRow("用户隐私须知", onClick = { showPrivacyNotice = true })
                SettingsDivider()
                SettingActionRow(
                    title = "清除本地缓存",
                    detail = localCacheStatusMessage,
                    onClick = { showClearLocalCacheDialog = true },
                )
            }
        }
        item {
            SettingsGroup("支持与关于") {
                SettingValueRow("版本号", appVersion)
                SettingsDivider()
                SettingActionRow("帮助与反馈", onClick = { showHelpFeedbackPage = true })
                SettingsDivider()
                SettingActionRow("服务状态", onClick = {
                    showServiceStatusDialog = true
                    onCheckServiceStatus()
                })
                SettingsDivider()
                SettingActionRow("检查更新", onClick = { showUpdateDialog = true })
                SettingsDivider()
                SettingActionRow("开发者选项", onClick = { showDeveloperDialog = true })
            }
        }
    }

    if (showPrivacyNotice) PrivacyNoticeDialog(onDismiss = { showPrivacyNotice = false })
    if (showHelpFeedbackPage) HelpFeedbackDialog(onDismiss = { showHelpFeedbackPage = false })
    if (showClearLocalCacheDialog) {
        SettingsDialog(
            title = "清除本地缓存",
            onDismiss = { showClearLocalCacheDialog = false },
            actions = {
                ActionButton("取消", { showClearLocalCacheDialog = false }, kind = ButtonKind.SECONDARY)
                ActionButton("确认清除", {
                    showClearLocalCacheDialog = false
                    onClearLocalCache()
                }, kind = ButtonKind.DANGER)
            },
        ) {
            WarningBanner("将清除诊断历史记录、助手历史记录，以及本地保存的样本图片和缓存文件。")
            SettingsText("已同步到服务端的记录不会被删除，但后续重新同步时仍可能再次出现。")
        }
    }
    if (showServiceStatusDialog) {
        ServiceStatusDialog(
            status = serviceHealthStatus,
            onSaveBackendBaseUrl = onSaveBackendBaseUrl,
            onDismiss = { showServiceStatusDialog = false },
            onCheckServiceStatus = onCheckServiceStatus,
        )
    }
    if (showUpdateDialog) {
        SettingsDialog(title = "检查更新", onDismiss = { showUpdateDialog = false }) {
            MetaRow("当前安装版本", appVersion)
            StatusTag("当前是最新版本", StatusTone.CONFIRMED)
            SettingsText("未检测到可用更新。")
        }
    }
    if (showDeveloperDialog) {
        DeveloperOptionsDialog(
            onUseUsbLoopback = { onSaveBackendBaseUrl(USB_BACKEND_BASE_URL) },
            onDismiss = { showDeveloperDialog = false },
        )
    }
}

@Composable
private fun SettingActionRow(title: String, detail: String? = null, onClick: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "打开$title", onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                title,
                style = ClearViewTokens.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = ClearViewTokens.colors.text1,
            )
            detail?.let { SettingsText(it) }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "打开$title",
            tint = ClearViewTokens.colors.textDisabled,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SettingValueRow(title: String, value: String) {
    val spacing = ClearViewTokens.spacing
    val type = ClearViewTokens.typography
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = type.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = ClearViewTokens.colors.text1,
        )
        Text(
            value,
            style = type.num.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = ClearViewTokens.colors.text3,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ServiceStatusDialog(
    status: ServiceHealthStatus,
    onSaveBackendBaseUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onCheckServiceStatus: () -> Unit,
) {
    SettingsDialog(
        title = "服务状态",
        onDismiss = onDismiss,
        actions = {
            ActionButton("重新检查", onCheckServiceStatus, enabled = !status.checking, kind = ButtonKind.SECONDARY)
            ActionButton("确定", onDismiss)
        },
    ) {
        if (status.checking) StatusTag("正在检查服务状态")
        MetaRow(
            label = "在线推理",
            value = when (status.inferenceOnline) {
                true -> "在线"
                false -> "离线"
                null -> if (status.checking) "检查中" else "状态未知"
            },
            numeric = false,
            valueColor = if (status.inferenceOnline == true) ClearViewTokens.colors.ok else null,
        )
        SettingsDivider(indent = 0.dp)
        ServiceEndpointEditor(current = status.backendBaseUrl, onSave = onSaveBackendBaseUrl)
    }
}

private const val USB_BACKEND_BASE_URL = "http://127.0.0.1:8000/"
private val endpointIpv4Pattern = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

/**
 * 只接受 http:// + IPv4 + 端口。网络层的 normalizedBaseUrl() 只补尾斜杠，
 * 缺前缀或缺端口都不会被自动补齐，所以在这里一次性拒掉，避免存进一个必然请求失败的地址。
 */
private fun normalizeBackendBaseUrl(raw: String): String? {
    val trimmed = raw.trim().removeSuffix("/")
    val authority = trimmed.removePrefix("http://")
    if (authority == trimmed) return null
    val host = authority.substringBeforeLast(':')
    val port = authority.substringAfterLast(':', "").toIntOrNull() ?: return null
    if (!host.matches(endpointIpv4Pattern)) return null
    if (host.split('.').any { (it.toIntOrNull() ?: return null) > 255 }) return null
    if (port !in 1..65535) return null
    return "http://$host:$port/"
}

@Composable
private fun ServiceEndpointEditor(current: String, onSave: (String) -> Unit) {
    var draft by remember { mutableStateOf(current) }
    var invalid by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    // 默认态不放任何提示文案；仅在格式错误或保存成功时显示一行说明。
    val supportingText: (@Composable () -> Unit)? = when {
        invalid -> {
            { Text("格式应为 http://IPv4:端口，例如 http://192.168.1.23:8000/；不支持 https、裸 IP 或省略端口。") }
        }
        saved -> {
            { Text("已保存，正在按新地址自动重新检查服务状态。") }
        }
        else -> null
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { value ->
            draft = value
            invalid = false
            saved = false
        },
        label = { Text("服务地址") },
        placeholder = { Text("http://192.168.1.23:8000/") },
        supportingText = supportingText,
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    ActionButton(
        text = "保存",
        onClick = {
            val normalized = normalizeBackendBaseUrl(draft)
            if (normalized == null) {
                invalid = true
            } else {
                onSave(normalized)
                saved = true
                invalid = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeveloperOptionsDialog(onUseUsbLoopback: () -> Unit, onDismiss: () -> Unit) {
    var applied by remember { mutableStateOf(false) }
    SettingsDialog(title = "开发者选项", onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "USB 回环地址",
                style = ClearViewTokens.typography.body,
                color = ClearViewTokens.colors.text1,
            )
            // 地址用 12sp 等宽，22 个字符在窄屏弹窗上刚好单行放得下。
            Text(
                USB_BACKEND_BASE_URL,
                modifier = Modifier.weight(1f),
                style = ClearViewTokens.typography.numSmall,
                color = ClearViewTokens.colors.text3,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ActionButton(
            text = "使用该地址",
            onClick = {
                onUseUsbLoopback()
                applied = true
            },
            kind = ButtonKind.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )
        if (applied) SettingsText("已保存，正在按该地址重新检查服务状态。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit = { ActionButton("确定", onDismiss) },
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = ClearViewTokens.spacing
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(spacing.lg), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = ClearViewTokens.shapes.card,
                color = ClearViewTokens.colors.surface1,
                contentColor = ClearViewTokens.colors.text1,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    PageTitle(title)
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                        content = content,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) { actions() }
                }
            }
        }
    }
}

@Composable
private fun SettingsOverlayPage(title: String, onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ClearViewTokens.colors.background,
            contentColor = ClearViewTokens.colors.text1,
            shape = ClearViewTokens.shapes.none,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(ClearViewTokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.sm),
                ) {
                    ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回设置", onDismiss)
                    PageTitle(title, modifier = Modifier.weight(1f))
                }
                SettingsDivider(indent = 0.dp)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(ClearViewTokens.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.lg),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.xs)) {
        SectionHeader(label, modifier = Modifier.padding(start = ClearViewTokens.spacing.lg))
        SettingsCard(content = content)
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    gap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ClearViewTokens.shapes.card,
        color = ClearViewTokens.colors.surface1,
        contentColor = ClearViewTokens.colors.text1,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

@Composable
private fun SettingsOverlayPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SettingsCard(
        modifier = modifier,
        contentPadding = ClearViewTokens.spacing.lg,
        gap = ClearViewTokens.spacing.sm,
        content = content,
    )
}

@Composable
private fun SettingsDivider(indent: Dp = ClearViewTokens.spacing.lg) {
    HorizontalDivider(
        modifier = Modifier.padding(start = indent),
        thickness = 0.5.dp,
        color = ClearViewTokens.colors.line,
    )
}

@Composable
private fun PrivacyNoticeDialog(onDismiss: () -> Unit) {
    SettingsOverlayPage(
        title = "用户隐私须知",
        onDismiss = onDismiss,
    ) {
        item {
            SettingsOverlayPanel {
                StatusTag("使用与存储说明")
                SettingsText("以下内容说明图像、历史记录和助手在当前版本中的处理方式与使用边界。")
            }
        }
        privacyNoticeSections.forEach { section ->
            item {
                SettingsOverlayPanel { PrivacyNoticeSection(section) }
            }
        }
        item {
            WarningBanner("请避免上传包含人脸、证件、联系方式或与分析无关的敏感内容；提交前请确认图片和文字中不含可识别个人身份的信息。")
        }
        item { ActionButton("我已知晓", onDismiss, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun PrivacyNoticeSection(section: NoticeSection) {
    Column(verticalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.xs)) {
        Text(section.title, style = ClearViewTokens.typography.heading, color = ClearViewTokens.colors.text1)
        SettingsText(section.detail)
    }
}

@Composable
private fun HelpFeedbackDialog(onDismiss: () -> Unit) {
    val quickStartTopics = remember {
        listOf(
            HelpTopic(
                icon = Icons.Outlined.PhotoCamera,
                title = "采集或导入影像",
                detail = "进入拍摄页直接拍照，或从相册导入图片，保留清晰主体并避免严重反光；提交前请检查敏感信息。",
            ),
            HelpTopic(
                icon = Icons.Outlined.ImageSearch,
                title = "提交智能分析",
                detail = "分析完成后可查看分类候选、病灶分割和参考建议，也可以先保存为待分析记录。实际返回内容取决于当前推理服务。",
            ),
            HelpTopic(
                icon = Icons.Outlined.AccessTime,
                title = "回看历史与复查",
                detail = "历史页会保留本地影像与分析结果，便于后续复查、比对不同时间的记录。",
            ),
        )
    }
    val adviceTopics = remember {
        listOf(
            HelpTopic(
                icon = Icons.Outlined.Collections,
                title = "图片尽量单一聚焦",
                detail = "建议每张图片保留主要分析区域，避免把无关物体或敏感信息一起拍入。",
            ),
            HelpTopic(
                icon = Icons.Outlined.WarningAmber,
                title = "异常区域要清晰可见",
                detail = "保持原始图像的纹理和边界可辨认。请勿通过滤镜、着色或装饰性处理改变影像内容。",
            ),
            HelpTopic(
                icon = Icons.Outlined.Book,
                title = "乳腺超声教学资料",
                detail = "知识库包含 9 条乳腺超声教学资料（3 篇模型标签 + 6 篇延伸阅读），可按分类和关键词查阅超声表现与复核要点",
            ),
        )
    }
    val resultTopics = remember {
        listOf(
            HelpTopic(
                icon = Icons.Outlined.ImageSearch,
                title = "疑似所见不是唯一结论",
                detail = "自动分析仅供辅助参考，建议结合原始影像、检查背景与变化情况由专业人员复核。",
            ),
            HelpTopic(
                icon = Icons.Outlined.AutoAwesome,
                title = "助手提供智能解读",
                detail = "你可以把已保存记录带入助手，由服务端已配置的模型结合记录回答问题。回复由模型生成，需结合原始影像和实际情况核实，不能替代医师判断。",
            ),
        )
    }
    val faqItems = remember {
        listOf(
            HelpFaq(
                question = "为什么点了分析后没有立即返回结果？",
                answer = "在线分析依赖项目配置的服务端。若网络不可达、服务未启动或请求超时，结果页可能只保留影像路径而没有完整分析结果。",
            ),
            HelpFaq(
                question = "为什么结果和实际判断不一致？",
                answer = "请先检查图像清晰度与分析服务的适用范围，再结合原始影像、历史记录和专业意见复核，不要只依赖单次识别结果。",
            ),
            HelpFaq(
                question = "历史里的“待分析记录”是什么意思？",
                answer = "这表示影像已经保存在本地，但你当时没有提交到分析服务。你可以后续重新打开相关记录继续处理。",
            ),
        )
    }
    SettingsOverlayPage(
        title = "帮助与反馈",
        onDismiss = onDismiss,
    ) {
        item {
            SettingsOverlayPanel {
                StatusTag("仅作辅助参考", tone = StatusTone.WARN)
                SettingsText("ClearView 用于图像采集、结果回看和辅助解读，不替代专业复核或正式诊断。知识库与助手解读内容仅作辅助参考，不替代医师判断。")
                SettingsText("请先确认资料适用范围，不要把非医疗资料作为临床依据。")
            }
        }
        item { HelpSectionPanel("快速上手") { HelpTopics(quickStartTopics) } }
        item { HelpSectionPanel("拍摄与上传建议") { HelpTopics(adviceTopics) } }
        item { HelpSectionPanel("诊断结果怎么看") { HelpTopics(resultTopics) } }
        item {
            HelpSectionPanel("常见问题") {
                faqItems.forEachIndexed { index, faq ->
                    HelpFaqItem(faq)
                    if (index != faqItems.lastIndex) SettingsDivider(indent = 0.dp)
                }
            }
        }
    }
}

@Composable
private fun HelpTopics(topics: List<HelpTopic>) {
    topics.forEachIndexed { index, topic ->
        ActionableGuidanceRow(icon = topic.icon, title = topic.title, detail = topic.detail)
        if (index != topics.lastIndex) SettingsDivider(indent = 0.dp)
    }
}

@Composable
private fun HelpSectionPanel(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.xs)) {
        SectionHeader(title, modifier = Modifier.padding(start = ClearViewTokens.spacing.lg))
        SettingsOverlayPanel { content() }
    }
}

@Composable
private fun HelpFaqItem(faq: HelpFaq) {
    Column(verticalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.xs)) {
        Text(faq.question, style = ClearViewTokens.typography.heading, color = ClearViewTokens.colors.text1)
        SettingsText(faq.answer)
    }
}

@Composable
private fun SettingsText(text: String, style: TextStyle = ClearViewTokens.typography.body) {
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
        color = ClearViewTokens.colors.text2,
    )
}
