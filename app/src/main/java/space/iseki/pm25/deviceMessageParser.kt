@file:Suppress("NOTHING_TO_INLINE")

package space.iseki.pm25

fun parsePacket(buf: ByteArray): DeviceMessage {
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
        return when (buf[1]) {
            b(0x01) -> Shutdown
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
                else -> error("unrecognized packet")
            }
            b(0x54) -> VersionPacket(int(2 size 2), int(4 size 2))
            else -> error("unrecognized packet")
        }
    }
}

private inline fun b(i: Int) = i.toByte()
private inline fun ByteArray.int(range: IntRange) = bytes2Int(sliceArray(range))
private inline fun bytes2Int(bs: ByteArray) =
    bs.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xff) }

private inline infix fun Int.size(size: Int) = this until this + size