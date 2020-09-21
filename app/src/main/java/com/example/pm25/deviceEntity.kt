package com.example.pm25

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
    charge: Boolean = false
) {
    val callback = SensorDeviceGattCallback()
    var connectStatus: DeviceConnectStatus = DeviceConnectStatus.DISCONNECTED
    var gatt: BluetoothGatt? = null

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
        if (!isConnected()) {
            val device = adapter.getRemoteDevice(info.address)
            gatt = device.connectGatt(context, false, callback)
        }
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    inner class SensorDeviceGattCallback : BluetoothGattCallback() {
        private lateinit var rx: BluetoothGattCharacteristic
        private lateinit var tx: BluetoothGattCharacteristic
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            debug("onConnectionStateChange[$status]: $newState")
            connectStatus = when (newState) {
                BluetoothProfile.STATE_DISCONNECTED -> DeviceConnectStatus.DISCONNECTED
                BluetoothProfile.STATE_CONNECTED -> DeviceConnectStatus.CONNECTED
                else -> error("")
            }
            if (isConnected()) {
                val s = gatt.getService(DEVICE_UART_SERVICE)!!
                rx = s.getCharacteristic(DEVICE_UART_RX)
                tx = s.getCharacteristic(DEVICE_UART_TX)
                gatt.setCharacteristicNotification(tx, true)
                debug("enable rx notif: $tx")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic == tx) {
                runCatching {
                    val packet = parsePacket(tx.value)
                    debug(packet.toString())
                    when (packet) {
                        is Battery -> updateBattery(packet)
                        is SensorData -> updateSensorValue(packet)
                    }
                    lastUpdate = unixTimestamp
                }
            }
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
            TODO()
        }

        private fun debug(s: String) {
            Log.d("SensorDevCallBack", s)
        }
    }
}