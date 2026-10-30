package com.chengying.clearview.core.database

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.chengying.clearview.core.model.*
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DiagnosisHistoryStore {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    fun readAll(
        filesDir: File,
        limit: Int = Int.MAX_VALUE,
        offset: Int = 0,
    ): List<DiagnosisReport> {
        if (!DiagnosisStoragePaths.diagnosisRoot(filesDir).exists()) return emptyList()

        return DiagnosisStoragePaths.diagnosisRoot(filesDir)
            .walkTopDown()
            .filter { it.isFile && it.name == DiagnosisStoragePaths.REPORT_FILE_NAME }
            .mapNotNull { file ->
                runCatching { file.toDiagnosisReport(filesDir) }.getOrNull()
            }
            .sortedByDescending(::reportSortKey)
            .drop(offset.coerceAtLeast(0))
            .let { reports ->
                if (limit == Int.MAX_VALUE) reports else reports.take(limit.coerceAtLeast(0))
            }
            .toList()
    }

    fun write(
        filesDir: File,
        report: DiagnosisReport,
    ) {
        val existingDirectory = DiagnosisStoragePaths.resolveRecordDirectory(filesDir, report.recordId)
        val reportFile = existingDirectory?.let { File(it, DiagnosisStoragePaths.REPORT_FILE_NAME) }
            ?: DiagnosisStoragePaths.reportFile(filesDir, report.recordId)
        reportFile.parentFile?.mkdirs()
        atomicDiagnosisWrite(reportFile, gson.toJson(report.toJsonObject()).toByteArray(Charsets.UTF_8))
        DiagnosisStoragePaths.evictIfNeeded(filesDir)
    }

    fun remove(
        filesDir: File,
        recordId: String,
    ) {
        DiagnosisStoragePaths.resolveRecordDirectory(filesDir, recordId)?.deleteRecursively()
    }
}

private object DiagnosisHistoryJsonFields {
    const val RECORD_ID = "recordId"
    const val CREATED_AT = "createdAt"
    const val EXAM_DESCRIPTION = "examDescription"
    const val INFERENCE_SOURCE = "inferenceSource"
    const val SOURCE_IMAGE_PATH = "sourceImagePath"
    const val MASK_IMAGE_PATH = "maskImagePath"
    const val LESION_IMAGE_RATIO = "lesionImageRatio"
    const val SEVERITY_LEVEL = "severityLevel"
    const val DISCLAIMER = "disclaimer"
    const val SUGGESTIONS = "suggestions"
    const val CANDIDATES = "candidates"
    const val SUPPORTS_SEVERITY = "supportsSeverity"
    const val STATUS = "status"
    const val FAILURE_REASON = "failureReason"

    const val NAME = "name"
    const val CONFIDENCE = "confidence"
    const val RAW_LABEL = "rawLabel"
    const val DISEASE_NAME = "diseaseName"
    const val DISEASE_NAME_ZH = "diseaseNameZh"
}

private fun DiagnosisReport.toJsonObject(): JsonObject = JsonObject().apply {
    addProperty(DiagnosisHistoryJsonFields.RECORD_ID, recordId)
    addProperty(DiagnosisHistoryJsonFields.CREATED_AT, createdAt)
    addProperty(DiagnosisHistoryJsonFields.EXAM_DESCRIPTION, examDescription)
    addProperty(DiagnosisHistoryJsonFields.INFERENCE_SOURCE, inferenceSource.name)
    addNullableString(DiagnosisHistoryJsonFields.SOURCE_IMAGE_PATH, sourceImagePath)
    addNullableString(DiagnosisHistoryJsonFields.MASK_IMAGE_PATH, maskImagePath)
    addProperty(DiagnosisHistoryJsonFields.LESION_IMAGE_RATIO, lesionImageRatio)
    addProperty(DiagnosisHistoryJsonFields.SEVERITY_LEVEL, severityLevel.name)
    addProperty(DiagnosisHistoryJsonFields.DISCLAIMER, disclaimer)
    add(
        DiagnosisHistoryJsonFields.SUGGESTIONS,
        JsonArray().apply {
            suggestions.forEach(::add)
        },
    )
    add(
        DiagnosisHistoryJsonFields.CANDIDATES,
        JsonArray().apply {
            candidates.forEach { candidate ->
                add(
                    JsonObject().apply {
                        addProperty(DiagnosisHistoryJsonFields.NAME, candidate.name)
                        addProperty(DiagnosisHistoryJsonFields.CONFIDENCE, candidate.confidence)
                        addNullableString(DiagnosisHistoryJsonFields.RAW_LABEL, candidate.rawLabel)
                        addNullableString(DiagnosisHistoryJsonFields.DISEASE_NAME, candidate.diseaseName)
                        addNullableString(DiagnosisHistoryJsonFields.DISEASE_NAME_ZH, candidate.diseaseNameZh)
                    },
                )
            }
        },
    )
    addProperty(DiagnosisHistoryJsonFields.SUPPORTS_SEVERITY, supportsSeverity)
    addProperty(DiagnosisHistoryJsonFields.STATUS, status.name)
    addProperty("schemaVersion", schemaVersion)
    addProperty("reportRevision", reportRevision)
    add("acquisition", diagnosisContractGson.toJsonTree(acquisition))
    add("geometry", diagnosisContractGson.toJsonTree(geometry))
    add("assets", diagnosisContractGson.toJsonTree(assets))
    add("provenance", diagnosisContractGson.toJsonTree(provenance))
    add("fullScores", diagnosisContractGson.toJsonTree(fullScores))
    addProperty("attentionTarget", attentionTarget)
    addProperty("attentionState", attentionState)
    add("measurements", diagnosisContractGson.toJsonTree(measurements))
    add("evidenceAssessment", diagnosisContractGson.toJsonTree(evidenceAssessment))
    addProperty("sourceAssetVerified", sourceAssetVerified)
    addProperty("maskAssetVerified", maskAssetVerified)
    addNullableString(DiagnosisHistoryJsonFields.FAILURE_REASON, failureReason)
}

