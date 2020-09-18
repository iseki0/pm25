package com.example.pm25

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log

class BleScanner(context: Context, val bluetoothAdapter: BluetoothAdapter) {
    val viewAdapter = BleAdapter(context)
    var scanning = false
        private set
    private val handler = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            viewAdapter.putDevice(result.device)
        }
    }

    fun doScan() {
        Log.d("scanner", "start")
        runCatching { bluetoothAdapter.bluetoothLeScanner.startScan(handler) }
    }

    fun stopScan() {
        Log.d("scanner", "stop")
        runCatching { bluetoothAdapter.bluetoothLeScanner.stopScan(handler) }
    }

}