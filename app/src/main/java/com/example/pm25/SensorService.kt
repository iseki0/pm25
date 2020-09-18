package com.example.pm25

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData

class Device(val info: DeviceInfo) {
    val pm25 = MutableLiveData(-1)
    val battery = MutableLiveData(-1)
    val connectStatus = MutableLiveData(DeviceConnectStatus.DISCONNECTED)
}

class SensorService : Service() {
    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter!! }


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
        TODO()
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
                TODO()
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("service", "device connected, status = $status")
                        val service = gatt.getService(DEVICE_UART_SERVICE)!!
                        rx = service.getCharacteristic(DEVICE_UART_RX)
                        tx = service.getCharacteristic(DEVICE_UART_TX)
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

    class LocalBinder(val a: SensorService) : com.example.pm25.BaseLocalBinder<SensorService>(a)

    val localBinder = LocalBinder(this)

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}

enum class DeviceConnectStatus {
    CONNECTED, DISCONNECTED, CONNECTING, ERROR
}


