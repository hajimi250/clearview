package com.chengying.clearview.core.network

import com.chengying.clearview.core.model.*

data class SegmentResponse(
    val requestId: String,
    val model: String,
    val modelVersion: String,
    val inputSize: Int,
    val maskUrl: String,
    val lesionPixels: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val imagePixels: Int,
    val lesionImageRatio: Float,
    val inferenceMs: Int,
    val disclaimer: String,
)

data class PredictCandidate(
    val label: String,
    val labelZh: String?,
    val diseaseName: String?,
    val diseaseNameZh: String?,
    val score: Float,
)

data class PredictResponse(
    val top1: PredictCandidate?,
    val predictions: List<PredictCandidate>,
)

data class AnalyzeResponse(
    val recordId: String,
    val createdAt: String,
    val updatedAt: String,
    val topK: Int,
    val status: DiagnosisStatus,
    val inferenceSource: DiagnosisInferenceSource,
    val recordUrl: String,
    val sourceImageUrl: String,
    val maskUrl: String,
    val examDescription: String?,
    val primaryLabel: String?,
    val primaryLabelZh: String?,
    val diseaseName: String?,
    val diseaseNameZh: String?,
    val sourceImagePath: String?,
    val maskImagePath: String?,
    val lesionImageRatio: Float,
    val severityLevel: SeverityLevel,
    val classifier: PredictResponse,
    val segmentor: SegmentResponse,
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

data class AnalyzeRecordsResponse(
    val records: List<AnalyzeResponse>,
    val count: Int,
    val offset: Int,
    val limit: Int,
    val total: Int,
    val hasMore: Boolean,
    val nextOffset: Int?,
)

interface DiagnosisGateway {
    suspend fun analyzeImage(
        imagePath: String,
        topK: Int = 3,
        clientRecordId: String,
        acquisition: AcquisitionInfo = AcquisitionInfo(),
    ): AnalyzeResponse

    suspend fun downloadMaskByRelativeUrl(
        recordId: String,
        relativeUrl: String,
    ): ByteArray

    suspend fun downloadSourceImageByRelativeUrl(
        recordId: String,
        relativeUrl: String,
    ): ByteArray

    suspend fun deleteRecord(
        recordId: String,
    )

    suspend fun listRecords(
        limit: Int = 20,
        offset: Int = 0,
    ): AnalyzeRecordsResponse
}
