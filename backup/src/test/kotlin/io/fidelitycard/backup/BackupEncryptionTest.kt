package io.fidelitycard.backup

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BackupEncryptionTest {

    private val plaintext = "hello fidelity card backup".toByteArray()

    @Test
    fun `decrypting with the correct passphrase recovers the original plaintext`() {
        val envelope = BackupEncryption.encrypt(plaintext, "correct horse battery staple".toCharArray())

        val decrypted = BackupEncryption.decrypt(envelope, "correct horse battery staple".toCharArray())

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypting with the wrong passphrase fails cleanly, not with a generic crash`() {
        val envelope = BackupEncryption.encrypt(plaintext, "correct horse battery staple".toCharArray())

        assertThrows(BackupDecryptionException::class.java) {
            BackupEncryption.decrypt(envelope, "wrong passphrase".toCharArray())
        }
    }

    @Test
    fun `tampering with the ciphertext is detected, not silently accepted`() {
        val envelope = BackupEncryption.encrypt(plaintext, "correct horse battery staple".toCharArray())
        val tampered = envelope.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        assertThrows(BackupDecryptionException::class.java) {
            BackupEncryption.decrypt(tampered, "correct horse battery staple".toCharArray())
        }
    }

    @Test
    fun `encrypting the same plaintext twice produces different envelopes, because of fresh salt and iv`() {
        val a = BackupEncryption.encrypt(plaintext, "correct horse battery staple".toCharArray())
        val b = BackupEncryption.encrypt(plaintext, "correct horse battery staple".toCharArray())

        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `an envelope too short to contain a header, salt and iv is rejected as malformed, not a wrong-passphrase failure`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupEncryption.decrypt(ByteArray(10), "anything".toCharArray())
        }
    }

    @Test
    fun `an unrelated file is rejected outright, before even attempting to decrypt it`() {
        val unrelatedFile = ByteArray(64) { it.toByte() } // definitely not our magic bytes

        assertThrows(IllegalArgumentException::class.java) {
            BackupEncryption.decrypt(unrelatedFile, "anything".toCharArray())
        }
    }

    @Test
    fun `an unsupported format version is rejected outright, before even attempting to decrypt it`() {
        val envelope = BackupEncryption.encrypt(plaintext, "correct horse battery staple".toCharArray())
        val futureVersion = envelope.copyOf()
        futureVersion[4] = (futureVersion[4] + 1).toByte() // byte 4 is the format version, right after the 4-byte magic

        assertThrows(IllegalArgumentException::class.java) {
            BackupEncryption.decrypt(futureVersion, "correct horse battery staple".toCharArray())
        }
    }
}
