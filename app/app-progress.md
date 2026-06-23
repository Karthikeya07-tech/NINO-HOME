# App Progress Report

## Current Status

The provisioning workflow is implemented in both firmware and Android app for ESP32-P4 BLE Wi-Fi setup.

## Completed

- BLE-based Wi-Fi provisioning is defined and working flow is documented.
- Android app BLE flow is implemented end-to-end:
  - Scan and connect to `PROV_NINO`
  - Discover provisioning service `4facb001-5a2e-4b7c-9e1f-a8d3e6f20401`
  - Enable status notifications on `4facb001-5a2e-4b7c-9e1f-a8d3e6f20405`
  - Write SSID to `...0402`
  - Write password to `...0403`
  - Write command byte `0x01` to `...0404` (apply and connect)
  - Parse status JSON and surface `state/connected/ip` in UI
- GATT service/characteristic layout is finalized:
  - SSID write characteristic
  - Password write characteristic
  - Apply/connect command characteristic
  - Status read/notify characteristic
- Android provisioning sequence is documented end-to-end.
- Connection status model is defined (`idle`, `connecting`, `connected`, `failed`).
- Wi-Fi credentials persistence in NVS (`wifi_cfg` namespace) is documented.
- Auto-reconnect on reboot is in place via saved STA config.
- HTTP fallback provisioning over Soft AP is available.
- Build requirements and target setup notes for ESP32-P4 are documented.
- Serial console commands for Wi-Fi control/status are available.
- Reset/recovery process for Wi-Fi config is documented.

## In Place as Fallback / Recovery

- Soft AP fallback (`ESP32_P4_CAM`) with HTTP config/status APIs.
- Reset path via NVS erase / flash erase / reprovisioning.

## Integration Readiness

- Android BLE client can proceed using the provided UUIDs and operation order.
- Firmware exposes enough status data (`state`, `connected`, `ip`) for app-side UX.
- App currently performs BLE writes in-order with a GATT operation queue to avoid write races.

## Validation Snapshot

- Local Android build check passed (`:app:assembleDebug`).
- Manual expected behavior:
  - `state=1` after sending command means connecting
  - `state=2` with `connected=true` and `ip` means success
  - `state=3` means credentials failed or AP unreachable

## Troubleshooting (BLE Provisioning)

- Connected over BLE but no status updates:
  - Confirm notifications are enabled on status char `...0405` before sending command `0x01`.
  - Verify app has `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permissions granted.
  - Reconnect and retry provisioning (disconnect, reconnect, send again).
- Write fails on SSID/password/command:
  - Ensure SSID is <= 32 UTF-8 bytes and password is <= 64 UTF-8 bytes.
  - Make sure writes are sent in-order: SSID -> password -> command.
  - Confirm target device exposes service `4facb001-5a2e-4b7c-9e1f-a8d3e6f20401`.
- `state=3` (failed) after command:
  - Recheck Wi-Fi password and router SSID spelling/case.
  - Check router band/security compatibility (2.4 GHz support, WPA mode).
  - Check if AP is reachable from robot location and not hidden/filtered.
- Connected but app cannot open robot URL:
  - Ensure phone is on the same home Wi-Fi network as the robot.
  - Verify returned `ip` is not empty and ping/retry after a few seconds.
  - If still unreachable, reprovision or use HTTP fallback via Soft AP.

## Remaining / Next Suggested Milestones

- Validate provisioning on multiple Android versions/devices.
- Add robustness tests (invalid creds, router unavailable, reconnect loops).
- Add retry/backoff behavior visibility in status payload (if not already implemented).
- Add security hardening review for provisioning endpoints and BLE exposure.
- Add OTA/update readiness checks for production flow.
- Add concise release checklist and known issues section.

## Overall Progress Estimate

- **Core provisioning feature:** ~80–90% complete
- **Production hardening/testing/docs polish:** remaining ~10–20%

SAVE FOR LATER  
App is ready either way, but if you plan multi-bot same LAN, firmware should move to unique hostname.