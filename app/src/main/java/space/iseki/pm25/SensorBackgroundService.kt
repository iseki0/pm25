package space.iseki.pm25

import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder

class SensorBackgroundService : Service() {


    private val devices = mutableMapOf<String, SensorDevice>()
    private val deviceList = mutableListOf<SensorDevice>()
    private val watchers = mutableListOf<(DeviceStatusUpdate) -> Unit>()
    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter!! }

    val deviceInfos: List<DeviceStatusUpdate>
        get() = deviceList.map { it.toUpdate() }

    /**
     * @return invoke it to stop watching
     */
    fun watch(handler: (DeviceStatusUpdate) -> Unit): () -> Unit {
        if (handler !in watchers) {
            watchers.add(handler)
        }
        return { watchers.remove(handler) }
    }

    fun writeCommandToDevice(command: DeviceCommand, address: String) {
        val d = devices[address] ?: error("device: $address not exists")
        d.writeCommand(command)
    }

    fun connectDevice(address: String) {
        val d = devices[address] ?: error("device: $address not exists")
        d.connect(this, bluetoothAdapter)
    }

    fun disconnectDevice(address: String) {
        val d = devices[address] ?: error("device: $address not exists")
        d.disconnect()
    }

    fun addDevice(device: SensorDeviceInfo) {
        check(device.address !in devices) { "address conflict: ${device.address}" }
        val d = SensorDevice(device) { notifyUpdate(it) }
        deviceList.add(d)
        devices[device.address] = d
    }

    fun removeDevice(address: String) {
        val a = devices.remove(address) ?: return
        a.disconnect()
        deviceList.remove(a)
    }

    fun updateDevice(info: SensorDeviceInfo) {
        (devices[info.address]
            ?: error("device with address: ${info.address} not exists")).info = info
    }


    fun getDevice(address: String) = devices[address]?.info

    private fun notifyUpdate(update: DeviceStatusUpdate) {
        watchers.forEach { it.invoke(update) }
    }

    fun storeDeviceList() {
        writeDeviceInfoToStorage(deviceList.map { it.info })
    }

    fun storeDeviceRecentlyStatus() {
        writeDeviceLastStatusToStorage(deviceList.map {
            DeviceLastStatus(
                address = it.info.address,
                time = it.lastUpdate,
                pm25 = it.pm25,
                battery = it.battery,
                charging = it.charging,
            )
        })
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
            ) { notifyUpdate(it) }
        }.let {
            deviceList.clear()
            deviceList.addAll(it)
            devices.clear()
            it.forEach { devices[it.info.address] = it }
        }
    }


    class LocalBinder(a: SensorBackgroundService) : BaseLocalBinder<SensorBackgroundService>(a)

    override fun onBind(intent: Intent?): IBinder? = LocalBinder(this)
}

enum class DeviceConnectStatus {
    CONNECTED, DISCONNECTED, CONNECTING, ERROR
}




