package com.chengying.clearview.core.network

import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.chengying.clearview.core.model.*

data class PredictCandidateDto(
    @SerializedName("label")
    val label: String? = null,
    @SerializedName("label_zh")
    val labelZh: String? = null,
    @SerializedName("disease_name")
    val diseaseName: String? = null,
    @SerializedName("disease_name_zh")
    val diseaseNameZh: String? = null,
    @SerializedName("score")
    val score: Float,
)

data class PredictResponseDto(
    @SerializedName("top1")
    val top1: PredictCandidateDto? = null,
    @SerializedName("predictions")
    val predictions: List<PredictCandidateDto> = emptyList(),
    @SerializedName("diseaseTop1")
    val diseaseTop1: PredictCandidateDto? = null,
    @SerializedName("diseasePredictions")
    val diseasePredictions: List<PredictCandidateDto> = emptyList(),
)

data class AnalyzeResponseDto(
    @SerializedName("recordId")
    val recordId: String,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("updatedAt")
    val updatedAt: String,
    @SerializedName("topK")
    val topK: Int,
    @SerializedName("status")
    val status: String,
    @SerializedName("recordUrl")
    val recordUrl: String,
    @SerializedName("sourceImageUrl")
    val sourceImageUrl: String,
    @SerializedName("maskUrl")
    val maskUrl: String,
    @SerializedName("cropType")
    val examDescription: String? = null,
    @SerializedName("primaryLabel")
    val primaryLabel: String? = null,
    @SerializedName("primaryLabelZh")
    val primaryLabelZh: String? = null,
    @SerializedName("diseaseName")
    val diseaseName: String? = null,
    @SerializedName("diseaseNameZh")
    val diseaseNameZh: String? = null,
    @SerializedName("sourceImagePath")
    val sourceImagePath: String? = null,
    @SerializedName("maskImagePath")
    val maskImagePath: String? = null,
    @SerializedName("lesionImageRatio")
    val lesionImageRatio: Float,
    @SerializedName("severityLevel")
    val severityLevel: String?,
    @SerializedName("classifier")
    val classifier: PredictResponseDto,
    @SerializedName("segmentor")
    val segmentor: SegmentResponseDto,
    val schemaVersion: Int? = null,
    val reportRevision: String? = null,
    val acquisition: AcquisitionInfo? = null,
    val geometry: ReportGeometry? = null,
    val assets: ReportAssets? = null,
    val provenance: AnalysisProvenance? = null,
    val fullScores: Map<String, Float>? = null,
    val attentionTarget: String? = null,
    val attentionState: String? = null,
    val measurements: DiagnosisMeasurements? = null,
    val evidenceAssessment: EvidenceAssessment? = null,
)

data class AnalyzeRecordsResponseDto(
    @SerializedName("records")
    val records: List<AnalyzeResponseDto>,
    @SerializedName("count")
    val count: Int,
    @SerializedName("offset")
    val offset: Int,
    @SerializedName("limit")
    val limit: Int,
    @SerializedName("total")
    val total: Int,
    @SerializedName("hasMore")
    val hasMore: Boolean,
    @SerializedName("nextOffset")
    val nextOffset: Int? = null,
)

fun PredictResponseDto.toDomain(): PredictResponse {
    val primaryPredictions = diseasePredictions.takeIf(List<PredictCandidateDto>::isNotEmpty)
        ?: predictions
    // 服务端返回 Top-K softmax 候选；空列表按未返回候选处理，top1 允许缺失。
    val primaryTop1 = diseaseTop1 ?: top1 ?: primaryPredictions.firstOrNull()

    return PredictResponse(
        top1 = primaryTop1?.toDomain(),
        predictions = primaryPredictions.map(PredictCandidateDto::toDomain),
    )
}

