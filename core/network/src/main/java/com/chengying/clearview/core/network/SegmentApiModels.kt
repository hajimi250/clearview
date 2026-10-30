package com.chengying.clearview.core.network

import com.google.gson.annotations.SerializedName

data class SegmentResponseDto(
    @SerializedName("requestId")
    val requestId: String,
    @SerializedName("model")
    val model: String,
    @SerializedName("modelVersion")
    val modelVersion: String,
    @SerializedName("inputSize")
    val inputSize: Int,
    @SerializedName("maskUrl")
    val maskUrl: String,
    @SerializedName("lesionPixels")
    val lesionPixels: Int,
    @SerializedName("imageWidth")
    val imageWidth: Int,
    @SerializedName("imageHeight")
    val imageHeight: Int,
    @SerializedName("imagePixels")
    val imagePixels: Int,
    @SerializedName("lesionImageRatio")
    val lesionImageRatio: Float,
    @SerializedName("inferenceMs")
    val inferenceMs: Int,
    @SerializedName("disclaimer")
    val disclaimer: String,
)

fun SegmentResponseDto.toDomain(): SegmentResponse = SegmentResponse(
    requestId = requestId,
    model = model,
    modelVersion = modelVersion,
    inputSize = inputSize,
    maskUrl = maskUrl,
    lesionPixels = lesionPixels,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    imagePixels = imagePixels,
    lesionImageRatio = lesionImageRatio,
    inferenceMs = inferenceMs,
    disclaimer = disclaimer,
)
