package com.vaultkey.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class FieldCipherTest {

    @Test
    fun `encrypt then decrypt returns the original plaintext`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = FieldCipher(key)

        val blob = cipher.encrypt("hunter2".toByteArray())
        val recovered = String(cipher.decrypt(blob))

        assertEquals("hunter2", recovered)
    }

    @Test
    fun `decrypting with the wrong key fails instead of returning garbage`() {
        val keyA = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val keyB = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val blob = FieldCipher(keyA).encrypt("some secret".toByteArray())

        assertThrows(AEADBadTagException::class.java) {
            FieldCipher(keyB).decrypt(blob)
        }
    }
}