fun AnalyzeResponseDto.toDomain(): AnalyzeResponse = AnalyzeResponse(
    recordId = recordId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    topK = topK,
    status = status.toDiagnosisStatus(),
    inferenceSource = DiagnosisInferenceSource.ONLINE,
    recordUrl = recordUrl,
    sourceImageUrl = sourceImageUrl,
    maskUrl = maskUrl,
    examDescription = examDescription,
    primaryLabel = primaryLabel,
    primaryLabelZh = primaryLabelZh,
    diseaseName = diseaseName,
    diseaseNameZh = diseaseNameZh,
    sourceImagePath = sourceImagePath,
    maskImagePath = maskImagePath,
    lesionImageRatio = lesionImageRatio,
    severityLevel = severityLevel.toSeverityLevel(),
    classifier = classifier.toDomain(),
    segmentor = segmentor.toDomain(),
    schemaVersion = schemaVersion,
    reportRevision = reportRevision,
    acquisition = acquisition?.sanitized(),
    geometry = geometry?.sanitized(),
    assets = assets?.sanitized(),
    provenance = provenance,
    fullScores = fullScores.sanitizedScores(),
    attentionTarget = attentionTarget,
    attentionState = attentionState,
    measurements = measurements,
    evidenceAssessment = evidenceAssessment?.sanitized(),
)

fun AnalyzeRecordsResponseDto.toDomain(): AnalyzeRecordsResponse = AnalyzeRecordsResponse(
    records = records.map(AnalyzeResponseDto::toDomain),
    count = count,
    offset = offset,
    limit = limit,
    total = total,
    hasMore = hasMore,
    nextOffset = nextOffset,
)

private fun PredictCandidateDto.toDomain(): PredictCandidate = PredictCandidate(
    label = label ?: diseaseName ?: diseaseNameZh ?: labelZh ?: "",
    labelZh = labelZh,
    diseaseName = diseaseName,
    diseaseNameZh = diseaseNameZh,
    score = score,
)

fun AnalyzeResponse.toDiagnosisReport(
    createdAt: String = this.createdAt,
    sourceImagePath: String? = null,
    maskImagePath: String? = null,
    sourceAssetVerified: Boolean = false,
    maskAssetVerified: Boolean = false,
): DiagnosisReport {
    val supportsSeverity = segmentor.supportsSeverityMetrics()
    return classifier.toDiagnosisReport(
        recordId = recordId,
        createdAt = createdAt,
        inferenceSource = inferenceSource,
        sourceImagePath = sourceImagePath,
        maskImagePath = maskImagePath,
        examDescriptionOverride = examDescription,
        lesionImageRatio = lesionImageRatio,
        severityLevel = severityLevel,
        disclaimer = segmentor.disclaimer,
        supportsSeverity = supportsSeverity,
        status = status,
    ).copy(
        schemaVersion = schemaVersion,
        reportRevision = reportRevision,
        acquisition = acquisition?.sanitized(),
        geometry = geometry?.sanitized(),
        assets = assets?.sanitized(),
        provenance = provenance,
        fullScores = fullScores.sanitizedScores(),
        attentionTarget = attentionTarget,
        attentionState = attentionState,
        measurements = measurements,
        evidenceAssessment = evidenceAssessment?.sanitized(),
        sourceAssetVerified = sourceAssetVerified,
        maskAssetVerified = maskAssetVerified,
    )
}

