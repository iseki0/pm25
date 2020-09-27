package space.iseki.pm25

import android.bluetooth.*
import android.content.Context
import android.util.Log

data class DeviceStatusUpdate(
    val info: SensorDeviceInfo,
    val connectStatus: DeviceConnectStatus,
    val pm25: Int,
    val lastUpdate: Int,
    val battery: Int,
    val charge: Boolean,
)

data class SensorDeviceInfo(
    val address: String,
    val name: String,
)

data class DeviceLastStatus(
    val address: String,
    val time: Int,
    val battery: Int,
    val pm25: Int,
    val charging: Boolean,
)

fun SensorDevice.toUpdate() =
    DeviceStatusUpdate(info, connectStatus, pm25, lastUpdate, battery, charging)

class SensorDevice(
    var info: SensorDeviceInfo,
    pm25: Int = 0,
    battery: Int = 0,
    charge: Boolean = false,
    private val observer: (DeviceStatusUpdate) -> Unit
) {
    val callback = SensorDeviceGattCallback()
    var connectStatus: DeviceConnectStatus = DeviceConnectStatus.DISCONNECTED

    var pm25 = pm25
        private set
    var battery = battery
        private set
    var charging = charge
        private set
    var lastUpdate = 0
        private set

    fun connect(context: Context, adapter: BluetoothAdapter) {
        BluetoothAdapter.checkBluetoothAddress(info.address)
        if (connectStatus == DeviceConnectStatus.DISCONNECTED) {
            val d = adapter.getRemoteDevice(info.address)!!
            d.connectGatt(context, false, callback)
            connectStatus = DeviceConnectStatus.CONNECTING
        }
    }

    fun disconnect() {
        callback.disconnect()
    }

    fun writeCommand(command: DeviceCommand) {
        callback.writeCommand(command)
    }

    inner class SensorDeviceGattCallback : BluetoothGattCallback() {
        private var rx: BluetoothGattCharacteristic? = null
        private var tx: BluetoothGattCharacteristic? = null
        private var gatt: BluetoothGatt? = null
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            debug("onConnectionStateChange[$status]: $newState")
            this.gatt = gatt
            when (newState) {
                BluetoothProfile.STATE_DISCONNECTED ->
                    connectStatus = DeviceConnectStatus.DISCONNECTED
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                }
            }
            notifyUpdate()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val s = gatt.getService(DEVICE_UART_SERVICE) ?: return
            val rx = s.getCharacteristic(DEVICE_UART_RX)
            this.rx = rx
            val tx = s.getCharacteristic(DEVICE_UART_TX)
            this.tx = tx
            gatt.setCharacteristicNotification(tx, true)
            val txd = tx.getDescriptor(CCCD)
            txd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(txd)
            connectStatus = DeviceConnectStatus.CONNECTED
            debug("enable tx notif: $tx")
            notifyUpdate()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val tx = this.tx
            if (characteristic == tx) {
                runCatching {
                    when (val packet = parsePacket(tx.value)) {
                        is Battery -> updateBattery(packet)
                        is SensorData -> updateSensorValue(packet)
                    }
                    lastUpdate = unixTimestamp
                }
            }
        }

        fun disconnect() {
            gatt?.close() ?: return
            connectStatus = DeviceConnectStatus.DISCONNECTED
            notifyUpdate()
        }

        private fun updateSensorValue(packet: SensorData) {
            pm25 = packet.pm25
            notifyUpdate()
        }

        private fun updateBattery(packet: Battery) {
            battery = packet.capacity
            charging = packet.charging
            notifyUpdate()
        }

        private fun notifyUpdate() {
            this@SensorDevice.observer(toUpdate())
        }

        private fun debug(s: String) {
            Log.d("SensorDevCallBack", s)
        }

        fun writeCommand(command: DeviceCommand) {
            val rx = this.rx ?: return
            val gatt = this.gatt ?: return
            rx.value = command.toByteArray()
            gatt.writeCharacteristic(rx)
        }

    }
}