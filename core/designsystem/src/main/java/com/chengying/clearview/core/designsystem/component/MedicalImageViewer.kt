package com.chengying.clearview.core.designsystem.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** EMPTY_MASK means a decoded, aligned, all-zero foreground mask, never a normal diagnosis. */
enum class MedicalImageLoadState { LOADING, MISSING, DECODE_ERROR, MISALIGNED, READY, EMPTY_MASK }

// Paths identify immutable files; replace the path when replacing image contents.
@Composable
fun MedicalImageViewer(
    sourceImagePath: String?,
    modifier: Modifier = Modifier,
    maskImagePath: String? = null,
    overlayImagePath: String? = null,
    contentDescription: String = "医学影像",
    showLayers: Boolean = false,
    onSourceStateChange: (MedicalImageLoadState) -> Unit = {},
    onMaskStateChange: (MedicalImageLoadState) -> Unit = {},
) {
    val source = rememberDecodedImage(sourceImagePath, isMask = false)
    val mask = rememberDecodedImage(maskImagePath, isMask = true)
    val overlay = rememberDecodedImage(overlayImagePath, isMask = false)
    val alignedMask = alignedLayer(source, mask)
    val alignedOverlay = alignedLayer(source, overlay)
    val sourceCallback by rememberUpdatedState(onSourceStateChange)
    val maskCallback by rememberUpdatedState(onMaskStateChange)
    LaunchedEffect(source.state) { sourceCallback(source.state) }
    LaunchedEffect(alignedMask.state) { maskCallback(alignedMask.state) }
    var fullscreen by remember(sourceImagePath, maskImagePath, overlayImagePath) { mutableStateOf(false) }
    var requestedLayer by remember(sourceImagePath, maskImagePath, overlayImagePath) { mutableStateOf(ImageLayer.SOURCE) }
    val overlayLayer = if (overlayImagePath.isNullOrBlank()) alignedMask else alignedOverlay
    val layer = requestedLayer.takeIf { layerAvailable(it, source, alignedMask, overlayLayer) } ?: ImageLayer.SOURCE
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                .background(colors.imageCanvas)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "打开全屏影像查看器",
                ) { fullscreen = true },
        ) {
            ImageCanvas(
                source = source,
                mask = alignedMask,
                overlay = overlayLayer,
                overlayIsMask = overlayImagePath.isNullOrBlank(),
                layer = layer,
                description = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
            val imageDetails = "${source.width} × ${source.height}\n${source.samplingLabel}"
            ImageOverlayText(
                text = if (source.isReady) imageDetails else source.displayMessage,
                description = if (source.isReady) "${layer.label} · $imageDetails · 点按全屏" else source.message,
                modifier = Modifier.align(Alignment.TopStart).padding(spacing.lg),
            )
            ViewerButton(
                text = "全屏查看",
                description = "打开全屏影像查看器",
                onClick = { fullscreen = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.sm),
            )
        }
        if (showLayers) {
            // These controls live outside the image so the canvas itself never gets inset or rounded.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .semantics { this.contentDescription = layerAvailabilityDescription(alignedMask, overlayLayer) },
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                ImageLayer.entries.forEach { option ->
                    ActionButton(
                        text = option.label,
                        onClick = { requestedLayer = option },
                        kind = if (layer == option) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                        enabled = layerAvailable(option, source, alignedMask, overlayLayer),
                        modifier = Modifier.semantics { selected = layer == option },
                    )
                }
            }
            layerNotices(source, alignedMask, overlayLayer, overlayImagePath.isNullOrBlank()).forEach { notice ->
                Text(
                    text = notice,
                    modifier = Modifier.clearAndSetSemantics {},
                    style = ClearViewTokens.typography.bodySmall,
                    color = colors.text2,
                )
            }
        }
    }
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            FullscreenViewer(
                source = source,
                mask = alignedMask,
                overlay = overlayLayer,
                overlayIsMask = overlayImagePath.isNullOrBlank(),
                layer = layer,
                onLayerChange = { requestedLayer = it },
                showLayers = showLayers,
                description = contentDescription,
                onDismiss = { fullscreen = false },
            )
        }
    }
}

/** Verifies local source/mask alignment for screens that gate metrics without drawing the image. */
@Composable
fun rememberMaskDisplayState(sourceImagePath: String?, maskImagePath: String?): MedicalImageLoadState {
    val source = rememberDecodedImage(sourceImagePath, isMask = false)
    val mask = rememberDecodedImage(maskImagePath, isMask = true)
    return alignedLayer(source, mask).state
}

