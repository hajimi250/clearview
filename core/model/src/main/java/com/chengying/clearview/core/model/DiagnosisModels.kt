package com.chengying.clearview.core.model

enum class DiagnosisStatus {
    SUCCESS,
    PENDING_UPLOAD,
    FAILED,
}

enum class DiagnosisInferenceSource {
    ONLINE,
    OFFLINE,
    PENDING,
}

enum class SeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN,
}

data class DiagnosisCandidate(
    val name: String,
    val confidence: Float,
    val rawLabel: String? = null,
    val diseaseName: String? = null,
    val diseaseNameZh: String? = null,
)

data class DiagnosisReport(
    val recordId: String,
    val createdAt: String,
    val examDescription: String,
    val inferenceSource: DiagnosisInferenceSource,
    val sourceImagePath: String? = null,
    val maskImagePath: String? = null,
    val lesionImageRatio: Float,
    val severityLevel: SeverityLevel,
    val disclaimer: String,
    val suggestions: List<String>,
    val candidates: List<DiagnosisCandidate>,
    val supportsSeverity: Boolean = true,
    val status: DiagnosisStatus,
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
    val sourceAssetVerified: Boolean = false,
    val maskAssetVerified: Boolean = false,
    val failureReason: String? = null,
)

fun DiagnosisCandidate.displayDiseaseName(): String =
    diseaseNameZh?.takeIf { it.isNotBlank() } ?: name

fun DiagnosisReport.displayDiseaseName(): String =
    candidates.firstOrNull()?.displayDiseaseName()?.takeIf { it.isNotBlank() } ?: "未获得明确分类"

fun SeverityLevel.toDisplayLabel(): String = when (this) {
    SeverityLevel.LOW -> "低等级（模型参考）"
    SeverityLevel.MEDIUM -> "中等级（模型参考）"
    SeverityLevel.HIGH -> "高等级（模型参考）"
    SeverityLevel.UNKNOWN -> "分级数据无效 / 不可用"
}

fun DiagnosisStatus.toDisplayLabel(): String = when (this) {
    DiagnosisStatus.SUCCESS -> "已完成"
    DiagnosisStatus.PENDING_UPLOAD -> "待分析"
    DiagnosisStatus.FAILED -> "已失败"
}

fun DiagnosisInferenceSource.toDisplayLabel(): String = when (this) {
    DiagnosisInferenceSource.ONLINE -> "服务端分析"
    DiagnosisInferenceSource.OFFLINE -> "端侧推理"
    DiagnosisInferenceSource.PENDING -> "待分析"
}

object LocalDiagnosisFactory {
    fun savedPendingSample(
        recordId: String,
        createdAt: String,
        sourceImagePath: String?,
    ): DiagnosisReport = DiagnosisReport(
        recordId = recordId,
        createdAt = createdAt,
        examDescription = "",
        inferenceSource = DiagnosisInferenceSource.PENDING,
        sourceImagePath = sourceImagePath,
        maskImagePath = null,
        lesionImageRatio = 0f,
        severityLevel = SeverityLevel.UNKNOWN,
        disclaimer = "影像已保存到本机，等待分析服务完成分析。",
        suggestions = listOf(
            "连接分析服务后可继续提交，获取完整的分类与病灶分割结果。",
            "在此之前建议由医师直接复核原始影像。",
            "如需复查，请尽量保持相近的拍摄距离与光照条件。",
        ),
        candidates = listOf(
            DiagnosisCandidate(
                name = "待分析",
                confidence = 0f,
            ),
        ),
        supportsSeverity = false,
        status = DiagnosisStatus.PENDING_UPLOAD,
    )

    fun backendUnavailableSample(
        recordId: String,
        createdAt: String,
        sourceImagePath: String?,
        reason: String?,
        inferenceSource: DiagnosisInferenceSource = DiagnosisInferenceSource.ONLINE,
    ): DiagnosisReport = DiagnosisReport(
        recordId = recordId,
        createdAt = createdAt,
        examDescription = "",
        inferenceSource = inferenceSource,
        sourceImagePath = sourceImagePath,
        maskImagePath = null,
        lesionImageRatio = 0f,
        severityLevel = SeverityLevel.UNKNOWN,
        disclaimer = "本次未完成推理，原始影像已保留在本机，可稍后重试。",
        suggestions = listOf(
            "确认分析服务已启动，且服务地址配置正确。",
            "若暂时无法联通，可先保留为本地待分析记录，稍后重试。",
            reason ?: "影像分析服务未返回有效结果，请稍后重试。",
        ),
        candidates = listOf(
            DiagnosisCandidate(
                name = "影像分析服务暂不可用",
                confidence = 0f,
                rawLabel = reason,
            ),
        ),
        supportsSeverity = false,
        status = DiagnosisStatus.FAILED,
        failureReason = reason,
    )
}

