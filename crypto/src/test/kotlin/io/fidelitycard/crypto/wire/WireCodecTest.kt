package io.fidelitycard.crypto.wire

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WireCodecTest {

    @Test
    fun `round trips every primitive in order`() {
        val fixed = ByteArray(4) { it.toByte() }
        val bytes = WireWriter()
            .writeByte(0x07)
            .writeInt32(-42)
            .writeInt64(1_700_000_000_123L)
            .writeFixedBytes(fixed, expectedLength = 4)
            .writeVarBytes(byteArrayOf(9, 8, 7))
            .writeString("Joe's Coffee")
            .toByteArray()

        val reader = WireReader(bytes)
        assertEquals(0x07, reader.readByte())
        assertEquals(-42, reader.readInt32())
        assertEquals(1_700_000_000_123L, reader.readInt64())
        assertArrayEquals(fixed, reader.readFixedBytes(4))
        assertArrayEquals(byteArrayOf(9, 8, 7), reader.readVarBytes())
        assertEquals("Joe's Coffee", reader.readString())
        reader.requireFullyConsumed()
    }

    @Test
    fun `reading past the end of the buffer is reported as malformed, not a crash`() {
        val bytes = WireWriter().writeByte(0x01).toByteArray()
        val reader = WireReader(bytes)
        reader.readByte()

        assertThrows(MalformedMessageException::class.java) { reader.readByte() }
    }

    @Test
    fun `trailing unconsumed bytes are rejected`() {
        val bytes = WireWriter().writeByte(0x01).writeByte(0x02).toByteArray()
        val reader = WireReader(bytes)
        reader.readByte()

        assertThrows(MalformedMessageException::class.java) { reader.requireFullyConsumed() }
    }

    @Test
    fun `a var bytes length prefix that overruns the buffer is malformed rather than an exception leak`() {
        // Claims a 3-byte payload but only one byte follows.
        val bytes = byteArrayOf(0, 3, 42)

        assertThrows(MalformedMessageException::class.java) { WireReader(bytes).readVarBytes() }
    }

    @Test
    fun `fixed bytes of the wrong length are rejected at write time`() {
        assertThrows(IllegalArgumentException::class.java) {
            WireWriter().writeFixedBytes(ByteArray(3), expectedLength = 4)
        }
    }

    @Test
    fun `var bytes longer than 65535 are rejected at write time`() {
        assertThrows(IllegalArgumentException::class.java) {
            WireWriter().writeVarBytes(ByteArray(65536))
        }
    }
}