fun PredictResponse.toDiagnosisReport(
    recordId: String,
    createdAt: String,
    inferenceSource: DiagnosisInferenceSource,
    sourceImagePath: String? = null,
    maskImagePath: String? = null,
    examDescriptionOverride: String? = null,
    lesionImageRatio: Float = 0f,
    severityLevel: SeverityLevel = SeverityLevel.UNKNOWN,
    disclaimer: String = "本结果由分析服务生成，仅供科研与教学演示，不构成临床诊断依据。",
    supportsSeverity: Boolean = false,
    status: DiagnosisStatus = DiagnosisStatus.SUCCESS,
): DiagnosisReport {
    val topLabel = top1?.displayLabel()?.takeIf { it.isNotBlank() }

    return DiagnosisReport(
        recordId = recordId,
        createdAt = createdAt,
        examDescription = examDescriptionOverride.orEmpty(),
        inferenceSource = inferenceSource,
        sourceImagePath = sourceImagePath,
        maskImagePath = maskImagePath,
        lesionImageRatio = lesionImageRatio,
        severityLevel = severityLevel,
        disclaimer = disclaimer,
        suggestions = listOf(
            if (topLabel == null) {
                "本次未返回有效分类候选；不能据此排除病变，请由医师复核原始影像并结合临床判断。"
            } else {
                "请由医师复核“$topLabel”对应的超声表现，并结合病史、超声检查与病理资料综合判断。"
            },
            "请确认使用原始乳腺超声影像，避免滤镜、截图拼接或过度压缩。",
            if (supportsSeverity) "病灶掩码为模型逐像素分割结果，不等同于病理诊断；仅供科研与教学参考。" else "当前仅展示分类结果，未提供病灶分割；不能据此排除病变。",
        ),
        candidates = predictions.map { candidate ->
            DiagnosisCandidate(
                name = candidate.displayLabel(),
                confidence = candidate.score,
                rawLabel = candidate.label,
                diseaseName = candidate.diseaseName,
                diseaseNameZh = candidate.diseaseNameZh,
            )
        },
        supportsSeverity = supportsSeverity,
        status = status,
    )
}

private fun PredictCandidate.displayLabel(): String =
    diseaseNameZh?.takeIf { it.isNotBlank() }
        ?: diseaseName?.takeIf { it.isNotBlank() }
        ?: labelZh?.takeIf { it.isNotBlank() }
        ?: label

internal fun String?.toSeverityLevel(): SeverityLevel = when (this?.trim()?.uppercase()) {
    "HIGH" -> SeverityLevel.HIGH
    "MEDIUM" -> SeverityLevel.MEDIUM
    "LOW" -> SeverityLevel.LOW
    else -> SeverityLevel.UNKNOWN
}

private fun String?.toDiagnosisStatus(): DiagnosisStatus = when (this?.lowercase()) {
    "success" -> DiagnosisStatus.SUCCESS
    "pending_upload", "pending" -> DiagnosisStatus.PENDING_UPLOAD
    else -> DiagnosisStatus.FAILED
}

private fun SegmentResponse.supportsSeverityMetrics(): Boolean =
    model.lowercase() != "offline-classifier" && maskUrl.isNotBlank()

/** Strict nullable scalars affect only additive boxed fields, not legacy primitives. */
fun createDiagnosisGson(): Gson = GsonBuilder()
    .registerTypeAdapter(Boolean::class.javaObjectType, JsonDeserializer<Boolean?> { json, _, _ ->
        json.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
    })
    .registerTypeAdapter(Float::class.javaObjectType, JsonDeserializer<Float?> { json, _, _ ->
        json.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.runCatching { asFloat.takeIf(Float::isFinite) }?.getOrNull()
    })
    .registerTypeAdapter(Int::class.javaObjectType, JsonDeserializer<Int?> { json, _, _ ->
        json.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.runCatching { asBigDecimal.intValueExact() }?.getOrNull()
    })
    .registerTypeAdapterFactory(OptionalDiagnosisContractAdapterFactory)
    .create()

private object OptionalDiagnosisContractAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val embedded = type.rawType.`package`?.name == AcquisitionInfo::class.java.`package`?.name
        val scores = type.type == object : TypeToken<Map<String, Float>>() {}.type
        if (!embedded && !scores) return null
        val delegate = gson.getDelegateAdapter(this, type)
        val tree = gson.getAdapter(JsonElement::class.java)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)

            override fun read(reader: JsonReader): T? {
                val json = tree.read(reader)
                if (json == null || !json.isJsonObject) return null
                // A malformed new subtree must not prevent a legacy report from opening.
                return runCatching { delegate.fromJsonTree(json) }.getOrNull()
            }
        }
    }
}
