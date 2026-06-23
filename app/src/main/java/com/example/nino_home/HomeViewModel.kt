package com.example.nino_home

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class BotService(
    val serviceName: String,
    val serviceType: String,
    val host: String,
    val hostName: String?,
    val port: Int,
    val txt: Map<String, String> = emptyMap(),
)

data class BotStatus(
    val deviceName: String,
    val wifiSsid: String,
    val volume: Int,
    val firmware: String,
)

data class HomeUiState(
    val discoveredBots: List<BotService> = emptyList(),
    val isDiscoveringBots: Boolean = false,
    val discoveryError: String? = null,
    val selectedBot: BotService? = null,
    val isLoadingStatus: Boolean = false,
    val botStatus: BotStatus? = null,
    val statusError: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val PRIMARY_BOT_SERVICE = "_nino._tcp."
        private val DISCOVERY_TYPES = listOf(PRIMARY_BOT_SERVICE)
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val nsdManager = application.getSystemService(NsdManager::class.java)
    private val discoveryListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()

    fun startBotDiscovery() {
        if (discoveryListeners.isNotEmpty()) return

        _uiState.update {
            it.copy(
                discoveredBots = emptyList(),
                isDiscoveringBots = true,
                discoveryError = null,
            )
        }

        DISCOVERY_TYPES.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    _uiState.update {
                        it.copy(
                            isDiscoveringBots = discoveryListeners.isNotEmpty(),
                            discoveryError = "mDNS start failed for $serviceType ($errorCode)",
                        )
                    }
                    safeStop(serviceType, this)
                }

                override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                    _uiState.update {
                        it.copy(
                            isDiscoveringBots = discoveryListeners.isNotEmpty(),
                            discoveryError = "mDNS stop failed for $serviceType ($errorCode)",
                        )
                    }
                    safeStop(serviceType, this)
                }

                override fun onDiscoveryStarted(type: String) {
                    _uiState.update { it.copy(isDiscoveringBots = true, discoveryError = null) }
                }

                override fun onDiscoveryStopped(type: String) {
                    discoveryListeners.remove(serviceType)
                    _uiState.update { it.copy(isDiscoveringBots = discoveryListeners.isNotEmpty()) }
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType != serviceType) return
                    resolveService(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    _uiState.update { state ->
                        state.copy(
                            discoveredBots = state.discoveredBots.filterNot {
                                it.serviceType == serviceInfo.serviceType &&
                                    it.serviceName == serviceInfo.serviceName
                            },
                        )
                    }
                }
            }

            discoveryListeners[serviceType] = listener
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        discoveryError = "mDNS discovery error for $serviceType: ${e.message}",
                    )
                }
                discoveryListeners.remove(serviceType)
            }
        }

        _uiState.update { it.copy(isDiscoveringBots = discoveryListeners.isNotEmpty()) }
    }

    fun stopBotDiscovery() {
        if (discoveryListeners.isEmpty()) return
        val snapshot = discoveryListeners.toMap()
        snapshot.forEach { (serviceType, listener) ->
            safeStop(serviceType, listener)
        }
        _uiState.update { it.copy(isDiscoveringBots = false) }
    }

    fun clearDiscoveryError() {
        _uiState.update { it.copy(discoveryError = null) }
    }

    fun clearStatusError() {
        _uiState.update { it.copy(statusError = null) }
    }

    fun fetchBotStatus(bot: BotService) {
        _uiState.update {
            it.copy(
                selectedBot = bot,
                isLoadingStatus = true,
                botStatus = null,
                statusError = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { requestStatus(bot) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { status ->
                        state.copy(
                            selectedBot = bot,
                            isLoadingStatus = false,
                            botStatus = status,
                            statusError = null,
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            selectedBot = bot,
                            isLoadingStatus = false,
                            botStatus = null,
                            statusError = err.message ?: "Failed to get device status",
                        )
                    },
                )
            }
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (_uiState.value.discoveryError == null) {
                        _uiState.update {
                            it.copy(discoveryError = "Could not resolve bot service ($errorCode)")
                        }
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val hostAddress = serviceInfo.host?.hostAddress ?: return
                    val hostName = serviceInfo.host?.hostName
                    val txtRecords = serviceInfo.attributes.mapValues { entry ->
                        String(entry.value, StandardCharsets.UTF_8)
                    }
                    val bot = BotService(
                        serviceName = serviceInfo.serviceName,
                        serviceType = serviceInfo.serviceType,
                        host = hostAddress,
                        hostName = hostName,
                        port = serviceInfo.port,
                        txt = txtRecords,
                    )
                    viewModelScope.launch {
                        _uiState.update { state ->
                            if (state.discoveredBots.any {
                                    it.host == bot.host &&
                                        it.port == bot.port &&
                                        it.serviceType == bot.serviceType
                                }
                            ) {
                                state
                            } else {
                                state.copy(discoveredBots = state.discoveredBots + bot)
                            }
                        }
                    }
                }
            },
        )
    }

    private fun safeStop(serviceType: String, listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
            // Some listeners may already be stopped by the system.
        } finally {
            discoveryListeners.remove(serviceType)
            _uiState.update { it.copy(isDiscoveringBots = discoveryListeners.isNotEmpty()) }
        }
    }

    override fun onCleared() {
        stopBotDiscovery()
        super.onCleared()
    }

    private fun requestStatus(bot: BotService): BotStatus {
        val url = URL("http://${bot.host}:${bot.port}/status")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("Status request failed ($code)")
            }
            val payload = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            BotStatus(
                deviceName = json.optString("device_name", "ESP Assistant"),
                wifiSsid = json.optString("wifi_ssid", "Unknown"),
                volume = json.optInt("volume", -1),
                firmware = json.optString("firmware", "Unknown"),
            )
        } finally {
            conn.disconnect()
        }
    }
}
