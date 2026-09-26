package io.fidelitycard.crypto.wire

/** Protocol version tag written as the first byte of every message (SPEC/SPECS.md §5). */
const val WIRE_VERSION = 1

/** Ed25519 signatures are always exactly 64 bytes. */
const val SIGNATURE_LENGTH_BYTES = 64

/** Type tags, one per signed message kind in SPEC/SPECS.md §5. Never reused across kinds. */
object MessageTag {
    const val PROGRAM = 1
    const val CARD_CERT = 2
    const val STAMP = 3
    const val REDEMPTION = 4
}

/**
 * Every message starts with a version byte and a type tag. Reading them
 * through one shared helper means a mismatch always fails the same way
 * ([MalformedMessageException]) instead of each message type inventing its
 * own check.
 */
fun WireReader.readAndVerifyHeader(expectedTag: Int) {
    val version = readByte()
    if (version != WIRE_VERSION) {
        throw MalformedMessageException("Unsupported wire version $version (expected $WIRE_VERSION)")
    }
    val tag = readByte()
    if (tag != expectedTag) {
        throw MalformedMessageException("Unexpected message type tag $tag (expected $expectedTag)")
    }
}
