package io.fidelitycard.app.qr

import android.util.Log
import io.fidelitycard.crypto.wire.MalformedMessageException
import io.fidelitycard.crypto.wire.WireReader
import io.fidelitycard.crypto.wire.WireWriter

private const val LOG_TAG = "IssuerMessage"

private object IssuerTag {
    const val PROGRAM_INVITE = 201
    const val STAMP_GRANT = 202
}

/**
 * The things an issuer's phone can show a collector's camera. Neither
 * variant is a `:crypto` message type of its own - each just carries
 * already-independently-signed bytes (a Program Manifest, a Stamp Token),
 * the same way `CustomerMessage` carries already-signed Compact Stamp
 * Proofs. A leading tag byte (distinct from both
 * `io.fidelitycard.crypto.wire.MessageTag` and `CustomerMessage`'s own tag
 * space) is what lets the collector's single scanner figure out which
 * variant it's looking at without knowing in advance.
 */
sealed interface IssuerMessage {

    /** Every variant carries a Program Manifest, whether or not it's redundant with one this device already has. */
    val manifestBytes: ByteArray

    fun toWireBytes(): ByteArray

    /**
     * Pure join, no stamp attached - e.g. a static poster QR someone scans
     * out of curiosity, with no purchase and no cashier involved
     * (SPEC/SPECS.md §6.1).
     */
    data class ProgramInvite(override val manifestBytes: ByteArray) : IssuerMessage {
        override fun toWireBytes(): ByteArray =
            WireWriter().writeByte(IssuerTag.PROGRAM_INVITE).writeVarBytes(manifestBytes).toByteArray()
    }

    /**
     * "Give a stamp" (SPEC/SPECS.md §6.2): always carries the Program
     * Manifest alongside the Stamp Token, so a single scan can
     * create-or-reuse the local card and credit the stamp in the same
     * step - a customer's very first-ever stamp needs no separate "join"
     * scan first. The manifest is redundant on every stamp after the
     * first for a given card (the collector already has it), which costs
     * real bytes every time - measured and judged an acceptable trade for
     * always having exactly one uniform "give a stamp" action, no
     * new-vs-returning-customer branching at the register.
     */
    data class StampGrant(override val manifestBytes: ByteArray, val stampBytes: ByteArray) : IssuerMessage {
        override fun toWireBytes(): ByteArray =
            WireWriter()
                .writeByte(IssuerTag.STAMP_GRANT)
                .writeVarBytes(manifestBytes)
                .writeVarBytes(stampBytes)
                .toByteArray()
    }

    companion object {
        /** Never throws: an unrecognized or corrupt scan is data the collector's UI must show, not crash on. */
        fun parse(bytes: ByteArray): IssuerMessage? = try {
            val reader = WireReader(bytes)
            when (val tag = reader.readByte()) {
                IssuerTag.PROGRAM_INVITE -> {
                    val manifestBytes = reader.readVarBytes()
                    reader.requireFullyConsumed()
                    ProgramInvite(manifestBytes)
                }
                IssuerTag.STAMP_GRANT -> {
                    val manifestBytes = reader.readVarBytes()
                    val stampBytes = reader.readVarBytes()
                    reader.requireFullyConsumed()
                    StampGrant(manifestBytes, stampBytes)
                }
                else -> throw MalformedMessageException("Unknown issuer message tag $tag")
            }
        } catch (e: MalformedMessageException) {
            // Routine: scanning any unrelated QR code lands here every
            // time, not a bug - debug level, not a warning.
            Log.d(LOG_TAG, "Scanned bytes are not a recognized issuer message", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.d(LOG_TAG, "Scanned bytes are not a recognized issuer message", e)
            null
        }
    }
}
