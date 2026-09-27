package io.fidelitycard.app.data

/** A stamp/redemption id's raw bytes, as stored in Room and compared in `:core`'s spent-set checks. */
internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
