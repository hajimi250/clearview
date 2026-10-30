package com.chengying.clearview.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chengying.clearview.core.database.DiagnosisAssetStore
import com.chengying.clearview.core.database.DiagnosisHistoryStore
import com.chengying.clearview.core.database.DiagnosisStoragePaths
import com.chengying.clearview.core.model.DiagnosisInferenceSource
import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import com.chengying.clearview.core.model.ImageAsset
import com.chengying.clearview.core.model.sanitized
import com.chengying.clearview.core.network.AnalyzeRecordsResponse
import com.chengying.clearview.core.network.AnalyzeResponse
import com.chengying.clearview.core.network.DiagnosisGateway
import com.chengying.clearview.core.network.toDiagnosisReport
import java.io.File
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val records: List<DiagnosisReport> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
    val totalCount: Int = 0,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false,
)

typealias HistoryRecordsApplier = (reports: List<DiagnosisReport>, focusRecordId: String?) -> Unit

class HistoryViewModel(
    private val storageDir: File,
    private val legacyFilesDir: File,
    private val diagnosisGateway: DiagnosisGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState
    private var refreshJob: Job? = null

    fun refresh(
        focusRecordId: String?,
        onRecordsApplied: HistoryRecordsApplier,
    ) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val localReports = withContext(Dispatchers.IO) {
                DiagnosisHistoryStore.readAll(storageDir)
            }
            applyReports(localReports, focusRecordId, onRecordsApplied)
            _uiState.update { it.copy(totalCount = localReports.size, hasMore = false, nextOffset = 0) }

            try {
                val (response, reports, nextOffset) = withContext(Dispatchers.IO) {
                    loadRemotePage(offset = 0)
                }
                val mergedReports = withContext(Dispatchers.IO) {
                    reports.forEach { report ->
                        DiagnosisHistoryStore.write(storageDir, report)
                    }
                    DiagnosisHistoryStore.readAll(storageDir)
                }
                applyReports(mergedReports, focusRecordId, onRecordsApplied)
                _uiState.update {
                    it.copy(
                        totalCount = mergedReports.remoteAwareTotal(response.total),
                        hasMore = response.hasMore,
                        nextOffset = nextOffset,
                    )
                }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(errorMessage = "历史记录同步失败，暂时保留本地记录。")
                }
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun loadMore(
        focusRecordId: String?,
        onRecordsApplied: HistoryRecordsApplier,
    ) {
        val current = _uiState.value
        if (
            current.loading ||
            current.loadingMore ||
            !current.hasMore
        ) {
            return
        }
        _uiState.update { it.copy(loadingMore = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    loadRemotePage(offset = current.nextOffset)
                }
            }.onSuccess { (response, reports, nextOffset) ->
                val mergedReports = withContext(Dispatchers.IO) {
                    reports.forEach { report ->
                        DiagnosisHistoryStore.write(storageDir, report)
                    }
                    DiagnosisHistoryStore.readAll(storageDir)
                }
                applyReports(mergedReports, focusRecordId, onRecordsApplied)
                _uiState.update {
                    it.copy(
                        totalCount = mergedReports.remoteAwareTotal(response.total),
                        hasMore = response.hasMore,
                        nextOffset = nextOffset,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(errorMessage = "加载更多历史记录失败，请稍后重试。")
                }
            }
            _uiState.update { it.copy(loadingMore = false) }
        }
    }

    fun delete(
        record: DiagnosisReport,
        focusRecordId: String?,
        onRecordsApplied: HistoryRecordsApplier,
        onRecordRemoved: (recordId: String) -> Unit,
        onSyncRequested: () -> Unit,
    ) {
        _uiState.update { it.copy(errorMessage = null) }
        val remoteBacked = record.isRemoteBacked()
        viewModelScope.launch {
            val remainingReports = withContext(Dispatchers.IO) {
                DiagnosisHistoryStore.remove(storageDir, record.recordId)
                DiagnosisHistoryStore.remove(legacyFilesDir, record.recordId)
                DiagnosisHistoryStore.readAll(storageDir)
            }
            applyReports(remainingReports, focusRecordId, onRecordsApplied)
            _uiState.update {
                it.copy(
                    totalCount = adjustedTotalCountAfterDelete(
                        previousTotalCount = it.totalCount,
                        remainingLocalCount = remainingReports.size,
                    ),
                )
            }
            onRecordRemoved(record.recordId)

            if (!remoteBacked) {
                onSyncRequested()
                return@launch
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    diagnosisGateway.deleteRecord(record.recordId)
                }
            }.onSuccess {
                onSyncRequested()
            }.onFailure { throwable ->
                if (throwable.isHttpNotFound()) {
                    onSyncRequested()
                } else {
                    _uiState.update {
                        it.copy(errorMessage = "本机缓存已删除，但后端删除失败，刷新后该记录可能再次同步回来。")
                    }
                }
            }
        }
    }

    fun deleteMany(
        records: List<DiagnosisReport>,
        focusRecordId: String?,
        onRecordsApplied: HistoryRecordsApplier,
        onRecordRemoved: (recordId: String) -> Unit,
        onSyncRequested: () -> Unit,
    ) {
        if (records.isEmpty()) return
        _uiState.update { it.copy(errorMessage = null) }
        val remoteBacked = records.filter { it.isRemoteBacked() }
        viewModelScope.launch {
            // 本机一次性清完再回写一次状态，避免每删一条都重建整张列表。
            val remainingReports = withContext(Dispatchers.IO) {
                records.forEach { record ->
                    DiagnosisHistoryStore.remove(storageDir, record.recordId)
                    DiagnosisHistoryStore.remove(legacyFilesDir, record.recordId)
                }
                DiagnosisHistoryStore.readAll(storageDir)
            }
            applyReports(remainingReports, focusRecordId, onRecordsApplied)
            _uiState.update {
                it.copy(
                    totalCount = adjustedTotalCountAfterDelete(
                        previousTotalCount = it.totalCount,
                        remainingLocalCount = remainingReports.size,
                        removedCount = records.size,
                    ),
                )
            }
            records.forEach { onRecordRemoved(it.recordId) }

            // 后端逐条删除，404 视为已在服务端删除；其余失败只汇总次数，不阻断本机已完成的删除。
            val failed = withContext(Dispatchers.IO) {
                remoteBacked.count { record ->
                    val outcome = runCatching { diagnosisGateway.deleteRecord(record.recordId) }
                    outcome.isFailure && outcome.exceptionOrNull()?.isHttpNotFound() != true
                }
            }
            onSyncRequested()
            if (failed > 0) {
                _uiState.update {
                    it.copy(errorMessage = "本机缓存已删除，但后端有 $failed 条删除失败，刷新后可能再次同步回来。")
                }
            }
        }
    }

    suspend fun persistAndReload(
        report: DiagnosisReport,
        onRecordsApplied: HistoryRecordsApplier,
    ) {
        val storedReports = withContext(Dispatchers.IO) {
            DiagnosisHistoryStore.write(storageDir, report)
            DiagnosisHistoryStore.readAll(storageDir)
        }
        applyReports(storedReports, report.recordId, onRecordsApplied)
        _uiState.update { it.copy(totalCount = storedReports.size) }
    }

    fun reset() {
        _uiState.value = HistoryUiState()
    }

    private fun applyReports(
        reports: List<DiagnosisReport>,
        focusRecordId: String?,
        onRecordsApplied: HistoryRecordsApplier,
    ) {
        _uiState.update { it.copy(records = reports) }
        onRecordsApplied(reports, focusRecordId)
    }

    private suspend fun loadRemotePage(
        offset: Int,
    ): Triple<AnalyzeRecordsResponse, List<DiagnosisReport>, Int> {
        val response = diagnosisGateway.listRecords(limit = REMOTE_PAGE_SIZE, offset = offset)
        val reports = response.records.map { record ->
            record.toLocalDiagnosisReport(diagnosisGateway)
        }
        return Triple(
            response,
            reports,
            response.nextOffset ?: (offset + response.records.size),
        )
    }

    private suspend fun AnalyzeResponse.toLocalDiagnosisReport(
        gateway: DiagnosisGateway,
    ): DiagnosisReport {
        val declared = assets?.sanitized()
        val sourceAsset = declared?.source
        val attentionAsset = declared?.attention
        val schemaTwo = schemaVersion == 2
        val localSourcePath = when {
            sourceAsset != null -> persistVerifiedAsset(
                recordId = recordId,
                kind = "source",
                asset = sourceAsset,
                url = sourceAsset.url ?: sourceImageUrl,
            ) { relativeUrl -> gateway.downloadSourceImageByRelativeUrl(recordId, relativeUrl) }
            schemaTwo -> null // A schema-2 report without a declared asset is not a verified source.
            else -> resolveLocalAsset(recordId, DiagnosisStoragePaths.SOURCE_IMAGE_FILE_NAME)?.absolutePath
                ?: runCatching {
                    persistAsset(
                        destination = DiagnosisStoragePaths.sourceImage(filesDir = storageDir, recordId = recordId),
                        bytes = gateway.downloadSourceImageByRelativeUrl(recordId, sourceImageUrl),
                    )
                }.getOrNull()
        }
        val localMaskPath = when {
            attentionAsset != null -> persistVerifiedAsset(
                recordId = recordId,
                kind = "attention",
                asset = attentionAsset,
                url = attentionAsset.url ?: maskUrl,
            ) { relativeUrl -> gateway.downloadMaskByRelativeUrl(recordId, relativeUrl) }
            schemaTwo -> null
            else -> resolveLocalAsset(recordId, DiagnosisStoragePaths.MASK_IMAGE_FILE_NAME)?.absolutePath
                ?: runCatching {
                    persistAsset(
                        destination = DiagnosisStoragePaths.maskImage(filesDir = storageDir, recordId = recordId),
                        bytes = gateway.downloadMaskByRelativeUrl(recordId, maskUrl),
                    )
                }.getOrNull()
        }

        return toDiagnosisReport(
            sourceImagePath = localSourcePath,
            maskImagePath = localMaskPath,
            sourceAssetVerified = sourceAsset != null && localSourcePath != null,
            maskAssetVerified = attentionAsset != null && localMaskPath != null,
        )
    }

    private suspend fun persistVerifiedAsset(
        recordId: String,
        kind: String,
        asset: ImageAsset,
        url: String?,
        download: suspend (String) -> ByteArray,
    ): String? {
        DiagnosisAssetStore.resolve(storageDir, recordId, kind, asset)?.let { return it }
        val relativeUrl = url?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            DiagnosisAssetStore.persist(storageDir, recordId, kind, asset, download(relativeUrl))
        }.getOrNull()
    }

    private fun resolveLocalAsset(recordId: String, fileName: String): File? =
        DiagnosisStoragePaths.resolveRecordDirectory(storageDir, recordId)
            ?.let { recordDirectory -> File(recordDirectory, fileName) }
            ?.takeIf(File::exists)

    private fun persistAsset(destination: File, bytes: ByteArray): String {
        destination.parentFile?.mkdirs()
        destination.writeBytes(bytes)
        DiagnosisStoragePaths.evictIfNeeded(storageDir)
        return destination.absolutePath
    }
}

private const val REMOTE_PAGE_SIZE = 20

private fun List<DiagnosisReport>.remoteAwareTotal(remoteTotal: Int): Int =
    remoteTotal + count { report -> !report.isRemoteBacked() }

private fun DiagnosisReport.isRemoteBacked(): Boolean =
    inferenceSource == DiagnosisInferenceSource.ONLINE && status == DiagnosisStatus.SUCCESS

private fun adjustedTotalCountAfterDelete(
    previousTotalCount: Int,
    remainingLocalCount: Int,
    removedCount: Int = 1,
): Int = (previousTotalCount - removedCount).coerceAtLeast(remainingLocalCount).coerceAtLeast(0)

private fun Throwable.isHttpNotFound(): Boolean =
    httpStatusCodeOrNull() == HttpURLConnection.HTTP_NOT_FOUND

private fun Throwable.httpStatusCodeOrNull(): Int? {
    val directCode = runCatching {
        javaClass.methods
            .firstOrNull { method -> method.name == "code" && method.parameterCount == 0 }
            ?.invoke(this) as? Int
    }.getOrNull()
    if (directCode != null) return directCode
    return cause?.takeIf { it !== this }?.httpStatusCodeOrNull()
}
