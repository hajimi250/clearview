package com.chengying.clearview

import android.content.Context
import com.chengying.clearview.core.network.DefaultDebugHostConfig
import com.chengying.clearview.core.network.DiagnosisGateway
import com.chengying.clearview.core.network.DynamicDebugHostConfig
import com.chengying.clearview.core.network.InstallIdentityStore
import com.chengying.clearview.core.network.createRetrofitDiagnosisGateway
import com.chengying.clearview.core.network.createServerAssistantGateway

class AppGraph(appContext: Context) {
    private val context = appContext.applicationContext

    val serviceEndpointConfigStore: ServiceEndpointConfigStore by lazy { ServiceEndpointConfigStore(context) }
    val serviceEndpointConfig: ServiceEndpointConfig by lazy {
        // 没填过地址时调试版照旧走 USB 回环；填过则以填的为准，同一入口支持局域网。
        ServiceEndpointConfig(backendBaseUrl = currentBackendBaseUrl())
    }

    /** 当前生效的服务地址：始终取最近一次保存值，网关逐请求解析，保存后无需重启应用。 */
    fun currentBackendBaseUrl(): String =
        serviceEndpointConfigStore.loadOrNull()?.backendBaseUrl
            ?: if (BuildConfig.DEBUG) USB_BACKEND_BASE_URL else DefaultDebugHostConfig().baseUrl

    val debugHostConfig: DynamicDebugHostConfig by lazy { DynamicDebugHostConfig(::currentBackendBaseUrl) }
    val diagnosisGateway: DiagnosisGateway by lazy {
        createRetrofitDiagnosisGateway(
            installId = InstallIdentityStore(context).getOrCreate(),
            debugHostConfig = debugHostConfig,
        )
    }
    val assistantGateway by lazy {
        createServerAssistantGateway(debugHostConfig = debugHostConfig)
    }
}

private const val USB_BACKEND_BASE_URL = "http://127.0.0.1:8000/"
