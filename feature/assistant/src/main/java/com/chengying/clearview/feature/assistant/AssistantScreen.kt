package com.chengying.clearview.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chengying.clearview.core.designsystem.component.ActionButton
import com.chengying.clearview.core.designsystem.component.BrandMark
import com.chengying.clearview.core.designsystem.component.ButtonKind
import com.chengying.clearview.core.designsystem.component.EmptyState
import com.chengying.clearview.core.designsystem.component.MetaRow
import com.chengying.clearview.core.designsystem.component.PageTitle
import com.chengying.clearview.core.designsystem.component.Panel
import com.chengying.clearview.core.designsystem.component.ToolButton
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.network.AssistantGateway
import com.chengying.clearview.core.network.AssistantMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AssistantDiagnosisContext(
    val recordId: String,
    val diagnosisNumber: String,
    val sourceLabel: String,
    val finding: String,
    val createdAt: String,
)

data class AssistantThreadSummary(
    val threadKey: String,
    val title: String,
    val createdAt: String?,
)

private const val USER_BUBBLE_WIDTH_RATIO = 0.78f
private const val INTRO_WIDTH_RATIO = 0.8f
private val SuggestionPrompts = listOf(
    "下一步该看什么科",
    "我该怎么向医生描述这个情况",
    "这类结果常见的可能诱因有哪些",
    "医生通常还会安排哪些检查来确认",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    conversationRecordId: String? = null,
    conversationKey: String = AssistantConversationStore.threadKey(conversationRecordId),
    conversationTitle: String? = null,
    diagnosisContexts: List<AssistantDiagnosisContext> = emptyList(),
    threads: List<AssistantThreadSummary> = emptyList(),
    initialPrompt: String? = null,
    assistantGateway: AssistantGateway? = null,
    onInitialPromptConsumed: () -> Unit = {},
    onConversationUpdated: (threadKey: String, messages: List<AssistantConversationMessage>) -> Unit = { _, _ -> },
    onSelectConversation: (threadKey: String) -> Unit = {},
    onStartNewConversation: () -> Unit = {},
) {
    val context = LocalContext.current
    val messages = remember(context, conversationKey) {
        mutableStateListOf<AssistantConversationMessage>().apply {
            addAll(AssistantConversationStore.readThread(context, conversationKey))
        }
    }
    val orderedContexts = remember(diagnosisContexts) {
        diagnosisContexts.sortedByDescending { it.diagnosisNumber }
    }
    val coroutineScope = rememberCoroutineScope()
    var input by remember(conversationKey) { mutableStateOf("") }
    var showDiagnosisContextDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showConversationList by remember { mutableStateOf(false) }
    var selectedContextIds by remember(conversationKey) { mutableStateOf(setOf<String>()) }
    var initialPromptHandled by remember(initialPrompt) { mutableStateOf(false) }
    var isSending by remember(conversationKey) { mutableStateOf(false) }
    var sendError by remember(conversationKey) { mutableStateOf<String?>(null) }
    var failedQuestion by remember(conversationKey) { mutableStateOf<String?>(null) }
    val selectedContexts = orderedContexts.filter { it.recordId in selectedContextIds }
    val referenceSummary = remember(selectedContexts) { buildReferenceSummary(selectedContexts) }
    val transcriptState = rememberLazyListState()
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors

    fun persistMessages() {
        AssistantConversationStore.writeThread(context, conversationKey, messages)
        onConversationUpdated(conversationKey, messages.toList())
    }

    fun submitPrompt(prompt: String, isRetry: Boolean = false) {
        val question = prompt.trim()
        if (question.isEmpty() || isSending) return
        // The question is committed before the request so the transcript survives a failure.
        val historySnapshot = if (isRetry) messages.dropLast(1) else messages.toList()
        if (!isRetry) {
            messages += AssistantConversationMessage(text = question, isUser = true)
            persistMessages()
        }
        val contextSnapshot = selectedContexts
        val gateway = assistantGateway
        sendError = null
        failedQuestion = null
        if (gateway == null) {
            sendError = "助手服务未接入，请稍后重试或联系管理员。"
            failedQuestion = question
            return
        }
        isSending = true
        coroutineScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    gateway.chat(
                        buildAssistantMessages(
                            question = question,
                            contexts = contextSnapshot,
                            history = historySnapshot,
                        ),
                    )
                }
                messages += AssistantConversationMessage(text = reply.text, isUser = false)
                persistMessages()
                input = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                sendError = failure.message?.takeIf { it.isNotBlank() } ?: "助手请求失败，请稍后重试。"
                failedQuestion = question
            } finally {
                isSending = false
            }
        }
    }

    fun toggleContext(recordId: String) {
        selectedContextIds = if (recordId in selectedContextIds) {
            selectedContextIds - recordId
        } else {
            selectedContextIds + recordId
        }
    }

    fun selectAllContexts() {
        selectedContextIds = orderedContexts.mapTo(linkedSetOf()) { it.recordId }
    }

    fun clearContextSelection() {
        selectedContextIds = emptySet()
    }

    var linkedContextApplied by remember(conversationRecordId) { mutableStateOf(false) }
    LaunchedEffect(conversationRecordId, orderedContexts) {
        if (linkedContextApplied || conversationRecordId == null) return@LaunchedEffect
        val linked = orderedContexts.firstOrNull { it.recordId == conversationRecordId }
        if (linked != null) {
            if (selectedContextIds.isEmpty()) {
                selectedContextIds = setOf(linked.recordId)
            }
            linkedContextApplied = true
        }
    }

    LaunchedEffect(conversationKey) {
        onConversationUpdated(conversationKey, messages.toList())
    }
    LaunchedEffect(initialPrompt, conversationKey) {
        val prompt = initialPrompt?.trim().orEmpty()
        if (prompt.isNotEmpty() && !initialPromptHandled) {
            initialPromptHandled = true
            submitPrompt(prompt)
            onInitialPromptConsumed()
        }
    }

    val hasTranscriptHeader =
        conversationTitle != null || conversationRecordId != null || selectedContexts.isNotEmpty()
    val errorVisible = sendError != null && !isSending
    val transcriptItemCount = messages.size +
        (if (hasTranscriptHeader) 1 else 0) +
        (if (isSending) 1 else 0) +
        (if (errorVisible) 1 else 0)
    LaunchedEffect(conversationKey, transcriptItemCount) {
        if (transcriptItemCount > 0) transcriptState.animateScrollToItem(transcriptItemCount - 1)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val bubbleMaxWidth = maxWidth * USER_BUBBLE_WIDTH_RATIO
        val introMaxWidth = maxWidth * INTRO_WIDTH_RATIO
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.background),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(spacing.xxxl).padding(horizontal = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    PageTitle(
                        title = "辅助问答",
                        modifier = Modifier.weight(1f),
                    )
                    ToolButton(Icons.Outlined.Add, "新建对话", onStartNewConversation)
                    Surface(
                        onClick = { showConversationList = true },
                        shape = ClearViewTokens.shapes.medium,
                        color = colors.info,
                        contentColor = colors.onInfo,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Box(
                            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("对话记录", style = ClearViewTokens.typography.label)
                        }
                    }
                    Box(
                        modifier = Modifier.size(40.dp).clip(ClearViewTokens.shapes.medium)
                            .clickable(role = Role.Button) { showPrivacyDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "隐私说明",
                            tint = colors.text2,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !isSending) {
                    AssistantEmptyState(
                        introMaxWidth = introMaxWidth,
                        conversationTitle = conversationTitle,
                        conversationRecordId = conversationRecordId,
                        onSuggestion = { suggestion ->
                            input = suggestion
                            submitPrompt(suggestion)
                        },
                    )
                } else {
                    LazyColumn(
                        state = transcriptState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.md,
                            end = spacing.md,
                            top = spacing.md,
                            bottom = spacing.md + spacing.lg,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        if (hasTranscriptHeader) {
                            item {
                                AssistantTranscriptHeader(
                                    conversationTitle = conversationTitle,
                                    conversationRecordId = conversationRecordId,
                                    referenceSummary = referenceSummary,
                                )
                            }
                        }
                        items(messages.size) { index ->
                            AssistantMessageRow(
                                text = messages[index].text,
                                isUser = messages[index].isUser,
                                bubbleMaxWidth = bubbleMaxWidth,
                                showDisclaimer = !messages[index].isUser && index == messages.size - 1,
                            )
                        }
                        if (isSending) {
                            item { AssistantTypingRow() }
                        }
                        if (errorVisible) {
                            item {
                                AssistantErrorRow(
                                    message = sendError.orEmpty(),
                                    canRetry = failedQuestion != null && assistantGateway != null,
                                    onRetry = { failedQuestion?.let { submitPrompt(it, isRetry = true) } },
                                )
                            }
                        }
                    }
                }
            }
            AssistantInputBar(
                input = input,
                onInputChange = {
                    input = it
                    sendError = null
                    failedQuestion = null
                },
                canReference = orderedContexts.isNotEmpty(),
                referenceCount = selectedContexts.size,
                isSending = isSending,
                onOpenReferences = { showDiagnosisContextDialog = true },
                onSend = { submitPrompt(input) },
            )
        }
    }

    if (showPrivacyDialog) {
        Dialog(onDismissRequest = { showPrivacyDialog = false }) {
            Panel(contentPadding = spacing.lg) {
                PageTitle("隐私与边界")
                AssistantText(
                    text = if (conversationTitle != null) {
                        "当前对话已关联诊断记录；问答文本会发送至服务端，影像不会上传。"
                    } else {
                        "回复由服务端基于已引用记录生成；模型凭据仅保存在服务端，影像不会上传。"
                    },
                    color = colors.text2,
                )
                ActionButton(
                    text = "关闭",
                    onClick = { showPrivacyDialog = false },
                    kind = ButtonKind.TEXT,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showConversationList) {
        Dialog(
            onDismissRequest = { showConversationList = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight),
                    shape = ClearViewTokens.shapes.card,
                    color = colors.surface1,
                    contentColor = colors.text1,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PageTitle("对话记录", modifier = Modifier.weight(1f))
                            ToolButton(Icons.Outlined.Close, "关闭对话记录", { showConversationList = false })
                        }
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            if (threads.isEmpty()) {
                                item {
                                    EmptyState("暂无对话记录")
                                }
                            }
                            items(threads.size, key = { threads[it].threadKey }) { index ->
                                val thread = threads[index]
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable(role = Role.Button) {
                                            showConversationList = false
                                            onSelectConversation(thread.threadKey)
                                        }
                                        .heightIn(min = 52.dp)
                                        .padding(vertical = spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            thread.title,
                                            style = ClearViewTokens.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                            color = colors.text1,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (thread.threadKey == conversationKey) {
                                            Text(
                                                "当前",
                                                style = ClearViewTokens.typography.label,
                                                color = colors.info,
                                            )
                                        }
                                    }
                                    thread.createdAt?.let {
                                        Text(it, style = ClearViewTokens.typography.numSmall, color = colors.text3)
                                    }
                                }
                                if (index != threads.lastIndex) {
                                    HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                                }
                            }
                        }
                        ActionButton(
                            text = "关闭",
                            onClick = { showConversationList = false },
                            kind = ButtonKind.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showDiagnosisContextDialog) {
        Dialog(
            onDismissRequest = { showDiagnosisContextDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(spacing.lg), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight),
                    shape = ClearViewTokens.shapes.card,
                    color = colors.surface1,
                    contentColor = colors.text1,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        PageTitle("引用诊断记录")
                        MetaRow("已选择", selectedContexts.size.toString())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            ActionButton("全选", ::selectAllContexts, enabled = orderedContexts.isNotEmpty(), kind = ButtonKind.SECONDARY)
                            ActionButton("清空", ::clearContextSelection, enabled = selectedContextIds.isNotEmpty(), kind = ButtonKind.TEXT)
                        }
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            if (orderedContexts.isEmpty()) {
                                item { EmptyState("保存分析记录后可在此引用") }
                            }
                            items(orderedContexts.size, key = { orderedContexts[it].recordId }) { index ->
                                val item = orderedContexts[index]
                                val selected = item.recordId in selectedContextIds
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (selected) colors.infoContainer else colors.surface1)
                                        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { toggleContext(item.recordId) })
                                        .heightIn(min = 52.dp)
                                        .padding(vertical = spacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                        contentDescription = if (selected) "已选择记录" else "未选择记录",
                                        tint = if (selected) colors.info else colors.textDisabled,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                        Text(
                                            item.diagnosisNumber,
                                            style = ClearViewTokens.typography.num,
                                            color = colors.text1,
                                        )
                                        AssistantText("记录编号 ${item.recordId}", color = colors.text2)
                                        AssistantText(item.sourceLabel, color = colors.text2)
                                        AssistantText(item.finding, color = colors.text2)
                                    }
                                }
                                if (index != orderedContexts.lastIndex) {
                                    HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            ActionButton("完成", { showDiagnosisContextDialog = false })
                            ActionButton("关闭", { showDiagnosisContextDialog = false }, kind = ButtonKind.TEXT)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    canReference: Boolean,
    referenceCount: Int,
    isSending: Boolean,
    onOpenReferences: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = ClearViewTokens.colors
    val type = ClearViewTokens.typography
    val spacing = ClearViewTokens.spacing
    val lineColor = colors.line
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(colors.surface1)
            .drawBehind { drawRect(color = lineColor, size = Size(size.width, 0.5.dp.toPx())) }
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = spacing.xxxl).padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(ClearViewTokens.shapes.medium)
                    .clickable(enabled = canReference && !isSending, role = Role.Button, onClick = onOpenReferences),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = "引用记录",
                    tint = if (canReference) colors.text2 else colors.textDisabled,
                    modifier = Modifier.size(24.dp),
                )
                if (referenceCount > 0) {
                    Text(
                        text = referenceCount.toString(),
                        style = type.numSmall,
                        color = colors.text3,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (input.isEmpty()) {
                    Text("有问题，尽管问", style = type.body, color = colors.text3)
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending,
                    maxLines = 5,
                    textStyle = type.body.copy(color = colors.text1),
                    cursorBrush = SolidColor(colors.info),
                )
            }
            val canSend = input.isNotBlank() && !isSending
            Box(
                modifier = Modifier.size(40.dp).clip(ClearViewTokens.shapes.medium)
                    .background(if (canSend) colors.info else colors.transparent)
                    .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.info,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (canSend) colors.onInfo else colors.textDisabled,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantTranscriptHeader(
    conversationTitle: String?,
    conversationRecordId: String?,
    referenceSummary: String,
) {
    val colors = ClearViewTokens.colors
    val type = ClearViewTokens.typography
    Column(verticalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.xs)) {
        conversationTitle?.let {
            Text(it, style = type.label, color = colors.text2)
        }
        conversationRecordId?.let {
            Text("关联记录 $it", style = type.numSmall, color = colors.text3)
        }
        if (referenceSummary.isNotBlank()) {
            Text("已引用 $referenceSummary", style = type.numSmall, color = colors.text3)
        }
    }
}

@Composable
private fun AssistantMessageRow(text: String, isUser: Boolean, bubbleMaxWidth: Dp, showDisclaimer: Boolean = false) {
    val colors = ClearViewTokens.colors
    val spacing = ClearViewTokens.spacing
    val shape = ClearViewTokens.shapes.card
    val bubbleWidth = Modifier.widthIn(max = bubbleMaxWidth)
    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
            verticalAlignment = Alignment.Top,
        ) {
            SelectionContainer {
                AssistantText(
                    text = text,
                    modifier = bubbleWidth.background(colors.infoContainer, shape)
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }
            AssistantAvatar(isUser = true)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            AssistantAvatar(isUser = false)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SelectionContainer {
                    AssistantText(
                        text = text,
                        modifier = bubbleWidth.background(colors.surface1, shape)
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                    )
                }
                if (showDisclaimer) {
                    Text(
                        "内容由 AI 生成，请以医生最终判断为准。",
                        style = ClearViewTokens.typography.caption,
                        color = colors.text3,
                        modifier = Modifier.padding(start = spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantAvatar(isUser: Boolean) {
    val colors = ClearViewTokens.colors
    Box(
        modifier = Modifier.size(32.dp)
            .background(if (isUser) colors.surface3 else colors.infoContainer, ClearViewTokens.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (isUser) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "我",
                tint = colors.text2,
                modifier = Modifier.size(20.dp),
            )
        } else {
            BrandMark(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AssistantTypingRow() {
    val colors = ClearViewTokens.colors
    val spacing = ClearViewTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantAvatar(isUser = false)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier.size(4.dp).background(colors.text3, ClearViewTokens.shapes.floatingAction),
                )
            }
        }
    }
}

@Composable
private fun AssistantErrorRow(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    val colors = ClearViewTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = ClearViewTokens.typography.bodySmall,
            color = colors.critical,
            modifier = Modifier.weight(1f),
        )
        if (canRetry) {
            Text(
                text = "重试",
                style = ClearViewTokens.typography.label,
                color = colors.info,
                modifier = Modifier.clip(ClearViewTokens.shapes.medium)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = ClearViewTokens.spacing.md, vertical = ClearViewTokens.spacing.sm),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantEmptyState(
    introMaxWidth: Dp,
    conversationTitle: String?,
    conversationRecordId: String?,
    onSuggestion: (String) -> Unit,
) {
    val colors = ClearViewTokens.colors
    val type = ClearViewTokens.typography
    val spacing = ClearViewTokens.spacing
    Box(
        modifier = Modifier.fillMaxSize().padding(spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text("我是澄影 AI 影像解读助手", style = type.title, color = colors.text1)
            Text(
                text = conversationTitle?.let { "正在针对「$it」提问" }
                    ?: "引用记录后提问，回答基于记录生成",
                style = type.bodySmall,
                color = colors.text2,
                modifier = Modifier.widthIn(max = introMaxWidth),
            )
            if (conversationTitle != null && conversationRecordId != null) {
                Text(
                    text = "关联记录 $conversationRecordId",
                    style = type.numSmall,
                    color = colors.text3,
                )
            }
            FlowRow(
                modifier = Modifier.widthIn(max = introMaxWidth),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                SuggestionPrompts.forEach { suggestion ->
                    Surface(
                        onClick = { onSuggestion(suggestion) },
                        shape = ClearViewTokens.shapes.card,
                        color = colors.surface1,
                        contentColor = colors.text1,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Text(
                            text = suggestion,
                            style = type.label,
                            color = colors.text1,
                            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = spacing.md, vertical = spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ClearViewTokens.colors.text1,
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
        style = type.body,
        color = color,
        modifier = modifier,
    )
}

private const val MAX_CONTEXT_RECORDS = 20
private const val MAX_HISTORY_MESSAGES = 6
private const val MAX_HISTORY_MESSAGE_CHARS = 1200

private fun buildAssistantMessages(
    question: String,
    contexts: List<AssistantDiagnosisContext>,
    history: List<AssistantConversationMessage>,
): List<AssistantMessage> {
    val recentHistory = history.takeLast(MAX_HISTORY_MESSAGES).mapNotNull { message ->
        val content = message.text.trim().take(MAX_HISTORY_MESSAGE_CHARS)
        if (content.isEmpty()) {
            null
        } else {
            AssistantMessage(
                role = if (message.isUser) AssistantMessage.ROLE_USER else AssistantMessage.ROLE_ASSISTANT,
                content = content,
            )
        }
    }
    return buildList {
        add(AssistantMessage(role = AssistantMessage.ROLE_SYSTEM, content = buildSystemPrompt(contexts)))
        addAll(recentHistory)
        add(AssistantMessage(role = AssistantMessage.ROLE_USER, content = question))
    }
}

private fun buildSystemPrompt(contexts: List<AssistantDiagnosisContext>): String = buildString {
    appendLine("你是“澄影”的乳腺超声影像辅助解读助手。只依据下面提供的已保存诊断记录回答，使用简体中文，分点、简短。")
    appendLine("要求：")
    appendLine("1. 不得编造记录中未出现的病灶、分类、分割范围、数值、病史或治疗信息；信息不足时直接说明。")
    appendLine("2. 不给出药物剂量或具体治疗方案；确需进一步检查或专科就诊时，用一句话点明即可，不反复强调复核。")
    appendLine("3. 直接、具体地回答用户问题本身，依据记录给出明确解读；正文中不要添加免责声明、「仅供参考」「请复核」类套话，该类提示由界面统一展示。")
    appendLine("4. 使用纯文本分点，不要使用 Markdown 标题、星号或表格。")
    if (contexts.isEmpty()) {
        appendLine()
        appendLine("当前没有引用任何诊断记录。若问题涉及具体结果，请提示用户先在“引用记录”中选择。")
    } else {
        appendLine()
        appendLine("已引用记录（按时间倒序）：")
        contexts.take(MAX_CONTEXT_RECORDS).forEach { item ->
            appendLine("· 记录 ${item.diagnosisNumber}，recordId=${item.recordId}（${item.createdAt}，${item.sourceLabel}）")
            appendLine("  结论：${item.finding}")
        }
        if (contexts.size > MAX_CONTEXT_RECORDS) {
            appendLine("（其余 ${contexts.size - MAX_CONTEXT_RECORDS} 条记录未带入上下文）")
        }
    }
}

private fun buildReferenceSummary(selectedContexts: List<AssistantDiagnosisContext>): String =
    selectedContexts.joinToString("、") { "${it.diagnosisNumber}（${it.recordId}）" }
