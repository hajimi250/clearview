package com.chengying.clearview.feature.knowledge

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chengying.clearview.core.designsystem.component.ActionButton
import com.chengying.clearview.core.designsystem.component.ButtonKind
import com.chengying.clearview.core.designsystem.component.ClearViewTextField
import com.chengying.clearview.core.designsystem.component.EmptyState
import com.chengying.clearview.core.designsystem.component.MetaRow
import com.chengying.clearview.core.designsystem.component.Panel
import com.chengying.clearview.core.designsystem.component.SectionHeader
import com.chengying.clearview.core.designsystem.component.StatusTag
import com.chengying.clearview.core.designsystem.component.ToolButton
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KnowledgeScreen(onOpenDetail: (KnowledgeArticle) -> Unit = {}) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var recentSearches by remember { mutableStateOf(KnowledgeRecentSearchStore.read(context)) }
    var searchFocused by remember { mutableStateOf(false) }
    var selectedArticle by remember { mutableStateOf<KnowledgeArticle?>(null) }
    val activeCategory = knowledgeCategories[selectedCategory]
    val normalizedQuery = query.trim()
    val filteredItems = knowledgeArticles
        .filter { activeCategory == "全部" || it.category == activeCategory }
        .filter { article -> normalizedQuery.isEmpty() || article.matchesQuery(normalizedQuery) }
    val modelLabelItems = filteredItems.filter { it.kind == KnowledgeArticleKind.MODEL_LABEL }
    val extendedItems = filteredItems.filter { it.kind == KnowledgeArticleKind.EXTENDED }
    // 仅默认视图分组展示"模型标签 / 延伸阅读"；筛选或搜索时保持平铺列表。
    val groupedLayout = activeCategory == "全部" && normalizedQuery.isEmpty() &&
        modelLabelItems.isNotEmpty() && extendedItems.isNotEmpty()
    val showRecentSearches = searchFocused && normalizedQuery.isEmpty() && recentSearches.isNotEmpty()
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors

    fun commitRecentSearch(term: String) {
        val normalizedTerm = term.trim()
        if (normalizedTerm.isEmpty()) return
        recentSearches = (listOf(normalizedTerm) +
            recentSearches.filterNot { it.equals(normalizedTerm, ignoreCase = true) }
                .take(KNOWLEDGE_RECENT_SEARCH_LIMIT - 1))
            .also { KnowledgeRecentSearchStore.write(context, it) }
    }

    fun deleteRecentSearch(term: String) {
        recentSearches = recentSearches.filterNot { it.equals(term, ignoreCase = true) }
            .also { KnowledgeRecentSearchStore.write(context, it) }
    }

    fun openArticle(article: KnowledgeArticle) {
        commitRecentSearch(normalizedQuery)
        focusManager.clearFocus()
        selectedArticle = article
        onOpenDetail(article)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            ClearViewTextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索资料",
                modifier = Modifier.fillMaxWidth().onFocusChanged { searchFocused = it.isFocused },
                placeholder = KNOWLEDGE_SEARCH_PLACEHOLDER,
                trailingIcon = {
                    if (query.isNotBlank()) ToolButton(Icons.Outlined.Close, "清除搜索", { query = "" })
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    commitRecentSearch(normalizedQuery)
                    focusManager.clearFocus()
                }),
            )
        }
        if (showRecentSearches) {
            item {
                Panel(contentPadding = spacing.lg) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(KNOWLEDGE_RECENT_SEARCH_TITLE, modifier = Modifier.weight(1f))
                        ActionButton(
                            KNOWLEDGE_RECENT_SEARCH_CLEAR,
                            {
                                recentSearches = emptyList()
                                KnowledgeRecentSearchStore.write(context, emptyList())
                            },
                            kind = ButtonKind.TEXT,
                        )
                    }
                    Column {
                        recentSearches.forEachIndexed { index, term ->
                            if (index > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = colors.line)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).clickable(
                                        onClickLabel = "搜索 $term",
                                        onClick = {
                                            query = term
                                            commitRecentSearch(term)
                                            focusManager.clearFocus()
                                        },
                                    ),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    KnowledgeText(term)
                                }
                                ToolButton(Icons.Outlined.Close, "删除搜索记录 $term", { deleteRecentSearch(term) })
                            }
                        }
                    }
                }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                knowledgeCategories.forEachIndexed { index, label ->
                    ActionButton(
                        text = label,
                        onClick = { selectedCategory = index },
                        modifier = Modifier.semantics { selected = selectedCategory == index },
                        kind = if (selectedCategory == index) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                    )
                }
            }
        }
        item { MetaRow("匹配资料", filteredItems.size.toString()) }
        if (filteredItems.isEmpty()) {
            item { EmptyState(KNOWLEDGE_EMPTY_TITLE) }
        } else if (groupedLayout) {
            item { SectionHeader("模型标签") }
            items(modelLabelItems) { article ->
                KnowledgeArticleRow(article) { openArticle(article) }
            }
            item { SectionHeader("延伸阅读") }
            items(extendedItems) { article ->
                KnowledgeArticleRow(article) { openArticle(article) }
            }
        } else {
            items(filteredItems) { article ->
                KnowledgeArticleRow(article) { openArticle(article) }
            }
        }
        item {
            Text(
                text = KNOWLEDGE_USAGE_BOUNDARY,
                style = ClearViewTokens.typography.caption,
                color = colors.text3,
            )
        }
    }
    selectedArticle?.let { article ->
        KnowledgeDetailDialog(article = article, onDismiss = { selectedArticle = null })
    }
}

