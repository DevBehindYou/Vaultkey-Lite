package com.vaultkey.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class EncryptedBlobTest {

    @Test
    fun `toBytes then fromBytes round-trips iv and ciphertext`() {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val original = EncryptedBlob(iv, ciphertext)

        val restored = EncryptedBlob.fromBytes(original.toBytes())

        assertTrue(restored.iv.contentEquals(iv))
        assertTrue(restored.ciphertext.contentEquals(ciphertext))
        // This is what VaultSession relies on to nest the password envelope
        // inside the Keystore envelope and get the exact bytes back.
        assertEquals(original, restored)
    }

    @Test
    fun `value equality is by content, not reference`() {
        val a = EncryptedBlob(byteArrayOf(1, 2, 3), byteArrayOf(9, 8, 7))
        val b = EncryptedBlob(byteArrayOf(1, 2, 3), byteArrayOf(9, 8, 7))
        val c = EncryptedBlob(byteArrayOf(1, 2, 3), byteArrayOf(9, 8, 6))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
