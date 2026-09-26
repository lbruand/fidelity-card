package io.fidelitycard.crypto.wire

/**
 * The bytes handed to a [WireReader] (or to a message type's `parseAndVerify`)
 * don't decode as a well-formed message: truncated, an out-of-range length
 * prefix, or trailing bytes left over after decoding. Always a problem with
 * the input, never a signature failure — see `InvalidSignatureException` for
 * that.
 */
class MalformedMessageException(message: String) : RuntimeException(message)