@Composable
fun MedicalImageComparisonViewer(
    currentImagePath: String?,
    priorImagePath: String?,
    registered: Boolean,
    modifier: Modifier = Modifier,
    currentDescription: String = "本次影像",
    priorDescription: String = "既往影像",
) {
    val current = rememberDecodedImage(currentImagePath, isMask = false)
    val prior = rememberDecodedImage(priorImagePath, isMask = false)
    var fraction by remember(currentImagePath, priorImagePath) { mutableFloatStateOf(0.5f) }
    var fullscreen by remember(currentImagePath, priorImagePath) { mutableStateOf(false) }
    val aligned = registered && current.isReady && prior.isReady &&
        current.width == prior.width && current.height == prior.height
    val spacing = ClearViewTokens.spacing
    val colors = ClearViewTokens.colors
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Column(
                Modifier.fillMaxSize().background(colors.imageCanvas).statusBarsPadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(Modifier.fillMaxWidth().padding(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    ImageOverlayText(if (aligned) "配准对照" else "未配准，仅供目视", Modifier.weight(1f))
                    ViewerButton("关闭", "关闭全屏对照", { fullscreen = false })
                }
                ComparisonCanvas(current, prior, fraction, aligned, currentDescription, priorDescription, Modifier.weight(1f).fillMaxWidth())
                ComparisonControls(current, prior, fraction, { fraction = it }, Modifier.padding(spacing.sm))
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(colors.imageCanvas)) {
                ComparisonCanvas(current, prior, fraction, aligned, currentDescription, priorDescription, Modifier.fillMaxSize())
                ViewerButton(
                    "全屏对照", "打开全屏影像对照", { fullscreen = true },
                    Modifier.align(Alignment.BottomEnd).padding(spacing.sm),
                )
            }
            Text(
                if (aligned) "配准对照（模型参考）" else "未配准，仅供目视",
                style = ClearViewTokens.typography.bodySmall,
                color = colors.text2,
            )
            ComparisonControls(current, prior, fraction, { fraction = it })
        }
    }
}

@Composable
private fun ComparisonControls(
    current: DecodedImage,
    prior: DecodedImage,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ClearViewTokens.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (!current.isReady) ImageOverlayText("本次：${current.displayMessage}", description = "本次影像：${current.message}")
        if (!prior.isReady) ImageOverlayText("既往：${prior.displayMessage}", description = "既往影像：${prior.message}")
        Slider(
            value = fraction,
            onValueChange = onFractionChange,
            enabled = current.isReady && prior.isReady,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "对照分界，向右增加本次影像显示范围" },
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ActionButton("既往 100%", { onFractionChange(0f) }, kind = ButtonKind.SECONDARY, enabled = prior.isReady)
            ActionButton("各半", { onFractionChange(0.5f) }, kind = ButtonKind.SECONDARY, enabled = current.isReady && prior.isReady)
            ActionButton("本次 100%", { onFractionChange(1f) }, kind = ButtonKind.SECONDARY, enabled = current.isReady)
        }
    }
}

