package com.example.pm25

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}

class DeviceMessageParserTest {
    @Test
    fun test() {
        val p1 = hex2ByteArray("aa 50 07 00 0a 00 0b")
        assertEquals(MeasurementSetup(10, false), parsePacket(p1))
        val p2 = hex2ByteArray("aa 50 06 00 20 00 00 5f 57 8a 61 02 5f 57 8a 62 65")
        assertEquals(SensorData(32, 0x5f578a61, 0x02.toByte(), 0x5f578a62), parsePacket(p2))
    }
}


internal fun hex2ByteArray(hex: String, vararg delimiters: String = arrayOf(" ", ":", ",")) =
    hex.toUpperCase().splitToSequence(*delimiters).map { it.toInt(16).toByte() }.toList()
        .toByteArray()