@Composable
private fun KnowledgeArticleRow(article: KnowledgeArticle, onClick: () -> Unit) {
    val spacing = ClearViewTokens.spacing
    Panel(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "查看资料详情", onClick = onClick),
        contentPadding = spacing.lg,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            article.imageRes?.let { imageRes ->
                KnowledgeFilmImage(
                    article = article,
                    imageRes = imageRes,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 84.dp, height = 100.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KnowledgeText(
                        article.title,
                        style = ClearViewTokens.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ClearViewTokens.colors.text1,
                    )
                    if (article.kind == KnowledgeArticleKind.EXTENDED) {
                        StatusTag("延伸阅读")
                    } else {
                        StatusTag(article.category)
                    }
                }
                KnowledgeText(article.summary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun KnowledgeFilmImage(
    article: KnowledgeArticle,
    imageRes: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val colors = ClearViewTokens.colors
    val shape = ClearViewTokens.shapes.small
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.imageCanvas),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = "「${article.title}」教学片例：${article.imageCaption.orEmpty()}".trim(),
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun knowledgeFilmAspectRatio(imageRes: Int): Float {
    val size = painterResource(imageRes).intrinsicSize
    return if (size.width > 0f && size.height > 0f) size.width / size.height else 1f
}

@Composable
private fun KnowledgeDetailDialog(article: KnowledgeArticle, onDismiss: () -> Unit) {
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
                    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        SelectionContainer { KnowledgeDetailContent(article) }
                    }
                    ActionButton("关闭", onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KnowledgeDetailContent(article: KnowledgeArticle) {
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    val extended = article.kind == KnowledgeArticleKind.EXTENDED
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        KnowledgeText(article.title, style = ClearViewTokens.typography.title, color = colors.text1)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatusTag(article.category)
            if (extended) StatusTag("延伸阅读 · 非模型类别")
        }
        article.imageRes?.let { imageRes ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                KnowledgeFilmImage(
                    article = article,
                    imageRes = imageRes,
                    contentScale = ContentScale.Fit,
                    // Hugging the film's own ratio avoids letterbox bars, which would crop teaching detail if filled instead.
                    modifier = Modifier.fillMaxWidth().aspectRatio(knowledgeFilmAspectRatio(imageRes)),
                )
                article.imageCaption?.takeIf { it.isNotBlank() }?.let {
                    KnowledgeText(it, style = ClearViewTokens.typography.bodySmall)
                }
                Text(
                    text = "示例影像来自公开数据集，非本应用或本院患者数据；图为原始影像，未叠加模型分割，不构成病理诊断。",
                    style = ClearViewTokens.typography.caption,
                    color = colors.text3,
                )
                article.imageCredit?.takeIf { it.isNotBlank() }?.let {
                    Text(text = "图片来源与许可：$it", style = ClearViewTokens.typography.caption, color = colors.text3)
                }
            }
        }
        DetailBlock("概述", article.summary)
        DetailBlock(if (extended) "关键要点" else "影像表现", article.imagingFindings)
        DetailBlock(
            if (extended) "操作与阅读步骤" else "读片要点",
            article.checklist.joinToString("\n") { "• $it" },
        )
        if (article.pitfalls.isNotBlank()) DetailBlock(if (extended) "常见误区" else "鉴别提醒", article.pitfalls)
        DetailBlock(if (extended) "下一步建议" else "复核建议", article.reviewAdvice)
        article.sourceNote?.takeIf { it.isNotBlank() }?.let { DetailBlock("参考来源", it) }
    }
}

@Composable
private fun DetailBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ClearViewTokens.spacing.xs)) {
        Text(title, style = ClearViewTokens.typography.heading, color = ClearViewTokens.colors.text1)
        KnowledgeText(body)
    }
}

@Composable
private fun KnowledgeText(
    text: String,
    style: TextStyle = ClearViewTokens.typography.body,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
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
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun KnowledgeArticle.matchesQuery(query: String): Boolean {
    val normalized = query.lowercase()
    return listOf(title, summary, category, imagingFindings, reviewAdvice)
        .any { field -> field.lowercase().contains(normalized) } ||
        checklist.any { it.lowercase().contains(normalized) }
}

private const val KNOWLEDGE_SEARCH_PLACEHOLDER = "搜索影像所见或读片要点"
private const val KNOWLEDGE_EMPTY_TITLE = "无匹配结果，更换关键词继续查找"
private const val KNOWLEDGE_RECENT_SEARCH_TITLE = "最近搜索"
private const val KNOWLEDGE_RECENT_SEARCH_CLEAR = "清空"
private const val KNOWLEDGE_RECENT_SEARCH_LIMIT = 10
private const val KNOWLEDGE_USAGE_BOUNDARY = "资料内容仅供参考\n乳腺超声教学资料与模型结果阅读说明，不构成临床诊断依据，不替代医师判断"
