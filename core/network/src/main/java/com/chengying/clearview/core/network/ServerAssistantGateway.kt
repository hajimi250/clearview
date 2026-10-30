package com.chengying.clearview.core.network

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private data class ServerAssistantRequest(
    @SerializedName("messages")
    val messages: List<ServerAssistantMessage>,
)

private data class ServerAssistantMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String,
)

private data class ServerAssistantResponse(
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("model")
    val model: String? = null,
)

private interface ServerAssistantService {
    @POST("api/v1/assistant/chat")
    suspend fun chat(@Body request: ServerAssistantRequest): ServerAssistantResponse
}

private class RetrofitServerAssistantGateway(
    private val service: ServerAssistantService,
) : AssistantGateway {
    override suspend fun chat(messages: List<AssistantMessage>): AssistantReply {
        require(messages.isNotEmpty()) { "Messages must not be empty." }
        val response = try {
            service.chat(
                ServerAssistantRequest(
                    messages = messages.map {
                        ServerAssistantMessage(role = it.role, content = it.content)
                    },
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            throw throwable.toAssistantRequestException()
        }
        val text = response.text?.trim().orEmpty()
        if (text.isEmpty()) {
            throw IllegalStateException("助手未返回内容，请稍后重试。")
        }
        return AssistantReply(
            text = text,
            model = response.model?.takeIf { it.isNotBlank() } ?: "服务端",
        )
    }
}

private fun Throwable.toAssistantRequestException(): IllegalStateException {
    if (this is HttpException) {
        val detail = this.readAssistantErrorDetail()
        val statusCode = this.code()
        val message = when (statusCode) {
            400, 422 -> detail ?: "助手请求无效，请重试。"
            401, 403 -> "助手服务鉴权失败，请联系管理员。"
            404 -> "助手接口不可用，请确认服务端已更新并重启。"
            503 -> detail ?: "助手服务未配置或暂时不可用。"
            504 -> detail ?: "助手响应超时，请稍后重试。"
            in 500..599 -> detail ?: "助手服务暂时不可用，请稍后重试。"
            else -> detail ?: "助手请求失败（HTTP $statusCode）。"
        }
        return IllegalStateException(message, this)
    }
    if (this is SocketTimeoutException) {
        return IllegalStateException("助手响应超时，请稍后重试。", this)
    }
    if (this is IOException) {
        return IllegalStateException("无法连接助手服务，请检查服务地址与网络。", this)
    }
    return IllegalStateException(
        message?.takeIf { it.isNotBlank() } ?: "助手请求失败，请稍后重试。",
        this,
    )
}

private fun HttpException.readAssistantErrorDetail(): String? = runCatching {
    val raw = response()?.errorBody()?.string().orEmpty()
    val element = JsonParser.parseString(raw)
    if (!element.isJsonObject) return@runCatching null
    val detail = element.asJsonObject.get("detail") ?: return@runCatching null
    if (detail.isJsonPrimitive) detail.asString.takeIf { it.isNotBlank() } else null
}.getOrNull()

fun createServerAssistantGateway(
    debugHostConfig: DebugHostConfig = DefaultDebugHostConfig(),
): AssistantGateway {
    val retrofit = Retrofit.Builder()
        .baseUrl(debugHostConfig.normalizedBaseUrl())
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    // 动态地址：每次请求按当前保存的配置重写 origin，保存新地址后无需重启应用。
                    val request = chain.request()
                    val current = runCatching { debugHostConfig.normalizedBaseUrl().toHttpUrl() }.getOrNull()
                    val rewritten = if (current != null) {
                        request.newBuilder()
                            .url(request.url.newBuilder().scheme(current.scheme).host(current.host).port(current.port).build())
                            .build()
                    } else {
                        request
                    }
                    chain.proceed(
                        rewritten
                            .newBuilder()
                            .header("ngrok-skip-browser-warning", "true")
                            .build(),
                    )
                }
                .retryOnConnectionFailure(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(100, TimeUnit.SECONDS)
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    return RetrofitServerAssistantGateway(
        service = retrofit.create(ServerAssistantService::class.java),
    )
}
