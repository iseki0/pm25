package com.example.pm25

import com.fasterxml.jackson.core.type.TypeReference
import java.io.File

// TODO: rewrite it!

private val deviceInfoStorageType =
    object : TypeReference<List<SensorDeviceInfo>>() {}
private val deviceLastStatusStorageType =
    object : TypeReference<List<DeviceLastStatus>>() {}

private val deviceInfoPath
        by lazy { File(applicationContext.filesDir, "xfDevices.json") }
private val deviceInfoPathBackup
        by lazy { File(applicationContext.filesDir, "xfDevices_backup.json") }
private val deviceLastStatusPath
        by lazy { File(applicationContext.filesDir, "xfLastStatus.json") }
private val deviceLastStatusPathBackup
        by lazy { File(applicationContext.filesDir, "xfLastStatus_backup.json") }

fun writeDeviceInfoToStorage(devices: List<SensorDeviceInfo>) {
    val s = jsonMapper.writeValueAsString(devices)
    deviceInfoPath.writeText(s)
    deviceInfoPathBackup.writeText(s)
}

fun loadDeviceInfoFromStorage(): List<SensorDeviceInfo> {
    listOf(deviceInfoPath, deviceInfoPathBackup).forEach {
        runCatching { jsonMapper.readValue(it.readText(), deviceInfoStorageType) }
            .onSuccess { return it }
    }
    return emptyList()
}

fun writeDeviceLastStatusToStorage(devices: List<DeviceLastStatus>) {
    val s = jsonMapper.writeValueAsString(devices)
    deviceLastStatusPath.writeText(s)
    deviceLastStatusPathBackup.writeText(s)
}

fun loadDeviceLastStatusFromStorage(): List<DeviceLastStatus> {
    listOf(deviceLastStatusPath, deviceLastStatusPathBackup).forEach {
        runCatching { jsonMapper.readValue(it.readText(), deviceLastStatusStorageType) }
            .onSuccess { return it }
    }
    return emptyList()
}
