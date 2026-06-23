package com.example.nino_home.ble

import org.json.JSONObject

data class WifiProvisionStatus(
    val state: Int,
    val connected: Boolean,
    val ip: String?,
) {
    val isSuccess: Boolean get() = state == ProvGattUuids.STATE_CONNECTED && connected
    val isFailed: Boolean get() = state == ProvGattUuids.STATE_FAILED

    companion object {
        fun parse(json: String): WifiProvisionStatus? = try {
            val o = JSONObject(json.trim())
            WifiProvisionStatus(
                state = o.optInt("state", ProvGattUuids.STATE_IDLE),
                connected = o.optBoolean("connected", false),
                ip = o.optString("ip").takeIf { it.isNotEmpty() },
            )
        } catch (_: Exception) {
            null
        }
    }
}
