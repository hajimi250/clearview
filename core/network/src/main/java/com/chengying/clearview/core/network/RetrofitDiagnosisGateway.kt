package com.chengying.clearview.core.network

import com.chengying.clearview.core.model.AcquisitionInfo
import com.chengying.clearview.core.model.isValidDiagnosisId
import com.chengying.clearview.core.model.validatedForSubmission
import java.io.File
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitDiagnosisGateway(
    private val apiService: SegmentApiService,
    private val installId: String,
) : DiagnosisGateway {
    override suspend fun analyzeImage(
        imagePath: String,
        topK: Int,
        clientRecordId: String,
        acquisition: AcquisitionInfo,
    ): AnalyzeResponse {
        acquisition.validatedForSubmission()
        val imageFile = File(imagePath)
        require(imageFile.exists()) { "Image file does not exist: $imagePath" }

        val imageRequestBody = imageFile.asRequestBody("image/jpeg".toMediaType())
        val imagePart = MultipartBody.Part.createFormData(
            name = "image",
            filename = imageFile.name,
            body = imageRequestBody,
        )

        return apiService.analyze(
            image = imagePart,
            topK = topK.toString().toPlainPart(),
            ttaMode = CLASSIFIER_TTA_MODE.toPlainPart(),
            segmentMode = SEGMENT_MODE.toPlainPart(),
            clientRecordId = clientRecordId.toPlainPart(),
            installId = installId,
            projection = requireNotNull(acquisition.projection) { "Missing projection declaration" }.toPlainPart(),
            acquiredAt = acquisition.acquiredAt?.toPlainPart(),
            completenessConfirmed = (acquisition.completenessConfirmed == true).toString().toPlainPart(),
        ).toDomain()
    }

    override suspend fun downloadMaskByRelativeUrl(
        recordId: String,
        relativeUrl: String,
    ): ByteArray {
        require(isValidDiagnosisId(recordId)) { "Invalid record identifier" }
        require(relativeUrl == "/api/v1/records/$recordId/mask.png") { "Invalid record mask path" }
        return apiService.downloadByRelativeUrl(relativeUrl, installId).use { it.bytes() }
    }

    override suspend fun downloadSourceImageByRelativeUrl(
        recordId: String,
        relativeUrl: String,
    ): ByteArray {
        require(isValidDiagnosisId(recordId)) { "Invalid record identifier" }
        require(relativeUrl == "/api/v1/records/$recordId/source.jpg") { "Invalid record source path" }
        return apiService.downloadByRelativeUrl(relativeUrl, installId).use { it.bytes() }
    }

    override suspend fun deleteRecord(
        recordId: String,
    ) {
        apiService.deleteRecord(recordId, installId)
    }

    override suspend fun listRecords(
        limit: Int,
        offset: Int,
    ): AnalyzeRecordsResponse = apiService.listRecords(limit, offset, installId).toDomain()
}

fun createRetrofitDiagnosisGateway(
    installId: String,
    debugHostConfig: DebugHostConfig = DefaultDebugHostConfig(),
): DiagnosisGateway {
    val backend = debugHostConfig.normalizedBaseUrl().toHttpUrl()
    // 动态地址：每次请求按当前保存的配置解析 origin，路径与查询保持不变；
    // 保存新地址后无需重建网关或重启应用。
    fun currentBackend(): HttpUrl? = runCatching { debugHostConfig.normalizedBaseUrl().toHttpUrl() }.getOrNull()
    fun rewriteToCurrentBackend(request: okhttp3.Request): okhttp3.Request {
        val current = currentBackend() ?: return request
        return request.newBuilder()
            .url(request.url.newBuilder().scheme(current.scheme).host(current.host).port(current.port).build())
            .build()
    }
    fun requireBackendOrigin(request: okhttp3.Request) {
        if (request.header("X-Install-Id") != null) {
            val current = currentBackend()
            if (current == null ||
                request.url.scheme != current.scheme || request.url.host != current.host || request.url.port != current.port) {
                throw IOException("Refusing to send the installation identifier outside the configured backend")
            }
        }
    }
    val retrofit = Retrofit.Builder()
        .baseUrl(backend)
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = rewriteToCurrentBackend(chain.request())
                    requireBackendOrigin(request)
                    chain.proceed(
                        request
                            .newBuilder()
                            .header("ngrok-skip-browser-warning", "true")
                            .build()
                    )
                }
                .addNetworkInterceptor { chain ->
                    // OkHttp invokes network interceptors again for redirects.
                    requireBackendOrigin(chain.request())
                    chain.proceed(chain.request())
                }
                .retryOnConnectionFailure(true)
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create(createDiagnosisGson()))
        .build()

    return RetrofitDiagnosisGateway(
        apiService = retrofit.create(SegmentApiService::class.java),
        installId = installId,
    )
}

private fun String.toPlainPart(): RequestBody =
    toRequestBody("text/plain".toMediaType())

private const val CLASSIFIER_TTA_MODE = "flip_zoom"
private const val SEGMENT_MODE = "accurate"
