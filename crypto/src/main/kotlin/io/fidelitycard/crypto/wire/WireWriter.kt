package io.fidelitycard.crypto.wire

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Builds the canonical byte encoding of a message: fields are written in a
 * fixed, schema-defined order with unambiguous framing (fixed-width
 * integers, length-prefixed variable data), so two implementations that
 * write the same fields in the same order always produce identical bytes.
 * There is no general-purpose "canonical form" step to get right (as there
 * would be for e.g. CBOR maps) — for a fixed-shape record, sequential,
 * unambiguous concatenation already is the canonical form.
 */
class WireWriter {

    private val buffer = ByteArrayOutputStream()

    fun writeByte(value: Int): WireWriter {
        buffer.write(value and 0xFF)
        return this
    }

    fun writeInt32(value: Int): WireWriter {
        buffer.write((value ushr 24) and 0xFF)
        buffer.write((value ushr 16) and 0xFF)
        buffer.write((value ushr 8) and 0xFF)
        buffer.write(value and 0xFF)
        return this
    }

    fun writeInt64(value: Long): WireWriter {
        for (shift in 56 downTo 0 step 8) {
            buffer.write(((value ushr shift) and 0xFF).toInt())
        }
        return this
    }

    /** Writes [bytes] verbatim, no length prefix — the schema fixes the length. */
    fun writeFixedBytes(bytes: ByteArray, expectedLength: Int): WireWriter {
        require(bytes.size == expectedLength) {
            "Expected $expectedLength bytes, got ${bytes.size}"
        }
        buffer.write(bytes)
        return this
    }

    /** Writes a 2-byte big-endian length prefix followed by [bytes]. */
    fun writeVarBytes(bytes: ByteArray): WireWriter {
        require(bytes.size <= MAX_VAR_LENGTH) {
            "Variable-length field too long: ${bytes.size} > $MAX_VAR_LENGTH"
        }
        writeByte((bytes.size ushr 8) and 0xFF)
        writeByte(bytes.size and 0xFF)
        buffer.write(bytes)
        return this
    }

    fun writeString(value: String): WireWriter =
        writeVarBytes(value.toByteArray(StandardCharsets.UTF_8))

    fun toByteArray(): ByteArray = buffer.toByteArray()

    companion object {
        const val MAX_VAR_LENGTH = 0xFFFF
    }
}
