package com.chengying.clearview.feature.knowledge

/** MODEL_LABEL 对应模型的三类分类输出；EXTENDED 为通用科普，不属于模型输出类别。 */
enum class KnowledgeArticleKind { MODEL_LABEL, EXTENDED }

data class KnowledgeArticle(
    val title: String,
    val summary: String,
    val category: String,
    val imagingFindings: String,
    val checklist: List<String>,
    val pitfalls: String,
    val reviewAdvice: String,
    val kind: KnowledgeArticleKind = KnowledgeArticleKind.MODEL_LABEL,
    val sourceNote: String? = null,
    // Teaching films come from open-access literature; credit text must travel with the pixels.
    val imageRes: Int? = null,
    val imageCaption: String? = null,
    val imageCredit: String? = null,
)
