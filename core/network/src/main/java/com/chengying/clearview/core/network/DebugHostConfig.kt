package com.chengying.clearview.core.network

/**
 * Debug Host 配置入口。
 * 当前优先读取 core:network BuildConfig 中的 DEBUG_BACKEND_BASE_URL，
 * 便于通过 local.properties / Gradle 属性快速切换 ngrok 地址。
 */
interface DebugHostConfig {
    val baseUrl: String
}

data class DefaultDebugHostConfig(
    override val baseUrl: String = BuildConfig.DEBUG_BACKEND_BASE_URL,
) : DebugHostConfig

/** 每次请求时解析最新地址；保存新的服务地址后无需重建网关或重启应用即可生效。 */
class DynamicDebugHostConfig(
    private val provider: () -> String,
) : DebugHostConfig {
    override val baseUrl: String get() = provider()
}

fun DebugHostConfig.normalizedBaseUrl(): String =
    if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