@Composable
private fun ComparisonCanvas(
    current: DecodedImage,
    prior: DecodedImage,
    fraction: Float,
    registered: Boolean,
    currentDescription: String,
    priorDescription: String,
    modifier: Modifier,
) {
    val colors = ClearViewTokens.colors
    val spacing = ClearViewTokens.spacing
    Box(modifier.background(colors.imageCanvas)) {
        Canvas(
            Modifier.fillMaxSize().clipToBounds().semantics {
                contentDescription = "$currentDescription 在左，$priorDescription 在右；" +
                    if (registered) "配准后的模型参考对照" else "未配准，双方独立等比显示，不代表解剖位置对应"
            },
        ) {
            val split = size.width * fraction
            clipRect(right = split) { drawComparisonImage(current) }
            clipRect(left = split) { drawComparisonImage(prior) }
            drawLine(colors.imageAnnotation, Offset(split, 0f), Offset(split, size.height), strokeWidth = 2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
            ImageOverlayText("本次", description = currentDescription)
            ImageOverlayText("既往", description = priorDescription)
        }
    }
}

private fun DrawScope.drawComparisonImage(image: DecodedImage) {
    val bitmap = image.bitmap ?: return
    if (!image.isReady || image.width <= 0 || image.height <= 0) return
    val fit = min(size.width / image.width, size.height / image.height)
    val target = IntSize(max(1, (image.width * fit).roundToInt()), max(1, (image.height * fit).roundToInt()))
    val offset = IntOffset(((size.width - target.width) / 2f).roundToInt(), ((size.height - target.height) / 2f).roundToInt())
    drawImage(bitmap, dstOffset = offset, dstSize = target, filterQuality = FilterQuality.High)
}

private enum class ImageLayer(val label: String) { SOURCE("原图"), MASK("标记"), OVERLAY("叠加") }

private data class DecodedImage(
    val state: MedicalImageLoadState,
    val message: String,
    val displayMessage: String,
    val bitmap: ImageBitmap? = null,
    val foreground: ImageBitmap? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    val isReady: Boolean get() = state == MedicalImageLoadState.READY || state == MedicalImageLoadState.EMPTY_MASK
    val samplingLabel: String get() = if (bitmap != null && (bitmap.width != width || bitmap.height != height)) {
        "降采样预览 ${bitmap.width} × ${bitmap.height}"
    } else {
        "原始分辨率预览"
    }
}

// One native decoder at a time, at most 4 MP per decoded bitmap, and no unbounded full-file buffers.
private const val MAX_DECODE_EDGE = 2048
private const val MAX_DECODE_PIXELS = 4L * 1024 * 1024
private const val MAX_FILE_BYTES = 64L * 1024 * 1024
private val decodePermit = Semaphore(1)

@Composable
private fun rememberDecodedImage(path: String?, isMask: Boolean): DecodedImage {
    // No key() here: remember slots after a movable group crash the runtime on path change (b/315855015).
    val image = remember(path, isMask) {
        mutableStateOf(
            DecodedImage(
                if (path.isNullOrBlank()) MedicalImageLoadState.MISSING else MedicalImageLoadState.LOADING,
                if (path.isNullOrBlank()) "未提供影像文件" else "正在读取影像…",
                if (path.isNullOrBlank()) "未提供影像" else "读取中",
            ),
        )
    }
    LaunchedEffect(path, isMask) {
        if (!path.isNullOrBlank()) {
            image.value = withContext(Dispatchers.IO) {
                decodePermit.withPermit { decodeLocalImage(path, isMask) }
            }
        }
    }
    return image.value
}

private suspend fun decodeLocalImage(path: String, isMask: Boolean): DecodedImage {
    try {
        coroutineContext.ensureActive()
        val file = File(path)
        if (!file.isFile) return DecodedImage(MedicalImageLoadState.MISSING, "本地影像文件不存在", "文件缺失")
        if (!file.canRead()) return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "本地影像文件不可读取", "无法读取")
        if (file.length() == 0L || file.length() > MAX_FILE_BYTES) {
            return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "文件为空或超出安全预览大小（64 MiB）", "空文件或过大（64 MiB）")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "无法解码此影像格式；此查看器不解析 DICOM", "格式无法解码")
        }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        // Never declare a sampled-away lesion an empty mask. Oversized masks are explicitly unavailable.
        if (isMask && (pixels > MAX_DECODE_PIXELS || max(bounds.outWidth, bounds.outHeight) > MAX_DECODE_EDGE)) {
            return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "标记超过 2048 px / 4 MP 预览限制；未降采样或推断为空", "尺寸超限（2048 px / 4 MP）")
        }
        var sample = 1
        while ((bounds.outWidth.toLong() + sample - 1) / sample > MAX_DECODE_EDGE ||
            (bounds.outHeight.toLong() + sample - 1) / sample > MAX_DECODE_EDGE || pixels / sample / sample > MAX_DECODE_PIXELS
        ) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeFile(path, options)
            ?: return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "影像解码失败；文件可能损坏", "解码失败")
        if (decoded.width > MAX_DECODE_EDGE || decoded.height > MAX_DECODE_EDGE ||
            decoded.width.toLong() * decoded.height > MAX_DECODE_PIXELS
        ) {
            decoded.recycle()
            return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "解码尺寸超出安全预览范围", "解码尺寸超限")
        }
        coroutineContext.ensureActive()
        val orientation = try {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val oriented = orientBitmap(decoded, orientation)
        if (oriented !== decoded) decoded.recycle()
        val swapped = orientation in setOf(
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270,
        )
        val width = if (swapped) bounds.outHeight else bounds.outWidth
        val height = if (swapped) bounds.outWidth else bounds.outHeight
        var foreground: Bitmap? = null
        var hasForeground = false
        if (isMask) {
            foreground = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
            val row = IntArray(oriented.width)
            for (y in 0 until oriented.height) {
                coroutineContext.ensureActive()
                oriented.getPixels(row, 0, oriented.width, 0, y, oriented.width, 1)
                for (x in row.indices) {
                    val pixel = row[x]
                    if ((pixel ushr 24) != 0 && (pixel and 0x00FFFFFF) != 0) {
                        hasForeground = true
                    } else {
                        row[x] = 0 // Only the real mask's zero background becomes transparent.
                    }
                }
                foreground.setPixels(row, 0, oriented.width, 0, y, oriented.width, 1)
            }
        }
        coroutineContext.ensureActive()
        return DecodedImage(
            state = if (isMask && !hasForeground) MedicalImageLoadState.EMPTY_MASK else MedicalImageLoadState.READY,
            message = if (isMask && !hasForeground) "有效空标记（无非零前景）；不表示正常或低风险" else "可用",
            displayMessage = if (isMask && !hasForeground) "空标记" else "",
            bitmap = oriented.asImageBitmap(),
            foreground = foreground?.asImageBitmap(),
            width = width,
            height = height,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: OutOfMemoryError) {
        return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "内存不足，无法安全预览此影像", "内存不足")
    } catch (_: Exception) {
        return DecodedImage(MedicalImageLoadState.DECODE_ERROR, "影像读取或解码失败", "读取失败")
    }
}

