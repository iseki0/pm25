package space.iseki.pm25

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

object Shutdown : DeviceMessage(Type.SHUTDOWN)
data class MeasurementInterval(val interval: Int) : DeviceMessage(Type.MEASUREMENT_INTERVAL)
data class SetRTC(val timestamp: Int) : DeviceMessage(Type.SET_RTC)
data class NoMoreHistory(val unknown: Byte) : DeviceMessage(Type.NO_MORE_HISTORY)
data class History(val pm25: Int, val timestamp: Int) : DeviceMessage(Type.HISTORY)
data class MeasurementEnabled(val enabled: Boolean) : DeviceMessage(Type.MEASUREMENT_ENABLED)
data class VersionPacket(val minor: Int, val major: Int) : DeviceMessage(Type.VERSION)
data class Battery(val capacity: Int, val charging: Boolean) : DeviceMessage(Type.BATTERY)
data class HardwareRuntime(val run: Int, val boot: Int) : DeviceMessage(Type.RUNTIME)
data class SensorData(val pm25: Int, val recordDate: Int, val unknown: Byte, val currentDate: Int) :
    DeviceMessage(Type.SENSOR)

data class MeasurementSetup(val interval: Int, val enabled: Boolean) :
    DeviceMessage(Type.MEASUREMENT)