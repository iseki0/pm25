package com.example.pm25

import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder

class SensorBackgroundService : Service() {


    private val devices = mutableMapOf<String, SensorDevice>()
    private val deviceList = mutableListOf<SensorDevice>()
    private val watcher = mutableListOf<(DeviceStatusUpdate) -> Unit>()
    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter!! }

    val deviceInfos: List<DeviceStatusUpdate>
        get() = deviceList.map { it.toUpdate() }

    /**
     * @return invoke it to stop watching
     */
    fun watch(handler: (DeviceStatusUpdate) -> Unit): () -> Unit {
        TODO()
        return { watcher.remove(handler) }
    }


    fun connectDevice(address: String) {
        TODO()
    }

    fun addDevice(device: SensorDeviceInfo) {
        check(device.address !in devices) { "address conflict: ${device.address}" }
        val d = SensorDevice(device)
        deviceList.add(d)
        devices[device.address] = d
    }

    fun removeDevice(address: String) {
        val a = devices.remove(address) ?: return
        deviceList.remove(a)
    }

    fun updateDevice(info: SensorDeviceInfo) {
        (devices[info.address]
            ?: error("device with address: ${info.address} not exists")).info = info
    }


    fun getDevice(address: String) = devices[address]?.info

    private fun notifyUpdate(update: DeviceStatusUpdate) {
        TODO()
    }

    fun storeDeviceList() {
        TODO()
    }

    fun storeDeviceRecentlyStatus() {
        TODO()
    }

    private fun initialization() {
        val recently = loadDeviceLastStatusFromStorage().associateBy { it.address }
        loadDeviceInfoFromStorage().map { info ->
            val recently = recently[info.address]
            SensorDevice(
                info,
                recently?.pm25 ?: 0,
                recently?.battery ?: 0,
                recently?.charging ?: false
            )
        }.let {
            deviceList.clear()
            deviceList.addAll(it)
            devices.clear()
            it.forEach { devices[it.info.address] = it }
        }
    }


    class LocalBinder(val a: SensorBackgroundService) : BaseLocalBinder<SensorBackgroundService>(a)

    override fun onBind(intent: Intent?): IBinder? = LocalBinder(this)
}

enum class DeviceConnectStatus {
    CONNECTED, DISCONNECTED, CONNECTING, ERROR
}

fun SensorDevice.isConnected() = this.connectStatus == DeviceConnectStatus.CONNECTED