private fun orientBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
}

private fun alignedLayer(source: DecodedImage, layer: DecodedImage): DecodedImage = when {
    !layer.isReady -> layer
    source.state == MedicalImageLoadState.LOADING -> DecodedImage(MedicalImageLoadState.LOADING, "等待原图尺寸校验", "等待原图")
    !source.isReady -> DecodedImage(MedicalImageLoadState.MISALIGNED, "原图不可用，无法确认空间对齐", "原图不可用")
    layer.width != source.width || layer.height != source.height ->
        DecodedImage(MedicalImageLoadState.MISALIGNED, "尺寸与原图不一致，已禁用；未拉伸配准", "尺寸不符")
    else -> layer
}

private fun layerAvailable(layer: ImageLayer, source: DecodedImage, mask: DecodedImage, overlay: DecodedImage): Boolean =
    when (layer) {
        ImageLayer.SOURCE -> source.isReady
        // 全零空掩码（分割未检出病灶）时禁用标记/叠加图层，按钮置灰不可按。
        ImageLayer.MASK -> source.state == MedicalImageLoadState.READY && mask.state == MedicalImageLoadState.READY
        ImageLayer.OVERLAY -> source.state == MedicalImageLoadState.READY && overlay.state == MedicalImageLoadState.READY
    }

private fun layerAvailabilityDescription(mask: DecodedImage, overlay: DecodedImage): String =
    "标记：${mask.message}。叠加：${overlay.message}。模型标记不等同于病灶；缺失或空标记不能排除异常。"

private fun layerNotices(source: DecodedImage, mask: DecodedImage, overlay: DecodedImage, overlayIsMask: Boolean): List<String> {
    if (!source.isReady) return emptyList()
    return buildList {
        if (mask.state == MedicalImageLoadState.EMPTY_MASK) add(mask.displayMessage)
        else if (!mask.isReady) add("标记：${mask.displayMessage}")
        if (!overlayIsMask && !overlay.isReady) add("叠加：${overlay.displayMessage}")
    }
}

@Stable
private class ViewerSession {
    var scale by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(IntSize.Zero)
    var windowMode by mutableStateOf(false)
    var windowEnabled by mutableStateOf(false)
    var windowWidth by mutableFloatStateOf(255f)
    var windowLevel by mutableFloatStateOf(127.5f)
    var overlayAlpha by mutableFloatStateOf(0.5f)
    var toolbarVisible by mutableStateOf(true)
    var canvasInteracting by mutableStateOf(false)
    var toolbarInteracting by mutableStateOf(false)
    var focusedToolbars by mutableStateOf(emptySet<String>())
    val controlsFocused: Boolean get() = focusedToolbars.isNotEmpty()
    var activity by mutableLongStateOf(0L)

    fun wake() { toolbarVisible = true; activity = SystemClock.uptimeMillis() }

    fun fitSize(image: DecodedImage): Size {
        if (image.width <= 0 || image.height <= 0 || viewport.width == 0 || viewport.height == 0) return Size.Zero
        val fit = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        return Size(image.width * fit, image.height * fit)
    }

