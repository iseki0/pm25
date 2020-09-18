package com.example.pm25

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.util.*

private val UART_SERVICE = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val UART_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
private val UART_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

class Device(val info: DeviceInfo) {
    val pm25 = MutableLiveData(-1)
    val battery = MutableLiveData(-1)
    val connectStatus = MutableLiveData(DeviceConnectStatus.DISCONNECTED)
}

class SensorService : Service() {
    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter!! }
//    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }


    private val devices by lazy {
        runCatching {
            jsonMapper.readValue(configPath.readText(), DeviceInfoConfigListType).map(::Device)
                .toMutableList()
        }.getOrElse { mutableListOf() }
    }

    val deviceList: List<Device>
        get() = devices.toList()

    private val devicesInfo: List<DeviceInfo>
        get() = devices.map { it.info }

    var needSave = false
        private set

    fun disconnect(device: Device) {

    }

    fun connect(device: Device) {
        check(BluetoothAdapter.checkBluetoothAddress(device.info.address)) { "device address invalid" }
        val dev = bluetoothAdapter.getRemoteDevice(device.info.address)!!
        if (device.connectStatus.value != DeviceConnectStatus.DISCONNECTED) {
            Log.e("service", "device.connect != DISCONNECTED")
            return
        }
        Log.d("service", "try connect")
        dev.connectGatt(this, false, object : BluetoothGattCallback() {
            lateinit var rx: BluetoothGattCharacteristic
            lateinit var tx: BluetoothGattCharacteristic
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.d("service", "${rx == characteristic}")
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("service", "device connected, status = $status")
                        val service = gatt.getService(UART_SERVICE)!!
                        rx = service.getCharacteristic(UART_RX)
                        tx = service.getCharacteristic(UART_TX)
                        gatt.setCharacteristicNotification(rx, true)
                        device.connectStatus.postValue(DeviceConnectStatus.CONNECTED)

                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("service", "device disconnect, status = $status:")
                        device.connectStatus.postValue(DeviceConnectStatus.DISCONNECTED)
                    }
                    else -> error("unknown bluetooth connect status: $newState")
                }
            }

        })
    }

    fun addDevice(info: DeviceInfo) {
        if (devices.any { it.info.address == info.address })
            error("the address is exists")
        devices.add(Device(info))
        needSave = true
    }

    fun delDevice(address: String): Boolean {
        devices.find { it.info.address == address }?.let(devices::remove)
        needSave = true
        return false
    }

    fun saveConfig() {
        configPath.writeText(jsonMapper.writeValueAsString(devicesInfo))
        needSave = false
    }

    override fun onCreate() {
        Log.d("service", "onCreate")
        devices
    }

    override fun onDestroy() {
        Log.d("service", "onDestroy")
        if (needSave) saveConfig()
    }

    class LocalBinder(val service: SensorService) : Binder()

    val localBinder = LocalBinder(this)

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}

enum class DeviceConnectStatus {
    CONNECTED, DISCONNECTED, CONNECTING, ERROR
}

sealed class DeviceMessage(val type: Type) {
    enum class Type(val value: Byte) {
        SHUTDOWN(0x01),
        MEASUREMENT_INTERVAL(0x08),
        SET_RTC(0x09),
        NO_MORE_HISTORY(0x0a),
        HISTORY(0x0b),
        MEASUREMENT_ENABLED(0x16),
        UPDATE(0x50),
        VERSION(0x54),
        BATTERY(0x04),
        RUNTIME(0x05),
        SENSOR(0x06),
        MEASUREMENT(0x07),
    }
}

class Shutdown : DeviceMessage(Type.SHUTDOWN)
class MeasurementInterval(val interval: Int) : DeviceMessage(Type.MEASUREMENT_INTERVAL)
class SetRTC(val timestamp: Int) : DeviceMessage(Type.SET_RTC)
class NoMoreHistory(unknown: Byte) : DeviceMessage(Type.NO_MORE_HISTORY)
class History(val pm25: Int, val timestamp: Int) : DeviceMessage(Type.HISTORY)
class MeasurementEnabled(val enabled: Boolean) : DeviceMessage(Type.MEASUREMENT_ENABLED)
class VersionPacket(val minor: Int, val major: Int) : DeviceMessage(Type.VERSION)

class Battery(val capacity: Int, val charging: Boolean) : DeviceMessage(Type.BATTERY)
class HardwareRuntime(val run: Int, val boot: Int) : DeviceMessage(Type.RUNTIME)
class SensorData(val pm25: Int, val recordDate: Int, val unknown: Byte, val currentDate: Int) :
    DeviceMessage(Type.SENSOR)

class MeasurementSetup(val interval: Int, val enabled: Boolean) : DeviceMessage(Type.MEASUREMENT)


fun parsePacket(buf: ByteArray) {
    check(buf[0] == 0xaa.toByte()) { "magic header error: ${buf[0]}" }
    // checksum
    buf.foldIndexed(0) { index, acc, byte ->
        if (index == buf.lastIndex) {
            check((acc and 0xff).toByte() == byte) { "checksum error, expect: ${acc and 0xff}, get: $byte" }
            0
        } else {
            acc + byte
        }
    }
    buf.apply {
        when (buf[1]) {
            b(0x01) -> Shutdown()
            b(0x08) -> MeasurementInterval(int(2 size 4))
            b(0x09) -> SetRTC(int(2 size 4))
            b(0x0a) -> NoMoreHistory(buf[2])
            b(0x0b) -> History(int(2 size 2), int(6 size 4))
            b(0x16) -> MeasurementEnabled(int(2 size 1) == 1)
            b(0x50) -> when (buf[2]) {
                b(0x04) -> Battery(int(3 size 1), int(6 size 1) == 1)
                b(0x05) -> HardwareRuntime(int(3 size 4), int(7 size 4))
                b(0x06) -> SensorData(int(3 size 2), int(7 size 4), get(0x0b), int(0x0c size 4))
                b(0x07) -> MeasurementSetup(int(3 size 2), int(5 size 1) == 1)
            }
            b(0x54) -> VersionPacket(int(2 size 2), int(4 size 2))
        }
    }
}

private inline fun b(i: Int) = i.toByte()
private inline fun ByteArray.int(range: IntRange) = bytes2Int(sliceArray(range))
private inline fun bytes2Int(bs: ByteArray) = bs.fold(0) { acc, byte -> (acc shl 8) + byte }
private inline infix fun Int.size(size: Int) = this until this + size
