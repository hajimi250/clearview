package com.chengying.clearview

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.chengying.clearview.core.designsystem.component.ToolBar
import com.chengying.clearview.core.designsystem.component.ToolButton
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import com.chengying.clearview.core.designsystem.theme.ThemeMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chengying.clearview.core.database.DiagnosisStoragePaths
import com.chengying.clearview.core.database.DiagnosisAssetStore
import com.chengying.clearview.core.designsystem.component.BrandMark
import com.chengying.clearview.core.model.AcquisitionInfo
import com.chengying.clearview.core.model.ImageAsset
import com.chengying.clearview.core.model.toDisplayLabel
import com.chengying.clearview.core.model.DiagnosisInferenceSource
import com.chengying.clearview.core.model.DiagnosisReport
import com.chengying.clearview.core.model.DiagnosisStatus
import com.chengying.clearview.core.model.SeverityLevel
import com.chengying.clearview.core.model.displayDiseaseName
import com.chengying.clearview.core.model.LocalDiagnosisFactory
import com.chengying.clearview.core.network.toDiagnosisReport
import com.chengying.clearview.feature.assistant.AssistantConversationMessage
import com.chengying.clearview.feature.assistant.AssistantConversationStore
import com.chengying.clearview.feature.assistant.AssistantDiagnosisContext
import com.chengying.clearview.feature.assistant.AssistantScreen
import com.chengying.clearview.feature.assistant.AssistantThreadSummary
import com.chengying.clearview.feature.capture.CaptureScreen
import com.chengying.clearview.feature.history.HistoryScreen
import com.chengying.clearview.feature.history.HistoryViewModel
import com.chengying.clearview.feature.home.HomeScreen
import com.chengying.clearview.feature.knowledge.KnowledgeScreen
import com.chengying.clearview.feature.result.ResultAnalysisProgress
import com.chengying.clearview.feature.result.ResultScreen
import com.chengying.clearview.feature.settings.ServiceHealthStatus
import com.chengying.clearview.feature.settings.SettingsScreen
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private const val APP_LOG_TAG = "ClearViewApp"

private val topLevelDestinations = listOf(
    TopLevelDestination("home", "\u9996\u9875", Icons.Outlined.Home),
    TopLevelDestination("knowledge", "\u77e5\u8bc6\u5e93", Icons.Outlined.Book),
    TopLevelDestination("llm", "\u52a9\u624b", Icons.Outlined.AutoAwesome),
    TopLevelDestination("settings", "\u8bbe\u7f6e", Icons.Outlined.Settings),
)