    fun boundPan(value: Offset, image: DecodedImage): Offset {
        val fit = fitSize(image)
        val boundX = max(0f, (fit.width * scale - viewport.width) / 2f)
        val boundY = max(0f, (fit.height * scale - viewport.height) / 2f)
        return Offset(value.x.coerceIn(-boundX, boundX), value.y.coerceIn(-boundY, boundY))
    }

    fun navigate(image: DecodedImage, focus: Offset, movement: Offset, zoom: Float) {
        if (!image.isReady || !zoom.isFinite() || zoom <= 0f) return
        val nextScale = (scale * zoom).coerceIn(1f, 8f)
        val ratio = nextScale / scale
        val relativeFocus = focus - Offset(viewport.width / 2f, viewport.height / 2f)
        val nextPan = (pan - relativeFocus) * ratio + relativeFocus + movement
        scale = nextScale
        pan = boundPan(nextPan, image)
        wake()
    }

    fun zoom(image: DecodedImage, factor: Float) = navigate(
        image, Offset(viewport.width / 2f, viewport.height / 2f), Offset.Zero, factor,
    )

    fun window(widthDelta: Float, levelDelta: Float) {
        windowEnabled = true
        windowWidth = (windowWidth + widthDelta).coerceIn(1f, 510f)
        windowLevel = (windowLevel + levelDelta).coerceIn(0f, 255f)
        wake()
    }

    fun reset() {
        scale = 1f
        pan = Offset.Zero
        windowMode = false
        windowEnabled = false
        windowWidth = 255f
        windowLevel = 127.5f
        overlayAlpha = 0.5f
        wake()
    }
}

