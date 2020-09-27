package space.iseki.pm25

sealed class DeviceCommand

object ShutdownCommand : DeviceCommand()


fun DeviceCommand.toByteArray() =
    when (this) {
        ShutdownCommand -> byteArrayOf(0xaa.toByte(), 0x01, 0xab.toByte())
        else -> TODO()
    }
