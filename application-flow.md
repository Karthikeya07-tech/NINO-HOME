# Nino Home Application Flow and Features

This document explains how the Android app works end-to-end: what users see, what the app does behind the scenes, and which features are currently implemented.

## 1) What the App Does

`Nino Home` is an Android app for:

- provisioning a new device to home Wi-Fi over BLE,
- discovering already-provisioned devices on local Wi-Fi via mDNS,
- viewing device status,
- controlling volume,
- renaming the device,
- viewing advanced device info.

The app starts in `MainActivity` and renders `HomeScreen` using Jetpack Compose.

## 2) Main User Journey

The app has 3 bottom tabs:

- **My Music**: placeholder screen.
- **Create New**: discover bots on the current Wi-Fi and open bot details.
- **Configure**: BLE Wi-Fi provisioning flow for new devices.

Default tab on launch is **Create New**.

## 3) Complete Flow (Step by Step)

### A. App Launch

1. `MainActivity` loads `HomeScreen`.
2. `HomeScreen` defaults to **Create New**.
3. `HomeViewModel.startBotDiscovery()` starts mDNS discovery for `_nino._tcp.`.

### B. Existing Device Discovery (Create New tab)

1. App discovers LAN services using Android `NsdManager`.
2. Found services are resolved to host + port + TXT records.
3. Each resolved bot is shown as a card.
4. User taps a bot card to open bot details.

### C. Bot Detail Screen

1. App requests `GET http://<bot-ip>:<port>/status`.
2. UI shows:
   - device name,
   - connected Wi-Fi SSID,
   - current volume,
   - firmware version (in advanced options).
3. User can drag/tap volume control:
   - local UI updates immediately,
   - app sends debounced `POST /volume` with `{ "volume": <0-100> }`.
4. User can rename:
   - app sends `POST /device/name` with `{ "device_name": "<new-name>" }`,
   - UI updates on success.
5. Long-press on device name opens **Advanced Options**.

### D. Advanced Options Screen

Shows:

- editable device name,
- current IP address,
- firmware version,
- touch sensor ON/OFF toggle (currently UI state only).

### E. New Device Provisioning (Configure tab)

1. User opens **Configure** tab (`ProvisionScreen`).
2. App asks for required permissions:
   - `BLUETOOTH_SCAN`,
   - `BLUETOOTH_CONNECT`,
   - `ACCESS_WIFI_STATE`.
3. User taps **Scan**:
   - app scans BLE for provisioning service / advertised device (`PROV_NINO`).
4. User selects a discovered device:
   - app connects GATT,
   - discovers provisioning service,
   - enables notifications on status characteristic.
5. User enters SSID + password and taps **Provision Wi-Fi**.
6. App performs ordered GATT writes:
   1. SSID characteristic,
   2. Password characteristic,
   3. Command characteristic (`0x01` apply/connect).
7. App receives status notifications (`connecting`, `connected`, `failed`).
8. On success, app shows robot IP and target URL hint.

## 4) Internal Data / State Flow

### Home flow state (`HomeViewModel`)

- `discoveredBots`: LAN bots from mDNS.
- `isDiscoveringBots`: active discovery indicator.
- `selectedBot`: currently opened bot.
- `botStatus`: latest status payload.
- `statusError` / `discoveryError`: surfaced network/discovery errors.
- `isLoadingStatus`, `isUpdatingName`: in-flight UI states.

Network endpoints used in home flow:

- `GET /status`
- `POST /volume`
- `POST /device/name`

### Provision flow state (`ProvisionViewModel`)

- BLE scan list + selected device.
- Wi-Fi credentials (`ssid`, `password`).
- connection/provisioning booleans.
- provisioning status events from BLE notifications.
- final `robotIp`.
- rolling operation log (last 40 lines).

### BLE engine (`BleWifiProvisioner`)

- scans with service UUID/name filtering,
- connects via `connectGatt`,
- enables status notifications (CCCD),
- executes a write queue to avoid GATT write race conditions,
- parses status JSON and emits completion callbacks.

## 5) Current Features

- mDNS discovery of `_nino._tcp.` bots on local network.
- Bot list with host/service metadata.
- Live status fetch for selected bot.
- Volume control with low-latency debounced network update.
- Device rename from detail and advanced screens.
- BLE Wi-Fi provisioning flow with guided status feedback.
- Automatic initial SSID prefill from current phone Wi-Fi.
- Snackbar-based transient UX feedback for scan/connect/provision states.
- Error handling for BLE, mDNS, HTTP, and validation failures.

## 6) Protocol and Integration Assumptions

- Provision target advertises provisioning GATT service and supports:
  - SSID write,
  - password write,
  - apply/connect command,
  - status read/notify.
- Already-provisioned bots expose HTTP endpoints for status/control.
- Bot service is discoverable via mDNS type `_nino._tcp.`.
- Phone and device must be on the same Wi-Fi for HTTP control.

## 7) Known Gaps / In-Progress Areas

- **My Music** tab is placeholder UI.
- **Touch Sensor** toggle in advanced options is local-only (not persisted/sent yet).
- No explicit retry/backoff UX for failed status/rename/volume requests.
- No authentication on local HTTP control endpoints inside app flow.

## 8) File-Level Map (for maintainers)

- `app/src/main/java/com/example/nino_home/MainActivity.kt` -> app entry point.
- `app/src/main/java/com/example/nino_home/ui/HomeScreen.kt` -> main tabs + bot detail UI.
- `app/src/main/java/com/example/nino_home/HomeViewModel.kt` -> mDNS + bot HTTP operations.
- `app/src/main/java/com/example/nino_home/ui/ProvisionScreen.kt` -> BLE provision UI + permissions.
- `app/src/main/java/com/example/nino_home/ProvisionViewModel.kt` -> provision state orchestration.
- `app/src/main/java/com/example/nino_home/ble/BleWifiProvisioner.kt` -> BLE scanning/GATT/provision operations.
- `firmware.md` -> firmware-side provisioning protocol details.

