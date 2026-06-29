# Nino Home

`Nino Home` is an Android app (Jetpack Compose) that helps you:

- discover already configured Nino devices on local Wi-Fi (mDNS),
- control discovered devices (status, volume, rename),
- open device camera stream and visuals screen,
- provision new devices to Wi-Fi over BLE.

---

## Tech Stack

- Kotlin + Jetpack Compose
- Android SDK (min SDK 31, target SDK 36)
- BLE GATT for provisioning
- mDNS (`NsdManager`) for local device discovery
- HTTP for device control APIs

---

## Complete App Flow

### 1) App Launch

1. App starts from `MainActivity`.
2. `HomeScreen` is loaded.
3. Default tab is **Create New**.
4. App starts mDNS discovery for `_nino._tcp.` services.

---

### 2) Home Tabs

The bottom navigation has 3 tabs:

- **My Music**: placeholder screen.
- **Create New**: discover and open existing devices on home Wi-Fi.
- **Configure**: BLE Wi-Fi provisioning for new devices.

---

### 3) Existing Device Flow (Create New)

1. App scans local network for `_nino._tcp.` services.
2. Services are resolved to host/IP/port/TXT metadata.
3. Discovered devices are shown as cards.
4. User taps a device card.
5. App requests device status: `GET http://<ip>:<port>/status`.
6. Device detail screen opens.

---

### 4) Device Detail Flow

On the detail screen:

- **Status view**: device name, connected Wi-Fi SSID, volume, firmware.
- **Volume control**:
  - UI updates instantly.
  - App sends debounced `POST /volume` with JSON `{ "volume": <0-100> }`.
- **Rename device**:
  - App sends `POST /device/name` with JSON `{ "device_name": "<new-name>" }`.
- **Long press on device name** opens **Advanced Options**.
- **Device Camera** opens stream at `http://<ip>:<port>/stream`.
- **Play Zone** opens visuals selection screen.

---

### 5) Advanced Options Flow

Advanced screen shows:

- editable device name,
- IP address,
- service type,
- mDNS host,
- status URL,
- device tag (from TXT records),
- firmware version,
- touch sensor ON/OFF toggle (UI state currently local only).

---

### 6) New Device Provisioning Flow (Configure)

1. User opens **Configure** tab.
2. App asks for required permissions:
   - `BLUETOOTH_SCAN`
   - `BLUETOOTH_CONNECT`
   - `ACCESS_WIFI_STATE`
   - `ACCESS_FINE_LOCATION`
3. User taps **Scan**.
4. App scans for provisioning target (advertised as `PROV_NINO`, matching provisioning service UUID).
5. User selects a BLE device.
6. App connects GATT, discovers provisioning service, enables status notifications.
7. User enters SSID/password and taps **Provision Wi-Fi**.
8. App writes in order:
   1. SSID characteristic
   2. Password characteristic
   3. Command byte `0x01` (apply/connect)
9. App receives status updates (connecting/success/failure).
10. On success, UI shows device IP and URL hint.

---

## Network & Protocol Contracts

### HTTP Endpoints Used by App

- `GET /status`
- `POST /volume`
- `POST /device/name`

### BLE Provisioning Service

- Advertised name: `PROV_NINO`
- Service UUID: `4facb001-5a2e-4b7c-9e1f-a8d3e6f20401`
- Status JSON example: `{"state":2,"connected":true,"ip":"192.168.1.42"}`

For detailed GATT characteristic mapping and fallback provisioning notes, see `firmware.md`.

---

## Setup & Run

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK / Emulator or physical Android device (Android 12+)
- Bluetooth enabled on test phone

### Run Steps

1. Open this project in Android Studio.
2. Let Gradle sync complete.
3. Connect an Android phone or start an emulator.
4. Run the app (`app` module).

Or from terminal:

```powershell
.\gradlew.bat :app:assembleDebug
```

---

## Project Structure

- `app/src/main/java/com/example/nino_home/MainActivity.kt` - app entry.
- `app/src/main/java/com/example/nino_home/ui/HomeScreen.kt` - main navigation + device detail UI.
- `app/src/main/java/com/example/nino_home/HomeViewModel.kt` - mDNS discovery + HTTP control.
- `app/src/main/java/com/example/nino_home/ui/ProvisionScreen.kt` - provisioning UI + permissions.
- `app/src/main/java/com/example/nino_home/ProvisionViewModel.kt` - provisioning state orchestration.
- `app/src/main/java/com/example/nino_home/ble/BleWifiProvisioner.kt` - BLE scan/connect/GATT writes/status.
- `firmware.md` - firmware-side provisioning protocol and fallback details.

---

## Current Status

- Core discovery + control + provisioning flow is implemented.
- **My Music** is still placeholder UI.
- Touch sensor toggle in advanced screen is currently local UI state only.

