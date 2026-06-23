package com.example.nino_home

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nino_home.ble.BleWifiProvisioner
import com.example.nino_home.ble.ProvGattUuids
import com.example.nino_home.ble.WifiProvisionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProvisionUiState(
    val ssid: String = "",
    val password: String = "",
    val logLines: List<String> = emptyList(),
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val selectedDevice: DiscoveredDevice? = null,
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val isProvisioning: Boolean = false,
    val lastStatus: WifiProvisionStatus? = null,
    val robotIp: String? = null,
    val error: String? = null,
)

data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val device: BluetoothDevice,
)

class ProvisionViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProvisionUiState())
    val uiState: StateFlow<ProvisionUiState> = _uiState.asStateFlow()

    private val provisionerListener = object : BleWifiProvisioner.Listener {
        override fun onLog(message: String) {
            viewModelScope.launch { appendLog(message) }
        }

        override fun onScanResult(device: BluetoothDevice, name: String?) {
            viewModelScope.launch {
                val entry = DiscoveredDevice(device.address, name, device)
                _uiState.update { state ->
                    if (state.discoveredDevices.any { it.address == entry.address }) state
                    else state.copy(discoveredDevices = state.discoveredDevices + entry)
                }
            }
        }

        override fun onConnected() {
            viewModelScope.launch {
                _uiState.update { it.copy(isConnected = true, isScanning = false, error = null) }
            }
        }

        override fun onDisconnected() {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(isConnected = false, isProvisioning = false)
                }
            }
        }

        override fun onStatus(status: WifiProvisionStatus) {
            viewModelScope.launch {
                _uiState.update { it.copy(lastStatus = status) }
            }
        }

        override fun onError(message: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(error = message, isProvisioning = false) }
                appendLog(message)
            }
        }

        override fun onFinished(success: Boolean, ip: String?) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isProvisioning = false,
                        robotIp = if (success) ip else null,
                    )
                }
            }
        }
    }

    private val provisioner = BleWifiProvisioner(application, provisionerListener)

    init {
        _uiState.update { it.copy(ssid = readCurrentWifiSsid().orEmpty()) }
    }

    fun updateSsid(value: String) = _uiState.update { it.copy(ssid = value, error = null) }

    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun startScan() {
        _uiState.update {
            it.copy(
                isScanning = true,
                discoveredDevices = emptyList(),
                selectedDevice = null,
                error = null,
                robotIp = null,
                lastStatus = null,
            )
        }
        provisioner.startScan()
    }

    fun stopScan() {
        provisioner.stopScan()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun selectDevice(entry: DiscoveredDevice) {
        stopScan()
        _uiState.update { it.copy(selectedDevice = entry, error = null) }
        provisioner.connect(entry.device)
    }

    fun provision() {
        val state = _uiState.value
        if (!state.isConnected) {
            _uiState.update { it.copy(error = "Connect to ${ProvGattUuids.ADVERTISED_NAME} first") }
            return
        }
        _uiState.update {
            it.copy(
                isProvisioning = true,
                error = null,
                robotIp = null,
                lastStatus = null,
            )
        }
        provisioner.provision(state.ssid, state.password)
    }

    fun disconnect() {
        provisioner.disconnect()
        _uiState.update {
            it.copy(
                isConnected = false,
                isProvisioning = false,
                selectedDevice = null,
            )
        }
    }

    override fun onCleared() {
        provisioner.disconnect()
        super.onCleared()
    }

    private fun appendLog(message: String) {
        _uiState.update { it.copy(logLines = (it.logLines + message).takeLast(40)) }
    }

    @Suppress("DEPRECATION")
    private fun readCurrentWifiSsid(): String? {
        val wifi = getApplication<Application>().getSystemService(WifiManager::class.java) ?: return null
        val raw = wifi.connectionInfo?.ssid ?: return null
        return raw.trim('"').takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    }

}