@Composable
private fun FullscreenViewer(
    source: DecodedImage,
    mask: DecodedImage,
    overlay: DecodedImage,
    overlayIsMask: Boolean,
    layer: ImageLayer,
    onLayerChange: (ImageLayer) -> Unit,
    showLayers: Boolean,
    description: String,
    onDismiss: () -> Unit,
) {
    val session = remember { ViewerSession() }
    val colors = ClearViewTokens.colors
    val spacing = ClearViewTokens.spacing
    val accessibilityEnabled = rememberAccessibilityEnabled()
    val toolbarMotion = ClearViewTokens.motion.status
    val toolbarTimeout = ClearViewTokens.motion.toolbarTimeout
    // A pre-rendered overlay may already contain source gray values: do not pretend to window it.
    val canWindow = source.isReady && (layer == ImageLayer.SOURCE || (layer == ImageLayer.OVERLAY && overlayIsMask))
    val currentSource by rememberUpdatedState(source)
    val currentCanWindow by rememberUpdatedState(canWindow)
    LaunchedEffect(session.activity, session.canvasInteracting, session.toolbarInteracting, session.controlsFocused, accessibilityEnabled) {
        if (session.canvasInteracting || session.toolbarInteracting || session.controlsFocused || accessibilityEnabled) {
            session.toolbarVisible = true
        } else {
            delay(toolbarTimeout)
            session.toolbarVisible = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.imageCanvas)) {
        val maximumToolbarHeight = maxHeight * 0.6f
        ImageCanvas(
            source = source,
            mask = mask,
            overlay = overlay,
            overlayIsMask = overlayIsMask,
            layer = layer,
            description = description,
            session = session,
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { session.viewport = it; session.pan = session.boundPan(session.pan, currentSource) }
                .semantics {
                    onClick("显示影像工具栏") { session.wake(); true }
                    customActions = listOf(
                        CustomAccessibilityAction("放大影像") { session.zoom(currentSource, 1.25f); true },
                        CustomAccessibilityAction("缩小影像") { session.zoom(currentSource, 0.8f); true },
                        CustomAccessibilityAction("重置原始显示") { session.reset(); true },
                        CustomAccessibilityAction("向左平移") {
                            session.navigate(currentSource, Offset.Zero, Offset(-session.viewport.width / 4f, 0f), 1f); true
                        },
                        CustomAccessibilityAction("向右平移") {
                            session.navigate(currentSource, Offset.Zero, Offset(session.viewport.width / 4f, 0f), 1f); true
                        },
                        CustomAccessibilityAction("向上平移") {
                            session.navigate(currentSource, Offset.Zero, Offset(0f, -session.viewport.height / 4f), 1f); true
                        },
                        CustomAccessibilityAction("向下平移") {
                            session.navigate(currentSource, Offset.Zero, Offset(0f, session.viewport.height / 4f), 1f); true
                        },
                    )
                }
                .pointerInput(session) {
                    // This is the sole canvas gesture owner. Consume at Initial, including the first
                    // down, so neither a scroll ancestor nor another detector can steal navigation.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down.consume()
                        session.canvasInteracting = true
                        session.wake()
                        var totalMovement = Offset.Zero
                        var passedSlop = false
                        var hadMultiplePointers = false
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val stablePointers = event.changes.count { it.pressed && it.previousPressed }
                                val movement = event.calculatePan()
                                totalMovement += movement
                                if (totalMovement.getDistance() > viewConfiguration.touchSlop) passedSlop = true
                                if (stablePointers >= 2) {
                                    hadMultiplePointers = true
                                    passedSlop = true
                                    session.navigate(currentSource, event.calculateCentroid(useCurrent = false), movement, event.calculateZoom())
                                } else if (stablePointers == 1 && passedSlop && movement != Offset.Zero) {
                                    // Lifting one finger after a pinch must not accidentally change gray values.
                                    if (session.windowMode && currentCanWindow && !hadMultiplePointers) {
                                        session.window(
                                            movement.x * 510f / max(1, session.viewport.width),
                                            -movement.y * 255f / max(1, session.viewport.height),
                                        )
                                    } else {
                                        session.navigate(currentSource, event.calculateCentroid(useCurrent = false), movement, 1f)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            session.canvasInteracting = false
                            session.wake()
                        }
                    }
                },
        )
        AnimatedVisibility(
            visible = session.toolbarVisible,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            enter = fadeIn(tween(toolbarMotion)),
            exit = fadeOut(tween(toolbarMotion)),
        ) {
            Row(
                modifier = Modifier.background(colors.imageToolbar).statusBarsPadding()
                    .toolbarActivity(session, "top").padding(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                val imageDetails = if (!source.isReady) "" else buildString {
                    append("${source.width} × ${source.height} · ")
                    append(String.format(Locale.ROOT, "%.2f×", session.scale))
                    append("\n${source.samplingLabel}")
                    if (session.windowEnabled && canWindow) append("\nWW ${session.windowWidth.roundToInt()} / WL ${session.windowLevel.roundToInt()} · 像素灰阶调节")
                }
                ImageOverlayText(
                    text = imageDetails,
                    description = if (source.isReady) "${layer.label} · $imageDetails" else source.message,
                    modifier = Modifier.weight(1f),
                )
                ViewerButton("关闭", "关闭全屏影像查看器", onDismiss, useBackground = false)
            }
        }
        if (!source.isReady) {
            ImageOverlayText(
                text = source.displayMessage,
                description = source.message,
                modifier = Modifier.align(Alignment.Center).padding(spacing.lg),
            )
        }
        AnimatedVisibility(
            visible = session.toolbarVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(tween(toolbarMotion)),
            exit = fadeOut(tween(toolbarMotion)),
        ) {
            Column(
                modifier = Modifier.background(colors.imageToolbar).navigationBarsPadding()
                    .heightIn(max = maximumToolbarHeight).toolbarActivity(session, "bottom")
                    .verticalScroll(rememberScrollState()).padding(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (showLayers) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .semantics { contentDescription = layerAvailabilityDescription(mask, overlay) },
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        ImageLayer.entries.forEach { option ->
                            ViewerButton(
                                text = option.label,
                                description = "显示${option.label}图层",
                                onClick = { onLayerChange(option); session.wake() },
                                enabled = layerAvailable(option, source, mask, overlay),
                                selected = layer == option,
                                useBackground = false,
                            )
                        }
                    }
                    layerNotices(source, mask, overlay, overlayIsMask).forEach { notice ->
                        ImageOverlayText(notice, Modifier.clearAndSetSemantics {})
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ViewerButton("缩小", "缩小影像", { session.zoom(source, 0.8f) }, enabled = source.isReady && session.scale > 1f, useBackground = false)
                    ViewerButton("放大", "放大影像", { session.zoom(source, 1.25f) }, enabled = source.isReady && session.scale < 8f, useBackground = false)
                    ViewerButton("重置", "重置缩放、平移和像素灰阶预览", { session.reset() }, enabled = source.isReady, useBackground = false)
                    ViewerButton(
                        text = if (session.windowMode && canWindow) "切换平移" else "调节窗宽/窗位",
                        description = if (session.windowMode && canWindow) "切换为单指平移" else "切换为单指像素灰阶窗宽窗位调节",
                        onClick = { session.windowMode = !session.windowMode; session.wake() },
                        enabled = canWindow,
                        selected = session.windowMode && canWindow,
                        useBackground = false,
                    )
                }
                if (session.windowMode && canWindow) {
                    ImageOverlayText(
                        text = "横调窗宽·竖调窗位·双指缩放",
                        description = "单指：左右调窗宽，上下调窗位；双指：缩放和平移。仅改变预览，不修改原文件。",
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        ViewerButton("窗宽 −", "减小像素窗宽", { session.window(-16f, 0f) }, enabled = session.windowWidth > 1f, useBackground = false)
                        ViewerButton("窗宽 +", "增大像素窗宽", { session.window(16f, 0f) }, enabled = session.windowWidth < 510f, useBackground = false)
                        ViewerButton("窗位 −", "降低像素窗位", { session.window(0f, -8f) }, enabled = session.windowLevel > 0f, useBackground = false)
                        ViewerButton("窗位 +", "提高像素窗位", { session.window(0f, 8f) }, enabled = session.windowLevel < 255f, useBackground = false)
                    }
                } else {
                    ImageOverlayText(
                        text = "单指平移·双指缩放·点按唤醒",
                        description = "单指平移 · 双指缩放 · 点按恢复工具栏 · 重置恢复原始灰阶",
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        listOf(
                            "左移" to Offset(-session.viewport.width / 4f, 0f),
                            "右移" to Offset(session.viewport.width / 4f, 0f),
                            "上移" to Offset(0f, -session.viewport.height / 4f),
                            "下移" to Offset(0f, session.viewport.height / 4f),
                        ).forEach { (label, movement) ->
                            ViewerButton(
                                text = label,
                                description = "影像$label",
                                onClick = { session.navigate(source, Offset.Zero, movement, 1f) },
                                enabled = source.isReady && session.scale > 1f,
                                useBackground = false,
                            )
                        }
                    }
                }
                if (layer == ImageLayer.OVERLAY) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        ViewerButton("叠加 −", "降低真实叠加层不透明度", { session.overlayAlpha = (session.overlayAlpha - 0.1f).coerceIn(0f, 1f); session.wake() }, enabled = session.overlayAlpha > 0f, useBackground = false)
                        ImageOverlayText("${(session.overlayAlpha * 100).roundToInt()}%")
                        ViewerButton("叠加 +", "提高真实叠加层不透明度", { session.overlayAlpha = (session.overlayAlpha + 0.1f).coerceIn(0f, 1f); session.wake() }, enabled = session.overlayAlpha < 1f, useBackground = false)
                    }
                }
            }
        }
    }
}