private fun JsonObject.addNullableString(
    fieldName: String,
    value: String?,
) {
    if (value == null) {
        add(fieldName, null)
    } else {
        addProperty(fieldName, value)
    }
}

private fun File.toDiagnosisReport(filesDir: File): DiagnosisReport {
    val root = JsonParser.parseString(readText(Charsets.UTF_8)).asJsonObject
    val report = DiagnosisReport(
        recordId = root.requiredString(DiagnosisHistoryJsonFields.RECORD_ID),
        createdAt = root.requiredString(DiagnosisHistoryJsonFields.CREATED_AT),
        examDescription = root.requiredString(DiagnosisHistoryJsonFields.EXAM_DESCRIPTION),
        inferenceSource = root.inferenceSource(),
        sourceImagePath = root.pathOrNull(DiagnosisHistoryJsonFields.SOURCE_IMAGE_PATH),
        maskImagePath = root.pathOrNull(DiagnosisHistoryJsonFields.MASK_IMAGE_PATH),
        lesionImageRatio = root.floatOrDefault(DiagnosisHistoryJsonFields.LESION_IMAGE_RATIO, 0f),
        severityLevel = root.enumOrDefault(DiagnosisHistoryJsonFields.SEVERITY_LEVEL, SeverityLevel.UNKNOWN),
        disclaimer = root.requiredString(DiagnosisHistoryJsonFields.DISCLAIMER),
        suggestions = root.arrayOrEmpty(DiagnosisHistoryJsonFields.SUGGESTIONS).toStringList(),
        candidates = root.arrayOrEmpty(DiagnosisHistoryJsonFields.CANDIDATES).toDiagnosisCandidates(),
        supportsSeverity = root.booleanOrDefault(DiagnosisHistoryJsonFields.SUPPORTS_SEVERITY, false),
        status = root.enumOrDefault(DiagnosisHistoryJsonFields.STATUS, DiagnosisStatus.FAILED),
        schemaVersion = root.optionalContract<Int>("schemaVersion"),
        reportRevision = root.contractString("reportRevision"),
        acquisition = root.optionalContract<AcquisitionInfo>("acquisition")?.sanitized(),
        geometry = root.optionalContract<ReportGeometry>("geometry")?.sanitized(),
        assets = root.optionalContract<ReportAssets>("assets")?.sanitized(),
        provenance = root.optionalContract<AnalysisProvenance>("provenance"),
        fullScores = root.optionalContract<Map<String, Float>>("fullScores").sanitizedScores(),
        attentionTarget = root.contractString("attentionTarget"),
        attentionState = root.contractString("attentionState"),
        measurements = root.optionalContract<DiagnosisMeasurements>("measurements"),
        evidenceAssessment = root.optionalContract<EvidenceAssessment>("evidenceAssessment")?.sanitized(),
        failureReason = root.stringOrNull(DiagnosisHistoryJsonFields.FAILURE_REASON),
    )
    // Persisted booleans are not proof. Re-hash the immutable files on every cache load.
    val source = report.assets?.source?.let { DiagnosisAssetStore.resolve(filesDir, report.recordId, "source", it) }
    val mask = report.assets?.attention?.let { DiagnosisAssetStore.resolve(filesDir, report.recordId, "attention", it) }
    return report.copy(
        sourceImagePath = source ?: report.sourceImagePath,
        maskImagePath = mask ?: report.maskImagePath,
        sourceAssetVerified = source != null,
        maskAssetVerified = mask != null,
    )
}

