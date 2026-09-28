package io.fidelitycard.crypto.wire

/**
 * Protocol version tag written as the first byte of every message
 * (SPEC/SPECS.md §5). Bumped to 3 when Card Certificate (and enrollment as
 * a distinct handshake) was removed entirely - it never verified anything
 * a stamp/redemption request's own signature checks didn't already cover,
 * so it wasn't buying real security; see SPEC/SPECS.md §6.1. This also
 * frees up its type tag, reused by renumbering rather than left as a gap.
 * Bumped to 4 when Program Manifest gained `color`/`icon` (TODO.md
 * "Product / UX" - card personalization). Bumped to 5 when Stamp Token
 * and Redemption Certificate both dropped `card_id` entirely: a stamp's
 * value now lives in holding its bytes, not in a collector identity, so
 * minting a stamp no longer needs the issuer to learn anything from the
 * collector first - it's a one-way QR (issuer mints, collector scans),
 * not a request/response round trip. See SPEC/SPECS.md §4/§6.2 for the
 * reasoning and the accepted trade-off (a leaked stamp is redeemable by
 * whoever gets to redemption first, not just its original recipient).
 */
const val WIRE_VERSION = 5

/** Ed25519 signatures are always exactly 64 bytes. */
const val SIGNATURE_LENGTH_BYTES = 64

/** Type tags, one per signed message kind in SPEC/SPECS.md §5. Never reused across kinds. */
object MessageTag {
    const val PROGRAM = 1
    const val STAMP = 2
    const val REDEMPTION = 3
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