private fun diagnosisStorageDir(context: Context): File =
    runCatching { context.cacheDir.canonicalFile }.getOrElse { context.cacheDir }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClearViewApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val appVersionName = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isHistoryRoute = currentDestination?.hierarchy?.any { it.route == "history" } == true
    val isCaptureRoute = currentDestination?.hierarchy?.any { it.route == "capture" } == true
    val isResultRoute = currentDestination?.hierarchy?.any { it.route == "result" } == true
    val isSettingsRoute = currentDestination?.hierarchy?.any { it.route == "settings" } == true
    val isAssistantRoute = currentDestination?.hierarchy?.any { it.route == "llm" } == true
    // The assistant docks its own input bar, so the floating import action must not cover it and the
    // transcript has to reach the window bottom while the system keyboard is up.
    val assistantTyping = isAssistantRoute && WindowInsets.isImeVisible
    val showImportAction = !isCaptureRoute && !isAssistantRoute && !WindowInsets.isImeVisible

    val currentResult = remember { mutableStateOf<DiagnosisReport?>(null) }
    // Survives recreation so the result page and comparison pair are not wiped by a null first frame.
    var currentRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    val assistantThreadTitles = remember {
        mutableStateMapOf<String, String>()
    }
    val graph = (context.applicationContext as ClearViewApplication).appGraph
    val serviceEndpointConfig = graph.serviceEndpointConfig
    val diagnosisGateway = graph.diagnosisGateway
    val historyViewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    storageDir = diagnosisStorageDir(context),
                    legacyFilesDir = context.filesDir,
                    diagnosisGateway = diagnosisGateway,
                )
            }
        },
    )
    val historyUiState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val historyRecords = historyUiState.records

    var resultSaveWarning by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun setCurrentResult(report: DiagnosisReport?) {
        currentResult.value = report
        currentRecordId = report?.recordId
        if (resultSaveWarning?.first != report?.recordId) resultSaveWarning = null
    }

    fun applyHistoryFocus(reports: List<DiagnosisReport>, focusRecordId: String?) {
        val targetId = focusRecordId ?: currentRecordId
        setCurrentResult(targetId?.let { id -> reports.firstOrNull { report -> report.recordId == id } })
    }

    val timestampFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }

    var historyRefreshNonce by rememberSaveable { mutableIntStateOf(0) }
    var selectedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var acquisitionTime by rememberSaveable { mutableStateOf<String?>(null) }
    // 乳腺超声不使用旧版体位/完整度语义；兼容字段固定为未知。
    val acquisition = AcquisitionInfo(
        projection = "UNKNOWN",
        acquiredAt = acquisitionTime,
        completenessConfirmed = false,
    )
    fun updateAcquisition(value: AcquisitionInfo = AcquisitionInfo()) {
        acquisitionTime = value.acquiredAt
    }
    var captureStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // Composition-scoped: recreating the activity cancels the request, so "analyzing" must not be restored.
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf<ResultAnalysisProgress?>(null) }
    var analysisErrorMessage by remember { mutableStateOf<String?>(null) }
    // Opening another record during analysis must show that record, not the progress panel.
    val visibleAnalysisProgress = analysisProgress?.takeIf { progress ->
        val current = currentResult.value
        current == null || current.recordId == progress.recordId
    }
    var isImporting by remember { mutableStateOf(false) }
    var pendingCameraImagePath by remember { mutableStateOf<String?>(null) }
    var pendingAssistantPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    var assistantConversationRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var assistantChatKey by rememberSaveable { mutableStateOf(AssistantConversationStore.newChatThreadKey()) }
    var localCacheStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var serviceHealthStatus by remember {
        mutableStateOf(
            ServiceHealthStatus(
                inferenceOnline = null,
                backendBaseUrl = serviceEndpointConfig.backendBaseUrl,
            ),
        )
    }

    val assistantConversationRecord = assistantConversationRecordId?.let { recordId ->
        currentResult.value?.takeIf { it.recordId == recordId }
            ?: historyRecords.firstOrNull { it.recordId == recordId }
    }
    val assistantDiagnosisContexts = historyRecords.map { report ->
        AssistantDiagnosisContext(
            recordId = report.recordId,
            diagnosisNumber = report.toDiagnosisNumber(),
            sourceLabel = report.toAssistantSourceLabel(),
            finding = report.displayDiseaseName(),
            createdAt = report.createdAt,
        )
    }
    val assistantThreads = assistantThreadTitles.map { (threadKey, title) ->
        if (AssistantConversationStore.isFreeChatThread(threadKey)) {
            AssistantThreadSummary(
                threadKey = threadKey,
                title = title,
                createdAt = freeChatCreatedAt(threadKey),
            )
        } else {
            val record = historyRecords.firstOrNull { it.recordId == threadKey }
            AssistantThreadSummary(
                threadKey = threadKey,
                title = record?.displayDiseaseName() ?: "记录 $threadKey",
                createdAt = record?.createdAt,
            )
        }
    }.sortedWith(
        compareByDescending<AssistantThreadSummary> { it.createdAt ?: "" }
            .thenByDescending { it.threadKey },
    )

    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            if (!context.deleteSharedPreferences("location-cache")) {
                Log.w(APP_LOG_TAG, "Retired location cache could not be deleted")
            }
        }
    }

    LaunchedEffect(context) {
        val titles = withContext(Dispatchers.IO) {
            AssistantConversationStore.readAll(context)
                .mapNotNull { (key, messages) -> messages.toThreadTitle()?.let { key to it } }
                .toMap()
        }
        assistantThreadTitles.clear()
        assistantThreadTitles.putAll(titles)
    }

    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            DiagnosisStoragePaths.clearAll(context.filesDir)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (isImporting || isAnalyzing) return@rememberLauncherForActivityResult
        if (uri == null) {
            captureStatusMessage = "已取消相册选择。"
            return@rememberLauncherForActivityResult
        }
        isImporting = true
        captureStatusMessage = "正在导入影像…"
        coroutineScope.launch {
            try {
                val importedPath = importSelectedImage(context, uri)
                selectedImagePath = importedPath
                updateAcquisition()
                captureStatusMessage = "已从相册载入影像，可检查后提交分析。"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(
                    APP_LOG_TAG,
                    "Image import failed (${failure.javaClass.name})\n${failure.stackTrace.joinToString("\n")}",
                )
                captureStatusMessage = "影像导入失败，请重新选择可读取的图片；原选择未改变。"
            } finally {
                isImporting = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val imagePath = pendingCameraImagePath
        pendingCameraImagePath = null
        if (success && imagePath != null) {
            selectedImagePath = imagePath
            updateAcquisition()
            captureStatusMessage = "\u5df2\u5b8c\u6210\u62cd\u6444\uff1a${File(imagePath).name}"
        } else {
            imagePath?.let { File(it).delete() }
            captureStatusMessage = "\u5df2\u53d6\u6d88\u62cd\u6444\u3002"
        }
    }

    val inferenceServiceOnline by produceState<Boolean?>(
        initialValue = null,
        key1 = historyRefreshNonce,
        key2 = serviceEndpointConfig.backendBaseUrl,
    ) {
        value = runCatching {
            diagnosisGateway.listRecords(limit = 1, offset = 0)
            true
        }.getOrDefault(false)
    }
    LaunchedEffect(inferenceServiceOnline) {
        inferenceServiceOnline?.let { online ->
            serviceHealthStatus = serviceHealthStatus.copy(
                inferenceOnline = online,
                backendBaseUrl = graph.currentBackendBaseUrl(),
            )
        }
    }

    LaunchedEffect(historyRefreshNonce) {
        historyViewModel.refresh(
            focusRecordId = currentResult.value?.recordId ?: currentRecordId,
            onRecordsApplied = ::applyHistoryFocus,
        )
    }

    fun updateAssistantThreadTitle(
        threadKey: String,
        messages: List<AssistantConversationMessage>,
    ) {
        val title = messages.toThreadTitle()
        if (title == null) {
            assistantThreadTitles.remove(threadKey)
        } else {
            assistantThreadTitles[threadKey] = title
        }
    }

    fun clearAssistantConversation(recordId: String) {
        AssistantConversationStore.removeThread(
            context = context,
            key = AssistantConversationStore.threadKey(recordId),
        )
        assistantThreadTitles.remove(recordId)
        if (assistantConversationRecordId == recordId) {
            assistantConversationRecordId = null
        }
    }

    fun checkServiceStatus() {
        serviceHealthStatus = serviceHealthStatus.copy(
            checking = true,
            backendBaseUrl = graph.currentBackendBaseUrl(),
        )
        coroutineScope.launch {
            val inferenceOnline = runCatching { diagnosisGateway.listRecords(limit = 1, offset = 0) }.isSuccess
            serviceHealthStatus = ServiceHealthStatus(
                checking = false,
                inferenceOnline = inferenceOnline,
                backendBaseUrl = graph.currentBackendBaseUrl(),
            )
        }
    }

    fun clearLocalCache() {
        deleteLocalImageIfExists(selectedImagePath)
        deleteLocalImageIfExists(pendingCameraImagePath)
        clearDirectoryIfExists(File(context.cacheDir, "capture"))
        clearDirectoryIfExists(File(context.cacheDir, "selected"))
        DiagnosisStoragePaths.clearAll(diagnosisStorageDir(context))
        DiagnosisStoragePaths.clearAll(context.filesDir)
        AssistantConversationStore.clearAll(context)

        selectedImagePath = null
        updateAcquisition()
        setCurrentResult(null)
        pendingCameraImagePath = null
        historyViewModel.reset()
        assistantThreadTitles.clear()
        assistantConversationRecordId = null
        assistantChatKey = AssistantConversationStore.newChatThreadKey()
        pendingAssistantPrompt = null
        captureStatusMessage = null
        analysisErrorMessage = null
        localCacheStatusMessage = "已清除诊断历史记录、助手历史记录和本地样本缓存。"
    }

    fun navigateToAssistant(
        record: DiagnosisReport? = null,
        prompt: String? = null,
    ) {
        assistantConversationRecordId = record?.recordId
        pendingAssistantPrompt = prompt
        navController.navigate("llm") {
            launchSingleTop = true
        }
    }

    fun navigateToTopLevel(route: String) {
        if (route == "llm") {
            assistantConversationRecordId = null
            pendingAssistantPrompt = null
        }
        navController.navigate(route) {
            popUpTo("home") {
                inclusive = false
            }
            launchSingleTop = true
        }
    }

    fun openHistoryUtility() {
        navController.navigate("history") {
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column(Modifier.statusBarsPadding()) {
                        ToolBar {
                            if (isCaptureRoute || isResultRoute || isHistoryRoute) {
                                ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", { navController.popBackStack() })
                            }
                            BrandMark(Modifier.size(24.dp))
                            Text("澄影", Modifier.weight(1f), style = ClearViewTokens.typography.heading)
                            if (!isHistoryRoute) ToolButton(Icons.Outlined.AccessTime, "诊断历史", ::openHistoryUtility)
                        }
                    }
                },
                floatingActionButton = {
                    if (showImportAction) {
                        val colors = ClearViewTokens.colors
                        Surface(
                            onClick = { navController.navigate("capture") { launchSingleTop = true } },
                            modifier = Modifier.size(56.dp).semantics { role = Role.Button },
                            shape = ClearViewTokens.shapes.floatingAction,
                            color = colors.surface1,
                            contentColor = colors.text1,
                            tonalElevation = 0.dp,
                            shadowElevation = 6.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "导入影像", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!assistantTyping) {
                        Column(Modifier.navigationBarsPadding()) {
                            ToolBar {
                                topLevelDestinations.forEach { destination ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                                    Column(
                                        Modifier.weight(1f).heightIn(min = 48.dp)
                                            .background(if (selected) ClearViewTokens.colors.surface2 else ClearViewTokens.colors.surface1)
                                            .selectable(selected = selected, role = Role.Tab, onClick = { navigateToTopLevel(destination.route) })
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(destination.icon, contentDescription = null, modifier = Modifier.size(16.dp),
                                            tint = if (selected) ClearViewTokens.colors.text1 else ClearViewTokens.colors.text2)
                                        Text(destination.label, style = ClearViewTokens.typography.label,
                                            color = if (selected) ClearViewTokens.colors.text1 else ClearViewTokens.colors.text2)
                                    }
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.padding(
                        if (assistantTyping) PaddingValues(top = innerPadding.calculateTopPadding()) else innerPadding,
                    ),
                    enterTransition = { fadeIn(tween(ClearViewTokens.motion.page)) },
                    exitTransition = { fadeOut(tween(ClearViewTokens.motion.page)) },
                    popEnterTransition = { fadeIn(tween(ClearViewTokens.motion.page)) },
                    popExitTransition = { fadeOut(tween(ClearViewTokens.motion.page)) },
                ) {
                    composable("home") {
                        HomeScreen(
                            onStartDiagnosis = { navController.navigate("capture") },
                            recentDiagnoses = historyRecords,
                            onOpenHistory = { navController.navigate("history") },
                            onOpenRecord = { record ->
                                setCurrentResult(record)
                                navController.navigate("result")
                            },
                            onRetryRecord = { record ->
                                selectedImagePath = record.sourceImagePath
                                updateAcquisition(record.acquisition ?: AcquisitionInfo())
                                captureStatusMessage = "请确认待分析影像后提交分析。"
                                navController.navigate("capture")
                            },
                            onOpenSettings = { navController.navigate("settings") },
                            serviceOnline = serviceHealthStatus.inferenceOnline,
                            serviceChecking = serviceHealthStatus.checking,
                        )
                    }
                    composable("capture") {
                        CaptureScreen(
                            selectedImagePath = selectedImagePath,
                            statusMessage = captureStatusMessage,
                            isAnalyzing = isAnalyzing,
                            isImporting = isImporting,
                            analysisProgressVisible = visibleAnalysisProgress != null,
                            acquisition = acquisition,
                            onAcquisitionChange = ::updateAcquisition,
                            onPickFromGallery = {
                                if (isImporting || isAnalyzing) return@CaptureScreen
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onCapturePhoto = {
                                if (isImporting || isAnalyzing) return@CaptureScreen
                                val (captureUri, imagePath) = createCameraCaptureUri(context)
                                pendingCameraImagePath = imagePath
                                captureStatusMessage = "\u6b63\u5728\u6253\u5f00\u7cfb\u7edf\u76f8\u673a..."
                                cameraLauncher.launch(captureUri)
                            },
                            onOpenLatestResult = {
                                navController.navigate("result") { launchSingleTop = true }
                            },
                            onRemoveImage = {
                                if (isImporting || isAnalyzing) return@CaptureScreen
                                selectedImagePath?.let(::File)?.let { image ->
                                    if (
                                        image.parentFile == File(context.cacheDir, "selected") ||
                                        image.parentFile == File(context.cacheDir, "capture")
                                    ) {
                                        deleteLocalImageIfExists(image.absolutePath)
                                    }
                                }
                                selectedImagePath = null
                                updateAcquisition()
                                captureStatusMessage = "已移除当前影像，可重新导入；历史记录不受影响。"
                            },
                            onSubmitAnalysis = {
                                if (isImporting || isAnalyzing) return@CaptureScreen
                                val sourcePath = selectedImagePath
                                if (sourcePath == null) {
                                    captureStatusMessage = "请先导入一张乳腺超声影像。"
                                    return@CaptureScreen
                                }
                                val recordId = "diag-${System.currentTimeMillis()}"
                                val createdAt = LocalDateTime.now().format(timestampFormatter)
                                val acquisitionSnapshot = acquisition
                                isAnalyzing = true
                                analysisErrorMessage = null
                                analysisProgress = ResultAnalysisProgress(
                                    recordId = recordId,
                                    createdAt = createdAt,
                                    sourceImagePath = sourcePath,
                                )
                                // A new submission replaces the visible record; stale output must not be shown as this run.
                                setCurrentResult(null)
                                captureStatusMessage = "已提交分析，可在结果页查看进度。"
                                navController.navigate("result") { launchSingleTop = true }
                                coroutineScope.launch {
                                    var stage = "影像准备"
                                    try {
                                        val storedSource = withContext(Dispatchers.IO) {
                                            persistSelectedImage(context, sourcePath, recordId)
                                        }
                                        stage = "影像分析"
                                        val analyzeResult = try {
                                            Result.success(diagnosisGateway.analyzeImage(
                                                imagePath = storedSource,
                                                topK = 3,
                                                clientRecordId = recordId,
                                                acquisition = acquisitionSnapshot,
                                            ))
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            Log.e(APP_LOG_TAG, "Hospital server analysis failed", failure)
                                            Result.failure(failure)
                                        }
                                        stage = "结果整理"
                                        val response = analyzeResult.getOrNull()
                                        val result = if (response != null) {
                                            val paths = withContext(Dispatchers.IO) {
                                                val source = cacheVerifiedAsset(diagnosisStorageDir(context), recordId, "source", response.assets?.source) {
                                                    diagnosisGateway.downloadSourceImageByRelativeUrl(recordId, response.sourceImageUrl)
                                                }
                                                val mask = cacheVerifiedAsset(diagnosisStorageDir(context), recordId, "attention", response.assets?.attention) {
                                                    diagnosisGateway.downloadMaskByRelativeUrl(recordId, response.maskUrl)
                                                }
                                                source to mask
                                            }
                                            response.toDiagnosisReport(
                                                createdAt = createdAt,
                                                sourceImagePath = paths.first ?: storedSource,
                                                maskImagePath = paths.second.takeIf { paths.first != null },
                                            ).copy(
                                                sourceAssetVerified = paths.first != null,
                                                maskAssetVerified = paths.first != null && paths.second != null,
                                            )
                                        } else {
                                            LocalDiagnosisFactory.backendUnavailableSample(
                                                recordId = recordId,
                                                createdAt = createdAt,
                                                sourceImagePath = storedSource,
                                                reason = analyzeResult.exceptionOrNull()?.toUserVisibleDiagnosisReason()
                                                    ?: "分析服务未返回有效结果。",
                                                inferenceSource = DiagnosisInferenceSource.ONLINE,
                                            ).copy(acquisition = acquisitionSnapshot)
                                        }
                                        // A completed analysis stays visible even if writing the local cache fails.
                                        if (currentRecordId == null || currentRecordId == recordId) {
                                            setCurrentResult(result)
                                        }
                                        stage = "本机保存"
                                        var persisted: DiagnosisReport? = null
                                        var saveFailed = false
                                        try {
                                            historyViewModel.persistAndReload(
                                                report = result,
                                                onRecordsApplied = { reports, _ ->
                                                    persisted = reports.firstOrNull { it.recordId == recordId }
                                                },
                                            )
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            saveFailed = true
                                            Log.e(APP_LOG_TAG, "Analysis result could not be saved locally (${failure.javaClass.name})")
                                            val saveDetail = "存储异常：${failure.javaClass.simpleName}。"
                                            resultSaveWarning = recordId to if (result.status == DiagnosisStatus.SUCCESS) {
                                                "分析已完成，但本次结果未能写入本机历史，关闭本页后可能无法再打开。$saveDetail"
                                            } else {
                                                "本次分析未获得有效结果，记录也未能写入本机历史。$saveDetail"
                                            }
                                        }
                                        // Do not replace a record the user opened while this request was running.
                                        if (!saveFailed && (currentRecordId == null || currentRecordId == recordId)) {
                                            // Prefer the re-hashed, read-back report over the in-memory copy.
                                            setCurrentResult(persisted ?: result)
                                        }
                                        captureStatusMessage = if (saveFailed) {
                                            if (result.status == DiagnosisStatus.SUCCESS) {
                                                "分析已完成，但本机保存未完成；请刷新历史确认。"
                                            } else {
                                                "分析未完成，结果也未能写入本机历史；请刷新历史确认。"
                                            }
                                        } else if (result.status == DiagnosisStatus.SUCCESS) {
                                            // 分析成功后不再显示"影像分析完成：…"提示。
                                            null
                                        } else {
                                            buildCaptureCompletionMessage(result)
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        Log.e(APP_LOG_TAG, "Analysis stopped during $stage (${failure.javaClass.name})")
                                        captureStatusMessage = "分析未完成，请刷新历史确认。"
                                        analysisErrorMessage =
                                            "本次分析未完成（${stage}阶段，${failure.javaClass.simpleName}）。请先在诊断历史中确认记录状态，再决定是否重新提交；失败不表示正常。"
                                    } finally {
                                        isAnalyzing = false
                                        analysisProgress = null
                                    }
                                }
                            },
                            onSaveForLater = {
                                if (isImporting || isAnalyzing) return@CaptureScreen
                                val sourcePath = selectedImagePath
                                if (sourcePath == null) {
                                    captureStatusMessage = "请先导入一张乳腺超声影像。"
                                    return@CaptureScreen
                                }
                                val recordId = "pending-${System.currentTimeMillis()}"
                                val createdAt = LocalDateTime.now().format(timestampFormatter)
                                val storedSource = persistSelectedImage(
                                    context = context,
                                    sourcePath = sourcePath,
                                    recordId = recordId,
                                )
                                val result = LocalDiagnosisFactory.savedPendingSample(
                                    recordId = recordId,
                                    createdAt = createdAt,
                                    sourceImagePath = storedSource,
                                ).copy(acquisition = acquisition)
                                coroutineScope.launch {
                                    try {
                                        historyViewModel.persistAndReload(
                                            report = result,
                                            onRecordsApplied = ::applyHistoryFocus,
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        Log.e(APP_LOG_TAG, "Pending record could not be saved locally (${failure.javaClass.name})")
                                        captureStatusMessage =
                                            "待分析记录未能写入本机历史（${failure.javaClass.simpleName}），请刷新后重试。"
                                    }
                                }
                                captureStatusMessage = "\u5df2\u4fdd\u5b58\u4e3a\u5f85\u5206\u6790\u8bb0\u5f55\uff0c\u53ef\u5728\u5386\u53f2\u9875\u7ee7\u7eed\u5904\u7406\u3002"
                                navController.navigate("history")
                            },
                        )
                    }
                    composable("result") {
                        ResultScreen(
                            result = currentResult.value,
                            onOpenConversation = { record ->
                                setCurrentResult(record)
                                navigateToAssistant(record)
                            },
                            onRetake = {
                                currentResult.value?.takeIf { it.status != DiagnosisStatus.SUCCESS }?.let { record ->
                                    selectedImagePath = record.sourceImagePath
                                    updateAcquisition(record.acquisition ?: AcquisitionInfo())
                                    captureStatusMessage = "请确认待分析影像后提交分析。"
                                }
                                navController.navigate("capture")
                            },
                            analysisProgress = visibleAnalysisProgress,
                            analysisErrorMessage = analysisErrorMessage,
                            saveWarning = resultSaveWarning
                                ?.takeIf { it.first == currentResult.value?.recordId }
                                ?.second,
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            records = historyRecords,
                            loading = historyUiState.loading,
                            loadingMore = historyUiState.loadingMore,
                            errorMessage = historyUiState.errorMessage,
                            totalCount = historyUiState.totalCount,
                            hasMore = historyUiState.hasMore,
                            onRefresh = { historyRefreshNonce += 1 },
                            onLoadMore = {
                                historyViewModel.loadMore(
                                    focusRecordId = currentResult.value?.recordId ?: currentRecordId,
                                    onRecordsApplied = ::applyHistoryFocus,
                                )
                            },
                            onOpenRecord = { record ->
                                setCurrentResult(record)
                                navController.navigate("result")
                            },
                            onOpenConversation = { record ->
                                setCurrentResult(record)
                                navigateToAssistant(record)
                            },
                            onDeleteRecord = { record ->
                                captureStatusMessage = "已删除记录：${record.recordId}"
                                historyViewModel.delete(
                                    record = record,
                                    focusRecordId = currentResult.value?.recordId ?: currentRecordId,
                                    onRecordsApplied = ::applyHistoryFocus,
                                    onRecordRemoved = { recordId ->
                                        clearAssistantConversation(recordId)
                                        if ((currentResult.value?.recordId ?: currentRecordId) == recordId) {
                                            setCurrentResult(null)
                                        }
                                    },
                                    onSyncRequested = { historyRefreshNonce += 1 },
                                )
                            },
                            onDeleteRecords = { records ->
                                captureStatusMessage = "已删除 ${records.size} 条记录"
                                historyViewModel.deleteMany(
                                    records = records,
                                    focusRecordId = currentResult.value?.recordId ?: currentRecordId,
                                    onRecordsApplied = ::applyHistoryFocus,
                                    onRecordRemoved = { recordId ->
                                        clearAssistantConversation(recordId)
                                        if ((currentResult.value?.recordId ?: currentRecordId) == recordId) {
                                            setCurrentResult(null)
                                        }
                                    },
                                    onSyncRequested = { historyRefreshNonce += 1 },
                                )
                            },
                        )
                    }
                    composable("knowledge") {
                        KnowledgeScreen()
                    }
                    composable("llm") {
                        AssistantScreen(
                            conversationRecordId = assistantConversationRecordId,
                            conversationKey = assistantConversationRecordId ?: assistantChatKey,
                            conversationTitle = assistantConversationRecord?.let(::assistantConversationTitle),
                            diagnosisContexts = assistantDiagnosisContexts,
                            threads = assistantThreads,
                            initialPrompt = pendingAssistantPrompt,
                            assistantGateway = graph.assistantGateway,
                            onInitialPromptConsumed = { pendingAssistantPrompt = null },
                            onConversationUpdated = ::updateAssistantThreadTitle,
                            onSelectConversation = { threadKey ->
                                if (AssistantConversationStore.isFreeChatThread(threadKey)) {
                                    assistantConversationRecordId = null
                                    assistantChatKey = threadKey
                                } else {
                                    assistantConversationRecordId = threadKey
                                }
                            },
                            onStartNewConversation = {
                                assistantConversationRecordId = null
                                assistantChatKey = AssistantConversationStore.newChatThreadKey()
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            themeMode = themeMode,
                            appVersion = appVersionName,
                            onThemeModeChange = onThemeModeChange,
                            onClearLocalCache = ::clearLocalCache,
                            onOpenDiagnosisHistory = ::openHistoryUtility,
                            serviceHealthStatus = serviceHealthStatus,
                            onCheckServiceStatus = ::checkServiceStatus,
                            localCacheStatusMessage = localCacheStatusMessage,
                            onSaveBackendBaseUrl = { url ->
                                graph.serviceEndpointConfigStore.save(ServiceEndpointConfig(url))
                                // 网关逐请求按最新地址重写 origin，保存后立即重新检查，无需重启应用。
                                checkServiceStatus()
                            },
                        )
                    }
                }
            }
    }
}

private fun createCameraCaptureUri(context: Context): Pair<Uri, String> {
    val directory = File(context.cacheDir, "capture")
    directory.mkdirs()
    val imageFile = File(directory, "capture-${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile,
    )
    return uri to imageFile.absolutePath
}

private suspend fun importSelectedImage(context: Context, uri: Uri): String {
    var importedFile: File? = null
    try {
        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "selected")
            directory.mkdirs()
            val destination = File.createTempFile("selected-", ".jpg", directory)
            importedFile = destination
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选图片。" }
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            destination.absolutePath
        }
    } catch (failure: Exception) {
        // Cancellation can arrive after copying but before dispatching the result to the UI.
        withContext(NonCancellable + Dispatchers.IO) {
            importedFile?.delete()
        }
        throw failure
    }
}

private fun persistSelectedImage(
    context: Context,
    sourcePath: String,
    recordId: String,
): String {
    val destination = DiagnosisStoragePaths.sourceImage(
        filesDir = diagnosisStorageDir(context),
        recordId = recordId,
    )
    destination.parentFile?.mkdirs()
    File(sourcePath).inputStream().use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    }
    DiagnosisStoragePaths.evictIfNeeded(diagnosisStorageDir(context))
    return destination.absolutePath
}

private suspend fun cacheVerifiedAsset(
    storageDir: File,
    recordId: String,
    kind: String,
    asset: ImageAsset?,
    download: suspend () -> ByteArray,
): String? {
    if (asset == null) return null
    return try {
        DiagnosisAssetStore.resolve(storageDir, recordId, kind, asset)
            ?: DiagnosisAssetStore.persist(storageDir, recordId, kind, asset, download())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private fun deleteLocalImageIfExists(path: String?) {
    if (path.isNullOrBlank()) return
    runCatching { File(path).delete() }
}

private fun clearDirectoryIfExists(directory: File) {
    if (!directory.exists()) return
    runCatching { directory.deleteRecursively() }
}

private fun DiagnosisReport.toAssistantSourceLabel(): String = when (inferenceSource) {
    DiagnosisInferenceSource.ONLINE -> "服务端分析"
    DiagnosisInferenceSource.OFFLINE -> "端侧推理（历史）"
    DiagnosisInferenceSource.PENDING -> "待分析"
}

private fun DiagnosisReport.toDiagnosisNumber(): String {
    parseCreatedAt(createdAt)?.let { return it.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) }
    return createdAt.filter(Char::isDigit).padEnd(14, '0').take(14)
}

private fun parseCreatedAt(value: String): LocalDateTime? {
    val raw = value.trim()
    return runCatching { OffsetDateTime.parse(raw).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull()
}

private fun buildCaptureCompletionMessage(result: DiagnosisReport): String {
    val prefix = when (result.inferenceSource) {
        DiagnosisInferenceSource.ONLINE -> "影像分析"
        DiagnosisInferenceSource.OFFLINE -> "端侧推理"
        DiagnosisInferenceSource.PENDING -> "影像保存"
    }
    return "$prefix 未完成，已保留影像，可稍后重试。"
}

private fun Throwable.toUserVisibleDiagnosisReason(): String {
    val detail = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    return "分析失败：$detail"
}

private fun List<AssistantConversationMessage>.toThreadTitle(): String? {
    val firstQuestion = firstOrNull { it.isUser }?.text
        ?: firstOrNull()?.text
        ?: return null
    val normalized = firstQuestion.replace(Regex("""\s+"""), " ").trim()
    if (normalized.isEmpty()) return null
    return if (normalized.length > 24) normalized.take(24) + "…" else normalized
}

private fun freeChatCreatedAt(threadKey: String): String? {
    if (!threadKey.startsWith(AssistantConversationStore.CHAT_THREAD_PREFIX)) return null
    val millis = threadKey.removePrefix(AssistantConversationStore.CHAT_THREAD_PREFIX).toLongOrNull()
        ?: return null
    return runCatching {
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull()
}

private fun assistantConversationTitle(record: DiagnosisReport): String {
    return record.displayDiseaseName()
}
