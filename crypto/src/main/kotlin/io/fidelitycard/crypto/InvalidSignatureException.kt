package io.fidelitycard.crypto

/**
 * The bytes decoded into a well-formed message (see `MalformedMessageException`
 * for the alternative), but its signature does not verify against the
 * expected issuer key. Always treat this as "reject the message," never as
 * a reason to fall back to trusting it anyway.
 */
class InvalidSignatureException(message: String) : RuntimeException(message)
