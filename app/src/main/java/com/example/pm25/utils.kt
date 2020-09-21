package com.example.pm25

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper


val BluetoothAdapter.isDisabled: Boolean
    get() = !this.isEnabled

val LocationManager.isDisable: Boolean
    get() = !isEnable

val LocationManager.isEnable: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        this.isLocationEnabled
    else
        this.allProviders.any(this::isProviderEnabled)


fun Context.startBluetoothActivity() {
    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    startActivity(intent)
}

fun Context.startLocationActivity() {
    startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

fun checkPermissions(c: Context, vararg ps: String) = ps.all {
    ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED
}

val jsonMapper = jacksonObjectMapper()


fun onMainThread() = Looper.myLooper() == Looper.getMainLooper()

val unixTimestamp: Int
    get() = (System.currentTimeMillis() / 1000).toInt()