private fun JsonObject.inferenceSource(): DiagnosisInferenceSource {
    val raw = stringOrNull(DiagnosisHistoryJsonFields.INFERENCE_SOURCE)
    return raw?.let { value ->
        runCatching { DiagnosisInferenceSource.valueOf(value) }.getOrNull()
    } ?: if (enumOrDefault(DiagnosisHistoryJsonFields.STATUS, DiagnosisStatus.FAILED) == DiagnosisStatus.PENDING_UPLOAD) {
        DiagnosisInferenceSource.PENDING
    } else {
        DiagnosisInferenceSource.ONLINE
    }
}

private fun JsonArray.toStringList(): List<String> = buildList {
    for (element in this@toStringList) {
        element.asTrimmedStringOrNull()?.let(::add)
    }
}

private fun JsonArray.toDiagnosisCandidates(): List<DiagnosisCandidate> = buildList {
    forEachObject { item ->
        add(
            DiagnosisCandidate(
                name = item.requiredString(DiagnosisHistoryJsonFields.NAME),
                confidence = item.floatOrDefault(DiagnosisHistoryJsonFields.CONFIDENCE, 0f),
                rawLabel = item.stringOrNull(DiagnosisHistoryJsonFields.RAW_LABEL),
                diseaseName = item.stringOrNull(DiagnosisHistoryJsonFields.DISEASE_NAME),
                diseaseNameZh = item.stringOrNull(DiagnosisHistoryJsonFields.DISEASE_NAME_ZH),
            ),
        )
    }
}

private inline fun JsonArray.forEachObject(block: (JsonObject) -> Unit) {
    for (element in this) {
        element.takeIf { it.isJsonObject }?.asJsonObject?.let(block)
    }
}

private fun JsonObject.requiredString(fieldName: String): String =
    stringOrNull(fieldName).orEmpty()

private fun JsonObject.stringOrNull(fieldName: String): String? {
    val element = get(fieldName) ?: return null
    if (element.isJsonNull) return null
    return element.asString.trim()
        .takeIf { it.isNotEmpty() && it != "null" }
}

private fun JsonObject.pathOrNull(fieldName: String): String? =
    stringOrNull(fieldName)?.takeIf { File(it).exists() }

private fun JsonObject.floatOrDefault(
    fieldName: String,
    defaultValue: Float,
): Float = get(fieldName)
    ?.takeUnless(JsonElement::isJsonNull)
    ?.runCatching { asFloat }
    ?.getOrNull()
    ?: defaultValue

private fun JsonObject.booleanOrDefault(
    fieldName: String,
    defaultValue: Boolean,
): Boolean = get(fieldName)
    ?.takeUnless(JsonElement::isJsonNull)
    ?.runCatching { asBoolean }
    ?.getOrNull()
    ?: defaultValue

private inline fun <reified T : Enum<T>> JsonObject.enumOrDefault(
    fieldName: String,
    defaultValue: T,
): T = runCatching { stringOrNull(fieldName)?.let { enumValueOf<T>(it) } }
    .getOrNull()
    ?: defaultValue

private fun JsonObject.arrayOrEmpty(fieldName: String): JsonArray =
    get(fieldName)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?: JsonArray()

private fun JsonElement.asTrimmedStringOrNull(): String? =
    takeUnless { isJsonNull }
        ?.runCatching { asString.trim() }
        ?.getOrNull()
        ?.takeIf { it.isNotEmpty() && it != "null" }

private fun reportSortKey(report: DiagnosisReport): Long {
    val offsetTime = runCatching { OffsetDateTime.parse(report.createdAt) }.getOrNull()
    if (offsetTime != null) return offsetTime.toInstant().toEpochMilli()

    val localTime = runCatching {
        LocalDateTime.parse(report.createdAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull()
    if (localTime != null) {
        return localTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    return 0L
}

private val diagnosisContractGson: Gson = GsonBuilder()
    .serializeNulls()
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
    .registerTypeAdapterFactory(StoredDiagnosisContractAdapterFactory)
    .create()

private object StoredDiagnosisContractAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType.`package`?.name != AcquisitionInfo::class.java.`package`?.name) return null
        val delegate = gson.getDelegateAdapter(this, type)
        val tree = gson.getAdapter(JsonElement::class.java)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)

            override fun read(reader: JsonReader): T? {
                val json = tree.read(reader)
                if (json == null || !json.isJsonObject) return null
                val objectValue = json.asJsonObject
                if (type.rawType.declaredFields.any { field ->
                    field.type == String::class.java && objectValue.get(field.name)?.let {
                        !it.isJsonNull && (!it.isJsonPrimitive || !it.asJsonPrimitive.isString)
                    } == true
                }) return null
                return runCatching { delegate.fromJsonTree(json) }.getOrNull()
            }
        }
    }
}

private inline fun <reified T> JsonObject.optionalContract(name: String): T? = runCatching {
    get(name)?.takeUnless(JsonElement::isJsonNull)?.let {
        diagnosisContractGson.fromJson<T>(it, object : TypeToken<T>() {}.type)
    }
}.getOrNull()

private fun JsonObject.contractString(name: String): String? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
