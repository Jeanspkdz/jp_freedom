package com.jp.freedom.enforcement

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteFilterVpnServiceTest {
    @Test
    fun blockedDnsResponseTrimsUncountedEdnsRecord() {
        val question = byteArrayOf(
            0x07,
            'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03,
            'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01,
            0x00, 0x01,
        )
        val query = ByteArray(12).apply {
            this[0] = 0x42
            this[1] = 0x17
            this[2] = 0x01
            this[5] = 0x01
            this[11] = 0x01
        } + question + byteArrayOf(0x00, 0x00, 0x29, 0x04, 0xd0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val questionEnd = 12 + question.size

        val response = SiteFilterVpnService.blockedDnsResponse(query, questionEnd)

        assertEquals(questionEnd, response.size)
        assertArrayEquals(query.copyOfRange(0, 2), response.copyOfRange(0, 2))
        assertArrayEquals(query.copyOfRange(12, questionEnd), response.copyOfRange(12, questionEnd))
        val flags = unsignedShort(response, 2)
        assertTrue(flags and 0x8000 != 0)
        assertTrue(flags and 0x0100 != 0)
        assertEquals(3, flags and 0x000f)
        assertEquals(1, unsignedShort(response, 4))
        assertEquals(0, unsignedShort(response, 6))
        assertEquals(0, unsignedShort(response, 8))
        assertEquals(0, unsignedShort(response, 10))
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