// Nullable wire fields are intentional: neither omitted fields nor explicit JSON null
// may grant a measurement or evidence permission.
data class AcquisitionInfo(
    val projection: String? = "UNKNOWN",
    val acquiredAt: String? = null,
    val completenessConfirmed: Boolean? = null,
    val declaredBy: String? = "USER",
)

data class ImageAsset(val sha256: String? = null, val url: String? = null)

data class ReportAssets(
    val source: ImageAsset? = null,
    val attention: ImageAsset? = null,
)

data class ReportGeometry(
    val width: Int? = null,
    val height: Int? = null,
    val trimBounds: List<Int>? = null,
    val anatomyBounds: List<Int>? = null,
    val coordinateSystem: String? = null,
)

data class AnalysisProvenance(
    val classifierHash: String? = null,
    val segmentorHash: String? = null,
    val preprocessVersion: String? = null,
    val attentionVersion: String? = null,
    val measurementVersion: String? = null,
    val severityVersion: String? = null,
    val libraryVersion: String? = null,
)

data class MeasurementQuality(
    val lungValid: Boolean? = null,
    val heartValid: Boolean? = null,
    val anatomyClipped: Boolean? = null,
    val maskValid: Boolean? = null,
    val maskTouchesEdge: Boolean? = null,
)

data class DiagnosisMeasurements(
    val attentionAreaPixels: Int? = null,
    val quality: MeasurementQuality? = null,
    val lesionAreaPixels: Int? = null,
    val lesionImageRatio: Float? = null,
    val lesionBoundingBox: List<Int>? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val imagePixels: Int? = null,
    val segmentationThreshold: Float? = null,
)

data class EvidenceAssessment(
    val version: String? = null,
    val status: String? = null,
    val reasonCodes: List<String>? = null,
    val auditEventId: String? = null,
)

fun Float?.isValidRatio(): Boolean = this != null && isFinite() && this in 0f..1f

fun isValidDiagnosisId(value: String?): Boolean =
    value != null && Regex("[A-Za-z0-9_-]{1,128}").matches(value)

fun ImageAsset.isValid(): Boolean = sha256?.matches(Regex("[a-fA-F0-9]{64}")) == true

private fun List<String>?.safeCodes(): List<String> =
    orEmpty().filterIsInstance<String>().filter { it.isNotBlank() }.distinct()

fun Map<String, Float>?.sanitizedScores(): Map<String, Float>? = this?.filter { (label, value) ->
    !label.isNullOrBlank() && value.isValidRatio()
}

fun AcquisitionInfo.sanitized(): AcquisitionInfo = copy(
    projection = projection?.takeIf { it == "UNKNOWN" },
    acquiredAt = acquiredAt?.takeIf { runCatching { java.time.OffsetDateTime.parse(it) }.isSuccess },
    completenessConfirmed = completenessConfirmed.takeIf { declaredBy == "USER" },
    declaredBy = declaredBy?.takeIf { it == "USER" },
)

fun AcquisitionInfo.validatedForSubmission(): AcquisitionInfo {
    require(projection == "UNKNOWN") { "Invalid projection" }
    require(declaredBy == "USER") { "Acquisition must be declared by the user" }
    require(acquiredAt == null || runCatching { java.time.OffsetDateTime.parse(acquiredAt) }.isSuccess) {
        "Acquisition time must include a UTC offset"
    }
    return this
}

fun ReportAssets.sanitized(): ReportAssets = copy(
    source = source?.takeIf(ImageAsset::isValid),
    attention = attention?.takeIf(ImageAsset::isValid),
)

fun ReportGeometry.sanitized(): ReportGeometry? {
    val w = width ?: return null
    val h = height ?: return null
    if (w <= 0 || h <= 0 || coordinateSystem != "SOURCE_PIXELS") return null
    fun valid(bounds: List<Int>?): Boolean {
        if (bounds == null || bounds.size != 4 || bounds.filterIsInstance<Int>().size != 4) return false
        return bounds[0] >= 0 && bounds[1] >= 0 && bounds[2] > bounds[0] && bounds[3] > bounds[1] &&
            bounds[2] <= w && bounds[3] <= h
    }
    return takeIf { valid(trimBounds) && valid(anatomyBounds) }
}

fun EvidenceAssessment.sanitized(): EvidenceAssessment = copy(
    reasonCodes = reasonCodes.safeCodes(),
)