private fun Modifier.toolbarActivity(session: ViewerSession, region: String): Modifier = this
    .onFocusChanged {
        session.focusedToolbars = if (it.hasFocus) session.focusedToolbars + region else session.focusedToolbars - region
        if (it.hasFocus) session.wake()
    }
    .focusGroup()
    .pointerInput(session) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    session.toolbarInteracting = event.changes.any { it.pressed }
                    session.wake()
                }
            }
        } finally {
            session.toolbarInteracting = false
        }
    }

@Composable
private fun rememberAccessibilityEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var enabled by remember(manager) { mutableStateOf(manager.isEnabled) }
    DisposableEffect(manager) {
        // Keep controls visible for the entire accessibility session, including TalkBack focus.
        // Compose input focus alone does not represent accessibility focus.
        val listener = AccessibilityManager.AccessibilityStateChangeListener { enabled = it }
        manager.addAccessibilityStateChangeListener(listener)
        enabled = manager.isEnabled
        onDispose { manager.removeAccessibilityStateChangeListener(listener) }
    }
    return enabled
}

@Composable
private fun ImageCanvas(
    source: DecodedImage,
    mask: DecodedImage,
    overlay: DecodedImage,
    overlayIsMask: Boolean,
    layer: ImageLayer,
    description: String,
    modifier: Modifier = Modifier,
    session: ViewerSession? = null,
) {
    val canvas = ClearViewTokens.colors.imageCanvas
    val lesionTint = ClearViewTokens.colors.critical
    val zoomFactor = session?.scale ?: 1f
    val pan = session?.pan ?: Offset.Zero
    val alpha = session?.overlayAlpha ?: 0.5f
    var imageVisible by remember(source.bitmap) { mutableStateOf(false) }
    LaunchedEffect(source.bitmap) { imageVisible = source.isReady }
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageVisible) 1f else 0f,
        animationSpec = tween(ClearViewTokens.motion.image),
        label = "image-load",
    )
    val windowFilter = remember(session?.windowEnabled, session?.windowWidth, session?.windowLevel) {
        if (session?.windowEnabled == true) pixelGrayWindowFilter(session.windowWidth, session.windowLevel) else null
    }
    Canvas(
        modifier.background(canvas).clipToBounds().semantics {
            contentDescription = "$description，${layer.label}。${if (source.isReady) "初始完整适应画布；缩放 ${String.format(Locale.ROOT, "%.2f", zoomFactor)} 倍" else source.message}"
        },
    ) {
        if (!source.isReady || source.width == 0 || source.height == 0) return@Canvas
        val fit = min(size.width / source.width, size.height / source.height)
        val destinationSize = IntSize(max(1, (source.width * fit).roundToInt()), max(1, (source.height * fit).roundToInt()))
        val destinationOffset = IntOffset(((size.width - destinationSize.width) / 2f).roundToInt(), ((size.height - destinationSize.height) / 2f).roundToInt())
        withTransform({
            translate(pan.x, pan.y)
            scale(zoomFactor, zoomFactor, center)
        }) {
            when (layer) {
                ImageLayer.SOURCE -> source.bitmap?.let {
                    drawImage(it, dstOffset = destinationOffset, dstSize = destinationSize, alpha = imageAlpha, colorFilter = windowFilter, filterQuality = FilterQuality.High)
                }
                ImageLayer.MASK -> mask.bitmap?.let {
                    drawImage(it, dstOffset = destinationOffset, dstSize = destinationSize, alpha = imageAlpha, filterQuality = FilterQuality.None)
                }
                ImageLayer.OVERLAY -> {
                    source.bitmap?.let {
                        drawImage(it, dstOffset = destinationOffset, dstSize = destinationSize, alpha = imageAlpha, colorFilter = if (overlayIsMask) windowFilter else null, filterQuality = FilterQuality.High)
                    }
                    (if (overlayIsMask) overlay.foreground else overlay.bitmap)?.let {
                        drawImage(
                            it, dstOffset = destinationOffset, dstSize = destinationSize, alpha = alpha * imageAlpha,
                            colorFilter = if (overlayIsMask) ColorFilter.tint(lesionTint) else null,
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
        }
    }
}

/** Deliberately opt-in luminance conversion of decoded 8-bit pixels, not a DICOM VOI/HU transform. */
private fun pixelGrayWindowFilter(width: Float, level: Float): ColorFilter {
    val gain = 255f / width.coerceIn(1f, 510f)
    val bias = 127.5f - level.coerceIn(0f, 255f) * gain
    val r = 0.2126f * gain
    val g = 0.7152f * gain
    val b = 0.0722f * gain
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        r, g, b, 0f, bias,
        r, g, b, 0f, bias,
        r, g, b, 0f, bias,
        0f, 0f, 0f, 1f, 0f,
    )))
}

