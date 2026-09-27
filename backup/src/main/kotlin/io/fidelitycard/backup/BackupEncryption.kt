package io.fidelitycard.backup

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The passphrase was wrong, or the backup file was corrupted/tampered with - GCM's auth tag catches both identically. */
class BackupDecryptionException(message: String) : RuntimeException(message)

/**
 * Passphrase-based encryption for a full backup snapshot (SPEC/SPECS.md
 * §11 "Backup & restore", BACKUP_FORMAT.md). A backup contains raw signing
 * key seeds in the clear (see [BackupSnapshot]), so the exported file must
 * never be writable to anywhere shareable without this wrapped around it
 * first.
 *
 * AES-256-GCM (authenticated - a wrong passphrase or a corrupted/tampered
 * file both fail loudly, never silently return garbage) with a key
 * derived via PBKDF2-HMAC-SHA256. No new dependency: both are standard
 * JCE, available on every JVM/Android target without adding anything to
 * the dependency tree.
 */
object BackupEncryption {
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val HEADER_LENGTH_BYTES = 5 // MAGIC + FORMAT_VERSION

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = randomBytes(SALT_LENGTH_BYTES)
        val iv = randomBytes(IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + byteArrayOf(FORMAT_VERSION) + salt + iv + ciphertext
    }

    fun decrypt(envelope: ByteArray, passphrase: CharArray): ByteArray {
        require(envelope.size > HEADER_LENGTH_BYTES + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            "Envelope too short to be a valid backup (was ${envelope.size} bytes)"
        }
        require(envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Not a Fidelity Card backup file"
        }
        val version = envelope[MAGIC.size]
        require(version == FORMAT_VERSION) {
            "Unsupported backup format version $version (expected $FORMAT_VERSION)"
        }

        var offset = HEADER_LENGTH_BYTES
        val salt = envelope.copyOfRange(offset, offset + SALT_LENGTH_BYTES).also { offset += SALT_LENGTH_BYTES }
        val iv = envelope.copyOfRange(offset, offset + IV_LENGTH_BYTES).also { offset += IV_LENGTH_BYTES }
        val ciphertext = envelope.copyOfRange(offset, envelope.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw BackupDecryptionException("Wrong passphrase, or this backup is corrupted")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun randomBytes(length: Int): ByteArray = ByteArray(length).also { SecureRandom().nextBytes(it) }
}
