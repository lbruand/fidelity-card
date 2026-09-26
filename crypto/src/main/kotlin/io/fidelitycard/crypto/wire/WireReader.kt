package io.fidelitycard.crypto.wire

import java.nio.charset.StandardCharsets

/**
 * Reads back a message written by [WireWriter], field by field, in the same
 * order it was written. Every failure mode (truncation, an out-of-range
 * length prefix, trailing bytes) surfaces as [MalformedMessageException]
 * rather than an unchecked index/buffer exception, so callers parsing
 * untrusted QR payloads have exactly one exception type to handle.
 */
class WireReader(private val bytes: ByteArray) {

    private var position = 0

    fun readByte(): Int {
        require(1)
        return bytes[position++].toInt() and 0xFF
    }

    fun readInt32(): Int {
        require(4)
        var value = 0
        repeat(4) { value = (value shl 8) or (bytes[position++].toInt() and 0xFF) }
        return value
    }

    fun readInt64(): Long {
        require(8)
        var value = 0L
        repeat(8) { value = (value shl 8) or (bytes[position++].toLong() and 0xFF) }
        return value
    }

    fun readFixedBytes(length: Int): ByteArray {
        require(length)
        val result = bytes.copyOfRange(position, position + length)
        position += length
        return result
    }

    fun readVarBytes(): ByteArray {
        val length = readByte().shl(8) or readByte()
        require(length)
        val result = bytes.copyOfRange(position, position + length)
        position += length
        return result
    }

    fun readString(): String = String(readVarBytes(), StandardCharsets.UTF_8)

    /** Fails if any bytes remain unconsumed: a sign of trailing garbage or a length bug. */
    fun requireFullyConsumed() {
        if (position != bytes.size) {
            throw MalformedMessageException(
                "Expected all ${bytes.size} bytes to be consumed, but $position were read",
            )
        }
    }

    private fun require(byteCount: Int) {
        if (position + byteCount > bytes.size) {
            throw MalformedMessageException(
                "Expected $byteCount more byte(s) at position $position, only ${bytes.size - position} remain",
            )
        }
    }
}