@Composable
private fun ImageOverlayText(text: String, modifier: Modifier = Modifier, description: String = text) {
    val colors = ClearViewTokens.colors
    val type = ClearViewTokens.typography
    val style = type.imageOverlay
    val displayText = buildAnnotatedString {
        append(text)
        Regex("[^\\x00-\\x7F]+").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontFamily = type.chinese), match.range.first, match.range.last + 1)
        }
    }
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        // A centered 2px stroke leaves a 1px outline outside the glyph.
        Text(displayText, modifier = Modifier.clearAndSetSemantics {}, style = style.copy(drawStyle = Stroke(width = 2f)), color = colors.imageCanvas)
        Text(displayText, modifier = Modifier.clearAndSetSemantics {}, style = style, color = colors.imageText)
    }
}

@Composable
private fun ViewerButton(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    useBackground: Boolean = true,
) {
    val colors = ClearViewTokens.colors
    val spacing = ClearViewTokens.spacing
    Box(
        modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (useBackground) colors.imageToolbar else colors.transparent, ClearViewTokens.shapes.none)
            .border(1.dp, if (selected) colors.imageAnnotation else colors.imageText, ClearViewTokens.shapes.none)
            .semantics { contentDescription = if (enabled) description else "$description，不可用"; this.selected = selected }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = spacing.md, vertical = spacing.sm)),
        contentAlignment = Alignment.Center,
    ) {
        ImageOverlayText(
            text = text,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
