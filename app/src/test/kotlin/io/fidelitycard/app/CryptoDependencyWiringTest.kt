package io.fidelitycard.app

import io.fidelitycard.crypto.SigningKeyPair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Not a real feature test - just documents and pins that :app can see and
 * use :crypto's public API. Unverified in this environment (no Android SDK
 * available here to run :app:testDebugUnitTest); confirm once opened in
 * Android Studio.
 */
class CryptoDependencyWiringTest {

    @Test
    fun `crypto module is on the classpath`() {
        val keyPair = SigningKeyPair.generate()

        assertEquals(32, keyPair.publicKey.bytes.size)
    }
}
