package com.chengying.clearview.core.database

import com.chengying.clearview.core.model.DiagnosisCandidate
import com.chengying.clearview.core.model.DiagnosisInferenceSource
import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import com.chengying.clearview.core.model.LocalDiagnosisFactory
import com.chengying.clearview.core.model.SeverityLevel
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisHistoryStoreTest {

    @Test
    fun writeAndReadAll_preservesOfflineAndOnlineReports() {
        val filesDir = Files.createTempDirectory("diagnosis-history-store").toFile()
        val offlineRecordDir = DiagnosisStoragePaths.recordDirectory(filesDir, "offline-1")
        offlineRecordDir.mkdirs()
        File(offlineRecordDir, DiagnosisStoragePaths.SOURCE_IMAGE_FILE_NAME).writeBytes(byteArrayOf(1, 2, 3))

        DiagnosisHistoryStore.write(filesDir, sampleReport(
            recordId = "offline-1",
            createdAt = "2026-04-27 21:00",
            inferenceSource = DiagnosisInferenceSource.OFFLINE,
            sourceImagePath = File(offlineRecordDir, DiagnosisStoragePaths.SOURCE_IMAGE_FILE_NAME).absolutePath,
        ))
        DiagnosisHistoryStore.write(filesDir, sampleReport(
            recordId = "online-1",
            createdAt = "2026-04-27T21:10:00+08:00",
            inferenceSource = DiagnosisInferenceSource.ONLINE,
        ))

        val reports = DiagnosisHistoryStore.readAll(filesDir)

        assertEquals(listOf("online-1", "offline-1"), reports.map(DiagnosisReport::recordId))
        assertEquals(DiagnosisInferenceSource.ONLINE, reports.first().inferenceSource)
        assertEquals(DiagnosisInferenceSource.OFFLINE, reports.last().inferenceSource)
        assertEquals("malignant", reports.first().candidates.first().rawLabel)
        assertTrue(reports.last().sourceImagePath!!.endsWith(DiagnosisStoragePaths.SOURCE_IMAGE_FILE_NAME))
    }

    @Test
    fun writeAndReadAll_preservesPendingImageWithoutInference() {
        val filesDir = Files.createTempDirectory("diagnosis-history-pending").toFile()
        try {
            val sourceImage = DiagnosisStoragePaths.sourceImage(filesDir, "pending-1")
            sourceImage.parentFile!!.mkdirs()
            sourceImage.writeBytes(byteArrayOf(1, 2, 3))
            val report = LocalDiagnosisFactory.savedPendingSample(
                recordId = "pending-1",
                createdAt = "2026-10-23 10:00:00",
                sourceImagePath = sourceImage.absolutePath,
            )

            DiagnosisHistoryStore.write(filesDir, report)
            val restored = DiagnosisHistoryStore.readAll(filesDir).single()

            assertEquals(report, restored)
            assertEquals(DiagnosisStatus.PENDING_UPLOAD, restored.status)
            assertEquals(DiagnosisInferenceSource.PENDING, restored.inferenceSource)
            assertEquals(0f, restored.candidates.single().confidence, 0f)
            assertFalse(restored.supportsSeverity)
            assertTrue(File(restored.sourceImagePath!!).exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun remove_deletesWholeRecordDirectory() {
        val filesDir = Files.createTempDirectory("diagnosis-history-remove").toFile()
        val recordId = "pending-1"
        DiagnosisHistoryStore.write(filesDir, sampleReport(
            recordId = recordId,
            createdAt = "2026-04-27 22:00",
            inferenceSource = DiagnosisInferenceSource.PENDING,
            status = DiagnosisStatus.PENDING_UPLOAD,
        ))

        DiagnosisHistoryStore.remove(filesDir, recordId)

        assertFalse(DiagnosisStoragePaths.resolveRecordDirectory(filesDir, recordId)?.exists() == true)
        assertTrue(DiagnosisHistoryStore.readAll(filesDir).isEmpty())
    }

    @Test
    fun clearAll_removesEntireDiagnosisCacheRoot() {
        val cacheDir = Files.createTempDirectory("diagnosis-history-cache-clear").toFile()
        DiagnosisHistoryStore.write(cacheDir, sampleReport(
            recordId = "cache-1",
            createdAt = "2026-05-01 10:00",
            inferenceSource = DiagnosisInferenceSource.ONLINE,
        ))

        DiagnosisStoragePaths.clearAll(cacheDir)

        assertFalse(DiagnosisStoragePaths.diagnosisRoot(cacheDir).exists())
        assertTrue(DiagnosisHistoryStore.readAll(cacheDir).isEmpty())
    }

    @Test
    fun readAll_keepsImageAndOriginalFileWhenSeverityIsUnknownOrMalformed() {
        val filesDir = Files.createTempDirectory("diagnosis-history-invalid-grade").toFile()
        try {
            val source = DiagnosisStoragePaths.sourceImage(filesDir, "invalid-grade")
            source.parentFile!!.mkdirs()
            source.writeBytes(byteArrayOf(1, 2, 3))
            val report = sampleReport("invalid-grade", "2026-10-23 10:00", DiagnosisInferenceSource.ONLINE, source.absolutePath)
            for (raw in listOf("\"FUTURE_LEVEL\"", "null", "{}", "[]", "42")) {
                DiagnosisHistoryStore.write(filesDir, report)
                val file = DiagnosisStoragePaths.reportFile(filesDir, report.recordId)
                val json = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
                json.add("severityLevel", com.google.gson.JsonParser.parseString(raw))
                file.writeText(json.toString())
                val original = file.readBytes()
                val restored = DiagnosisHistoryStore.readAll(filesDir).single()
                assertEquals(SeverityLevel.UNKNOWN, restored.severityLevel)
                assertEquals(source.absolutePath, restored.sourceImagePath)
                assertTrue(original.contentEquals(file.readBytes()))
                assertTrue(source.exists())
            }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun writeAndReadAll_preservesEverySeverityLevel() {
        val filesDir = Files.createTempDirectory("diagnosis-history-grades").toFile()
        try {
            SeverityLevel.entries.forEach { level ->
                val report = sampleReport(level.name, "2026-10-23 10:00", DiagnosisInferenceSource.ONLINE).copy(severityLevel = level)
                DiagnosisHistoryStore.write(filesDir, report)
            }
            assertEquals(SeverityLevel.entries.toSet(), DiagnosisHistoryStore.readAll(filesDir).map { it.severityLevel }.toSet())
            assertEquals(SeverityLevel.UNKNOWN, LocalDiagnosisFactory.savedPendingSample("pending", "", null).severityLevel)
            assertEquals(SeverityLevel.UNKNOWN, LocalDiagnosisFactory.backendUnavailableSample("failed", "", null, null).severityLevel)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private fun sampleReport(
        recordId: String,
        createdAt: String,
        inferenceSource: DiagnosisInferenceSource,
        sourceImagePath: String? = null,
        status: DiagnosisStatus = DiagnosisStatus.SUCCESS,
    ): DiagnosisReport = DiagnosisReport(
        recordId = recordId,
        createdAt = createdAt,
        examDescription = "乳腺超声影像",
        inferenceSource = inferenceSource,
        sourceImagePath = sourceImagePath,
        maskImagePath = null,
        lesionImageRatio = 0f,
        severityLevel = SeverityLevel.LOW,
        disclaimer = "classifier-only",
        suggestions = listOf("first", "second"),
        candidates = listOf(
            DiagnosisCandidate(
                name = "恶性病变",
                confidence = 0.82f,
                rawLabel = "malignant",
                diseaseName = "Malignant breast lesion",
                diseaseNameZh = "恶性病变",
            ),
        ),
        supportsSeverity = false,
        status = status,
    )
}
