package com.example.nino_home.ble

import java.util.UUID

object ProvGattUuids {
    const val ADVERTISED_NAME = "PROV_NINO"

    val SERVICE: UUID = UUID.fromString("4facb001-5a2e-4b7c-9e1f-a8d3e6f20401")
    // Preferred/documented layout:
    // 4facb001-...0402 / 0403 / 0404 / 0405
    val SSID: UUID = UUID.fromString("4facb001-5a2e-4b7c-9e1f-a8d3e6f20402")
    val PASSWORD: UUID = UUID.fromString("4facb001-5a2e-4b7c-9e1f-a8d3e6f20403")
    val COMMAND: UUID = UUID.fromString("4facb001-5a2e-4b7c-9e1f-a8d3e6f20404")
    val STATUS: UUID = UUID.fromString("4facb001-5a2e-4b7c-9e1f-a8d3e6f20405")

    // Alternate firmware layout seen in field:
    // 4facb002-...0401 / 0401 / 0401 / 0401
    val SSID_ALT: UUID = UUID.fromString("4facb002-5a2e-4b7c-9e1f-a8d3e6f20401")
    val PASSWORD_ALT: UUID = UUID.fromString("4facb003-5a2e-4b7c-9e1f-a8d3e6f20401")
    val COMMAND_ALT: UUID = UUID.fromString("4facb004-5a2e-4b7c-9e1f-a8d3e6f20401")
    val STATUS_ALT: UUID = UUID.fromString("4facb005-5a2e-4b7c-9e1f-a8d3e6f20401")

    val SSID_CANDIDATES: List<UUID> = listOf(SSID, SSID_ALT)
    val PASSWORD_CANDIDATES: List<UUID> = listOf(PASSWORD, PASSWORD_ALT)
    val COMMAND_CANDIDATES: List<UUID> = listOf(COMMAND, COMMAND_ALT)
    val STATUS_CANDIDATES: List<UUID> = listOf(STATUS, STATUS_ALT)

    const val CMD_APPLY_AND_CONNECT: Byte = 0x01

    const val STATE_IDLE = 0
    const val STATE_CONNECTING = 1
    const val STATE_CONNECTED = 2
    const val STATE_FAILED = 3
}
