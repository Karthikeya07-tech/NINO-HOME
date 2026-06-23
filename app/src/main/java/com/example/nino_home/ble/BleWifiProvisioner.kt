package com.example.nino_home.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android client for the ESP32-P4 BLE Wi-Fi provisioning GATT service (see firmware.md).
 */
class BleWifiProvisioner(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onLog(message: String)
        fun onScanResult(device: BluetoothDevice, name: String?)
        fun onConnected()
        fun onDisconnected()
        fun onStatus(status: WifiProvisionStatus)
        fun onError(message: String)
        fun onFinished(success: Boolean, ip: String?)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private val operationQueue = ArrayDeque<() -> Boolean>()
    private val operationInFlight = AtomicBoolean(false)
    private var scanning = false

    @SuppressLint("MissingPermission")
    fun startScan() {
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            listener.onError("Bluetooth is off or unavailable")
            return
        }
        if (scanning) return
        scanning = true
        listener.onLog("Scanning for ${ProvGattUuids.ADVERTISED_NAME}…")
        Log.d(TAG, "startScan: scanning for ${ProvGattUuids.SERVICE}")

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(ProvGattUuids.SERVICE))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bt.bluetoothLeScanner.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        listener.onLog("Connecting to ${device.address}…")
        Log.d(TAG, "connect: ${device.address}")
        gatt?.close()
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun provision(ssid: String, password: String) {
        val trimmedSsid = ssid.trim()
        if (trimmedSsid.isEmpty()) {
            listener.onError("SSID is required")
            return
        }
        if (trimmedSsid.toByteArray(StandardCharsets.UTF_8).size > 32) {
            listener.onError("SSID is too long (max 32 bytes UTF-8)")
            return
        }
        if (password.toByteArray(StandardCharsets.UTF_8).size > 64) {
            listener.onError("Password is too long (max 64 bytes UTF-8)")
            return
        }
        Log.d(
            TAG,
            "provision: ssidBytes=${trimmedSsid.toByteArray(StandardCharsets.UTF_8).size}, passwordBytes=${password.toByteArray(StandardCharsets.UTF_8).size}",
        )

        val g = gatt
        val service = g?.getService(ProvGattUuids.SERVICE)
        if (g == null || service == null) {
            listener.onError("Not connected — scan and connect to the device first")
            return
        }

        val ssidChar = findCharacteristic(service, ProvGattUuids.SSID_CANDIDATES)
        val passChar = findCharacteristic(service, ProvGattUuids.PASSWORD_CANDIDATES)
        val cmdChar = findCharacteristic(service, ProvGattUuids.COMMAND_CANDIDATES)
        statusCharacteristic = findCharacteristic(service, ProvGattUuids.STATUS_CANDIDATES)

        if (ssidChar == null || passChar == null || cmdChar == null || statusCharacteristic == null) {
            listener.onError("Provisioning service characteristics not found")
            Log.e(TAG, "provision: missing one or more characteristics")
            return
        }
        Log.d(TAG, "provision: all characteristics resolved")

        operationQueue.clear()
        listener.onLog("Writing credentials and applying…")

        operationQueue.add {
            listener.onLog("Writing SSID to device…")
            Log.d(TAG, "write: SSID -> ${ProvGattUuids.SSID}")
            writeCharacteristic(g, ssidChar, trimmedSsid.toByteArray(StandardCharsets.UTF_8))
        }
        operationQueue.add {
            listener.onLog("Writing password to device…")
            Log.d(TAG, "write: PASSWORD -> ${ProvGattUuids.PASSWORD}")
            writeCharacteristic(
                g,
                passChar,
                password.toByteArray(StandardCharsets.UTF_8),
            )
        }
        operationQueue.add {
            listener.onLog("Sending apply command (0x01)…")
            Log.d(TAG, "write: COMMAND(0x01) -> ${ProvGattUuids.COMMAND}")
            writeCharacteristic(g, cmdChar, byteArrayOf(ProvGattUuids.CMD_APPLY_AND_CONNECT))
        }

        drainQueue()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        statusCharacteristic = null
        operationQueue.clear()
        operationInFlight.set(false)
    }

    @SuppressLint("MissingPermission")
    private fun enableStatusNotifications(gatt: BluetoothGatt, statusChar: BluetoothGattCharacteristic): Boolean {
        gatt.setCharacteristicNotification(statusChar, true)
        val cccd = statusChar.getDescriptor(
            UUID_CLIENT_CHARACTERISTIC_CONFIG,
        ) ?: return false
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        Log.d(TAG, "enableStatusNotifications: writing CCCD on ${statusChar.uuid}")
        return gatt.writeDescriptor(cccd)
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        return gatt.writeCharacteristic(characteristic)
    }

    private fun findCharacteristic(
        service: android.bluetooth.BluetoothGattService,
        candidates: List<java.util.UUID>,
    ): BluetoothGattCharacteristic? {
        for (uuid in candidates) {
            val ch = service.getCharacteristic(uuid)
            if (ch != null) return ch
        }
        return null
    }

    private fun drainQueue() {
        if (!operationInFlight.compareAndSet(false, true)) return
        val next = operationQueue.poll()
        if (next == null) {
            operationInFlight.set(false)
            return
        }
        if (!next()) {
            operationInFlight.set(false)
            listener.onError("GATT write failed to start")
        }
    }

    private fun completeOperation() {
        operationInFlight.set(false)
        drainQueue()
    }

    private fun postStatusFromBytes(value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        val json = String(value, StandardCharsets.UTF_8)
        Log.d(TAG, "status notify/read raw: $json")
        val status = WifiProvisionStatus.parse(json)
        if (status != null) {
            mainHandler.post {
                listener.onStatus(status)
                when {
                    status.isSuccess -> {
                        listener.onLog("Connected — IP ${status.ip ?: "unknown"}")
                        listener.onFinished(true, status.ip)
                    }
                    status.isFailed -> {
                        listener.onError("Device reported Wi-Fi connection failed")
                        listener.onFinished(false, null)
                    }
                    status.state == ProvGattUuids.STATE_CONNECTING -> {
                        listener.onLog("Device is connecting to Wi-Fi…")
                    }
                }
            }
        } else {
            val msg = json.trim()
            if (msg.isNotEmpty()) {
                mainHandler.post {
                    if (msg.equals("ok", ignoreCase = true)) {
                        listener.onLog("Device ACK received: OK")
                    } else {
                        listener.onLog("Device message: $msg")
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord
            val name = record?.deviceName ?: device.name
            val hasService = record?.serviceUuids?.any { it.uuid == ProvGattUuids.SERVICE } == true
            val matchesName = name == ProvGattUuids.ADVERTISED_NAME
            if (!hasService && !matchesName) return
            mainHandler.post { listener.onScanResult(device, name ?: ProvGattUuids.ADVERTISED_NAME) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            mainHandler.post { listener.onError("BLE scan failed (code $errorCode)") }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    mainHandler.post { listener.onLog("Connected — discovering services…") }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    mainHandler.post {
                        listener.onDisconnected()
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            listener.onError("Disconnected (status $status)")
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post { listener.onError("Service discovery failed ($status)") }
                Log.e(TAG, "onServicesDiscovered failed: $status")
                return
            }
            val discovered = gatt.services.orEmpty()
            Log.d(TAG, "discovered services count=${discovered.size}")
            discovered.forEach { svc ->
                Log.d(TAG, "service: ${svc.uuid}")
                svc.characteristics.orEmpty().forEach { ch ->
                    Log.d(TAG, "  char: ${ch.uuid} props=${ch.properties}")
                }
            }
            val service = gatt.getService(ProvGattUuids.SERVICE)
            val statusChar = service?.let { findCharacteristic(it, ProvGattUuids.STATUS_CANDIDATES) }
            if (service == null || statusChar == null) {
                mainHandler.post { listener.onError("Provisioning GATT service not found on device") }
                Log.e(
                    TAG,
                    "service/STATUS characteristic not found on device; expected service=${ProvGattUuids.SERVICE}, statusCandidates=${ProvGattUuids.STATUS_CANDIDATES}",
                )
                return
            }
            Log.d(TAG, "service found: ${service.uuid}; statusChar found: ${statusChar.uuid}")
            statusCharacteristic = statusChar
            if (!enableStatusNotifications(gatt, statusChar)) {
                mainHandler.post { listener.onError("Failed to enable status notifications") }
                Log.e(TAG, "failed to start CCCD write for status notifications")
                return
            }
            mainHandler.post {
                listener.onConnected()
                listener.onLog("Ready — enter Wi-Fi credentials and tap Provision")
            }
            @SuppressLint("MissingPermission")
            gatt.readCharacteristic(statusChar)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post { listener.onError("Failed to subscribe to status ($status)") }
                Log.e(TAG, "onDescriptorWrite failed for ${descriptor.uuid}: $status")
            } else {
                Log.d(TAG, "onDescriptorWrite success for ${descriptor.uuid}")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (characteristic.uuid == ProvGattUuids.STATUS && status == BluetoothGatt.GATT_SUCCESS) {
                postStatusFromBytes(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            @Suppress("DEPRECATION")
            if (characteristic.uuid == ProvGattUuids.STATUS && status == BluetoothGatt.GATT_SUCCESS) {
                postStatusFromBytes(characteristic.value)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post {
                    listener.onError("Write failed for ${characteristic.uuid} ($status)")
                    listener.onFinished(false, null)
                }
                Log.e(TAG, "onCharacteristicWrite failed for ${characteristic.uuid}: $status")
                operationInFlight.set(false)
                return
            }
            Log.d(TAG, "onCharacteristicWrite success for ${characteristic.uuid}")
            completeOperation()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == ProvGattUuids.STATUS) {
                postStatusFromBytes(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            if (characteristic.uuid == ProvGattUuids.STATUS) {
                postStatusFromBytes(characteristic.value)
            }
        }
    }

    companion object {
        private const val TAG = "NinoBleProvisioner"
        private val UUID_CLIENT_CHARACTERISTIC_CONFIG =
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
