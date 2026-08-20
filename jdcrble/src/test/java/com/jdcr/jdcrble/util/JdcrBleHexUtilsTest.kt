package com.jdcr.jdcrble.util

import com.jdcr.jdcrble.util.JdcrBleHexUtils.hexToBytes
import com.jdcr.jdcrble.util.JdcrBleHexUtils.signedIntsToBytes
import com.jdcr.jdcrble.util.JdcrBleHexUtils.toHexString
import com.jdcr.jdcrble.util.JdcrBleHexUtils.toUnsignedInt
import com.jdcr.jdcrble.util.JdcrBleHexUtils.toUnsignedIntList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JdcrBleHexUtilsTest {

    @Test
    fun hexToBytes_acceptsSupportedFormats() {
        val expected = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x05)

        listOf("FF FB 05", "0xFFFB05", "FF:FB:05", "fffb05", "FF-FB-05")
            .forEach { value -> assertArrayEquals(value, expected, value.hexToBytes()) }
    }

    @Test
    fun hexToBytes_rejectsEmptyOddLengthAndInvalidInput() {
        assertThrows(IllegalArgumentException::class.java) { "  ".hexToBytes() }
        assertThrows(IllegalArgumentException::class.java) { "ABC".hexToBytes() }
        assertThrows(IllegalArgumentException::class.java) { "FG".hexToBytes() }
    }

    @Test
    fun toHexString_supportsCaseAndMultiCharacterSeparator() {
        val value = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())

        assertEquals("007F80FF", value.toHexString())
        assertEquals("00::7f::80::ff", value.toHexString(separator = "::", lowerCase = true))
        assertEquals("", byteArrayOf().toHexString(separator = ":"))
    }

    @Test
    fun signedAndUnsignedConversions_preserveAllByteValues() {
        val bytes = listOf(-1, -128, 0, 127, 255).signedIntsToBytes()

        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00, 0x7F, 0xFF.toByte()),
            bytes
        )
        assertEquals(listOf(255, 128, 0, 127, 255), bytes.toUnsignedIntList())
        assertEquals(255, 0xFF.toByte().toUnsignedInt())
    }
}
