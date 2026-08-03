package com.vaultkey.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class PasswordKeyDerivationTest {

    @Test
    fun `wrap then unwrap with the correct password returns the original key`() {
        val salt = PasswordKeyDerivation.generateSalt()
        val dbKey = ByteArray(32) { it.toByte() }
        val wrapped = PasswordKeyDerivation.wrapDbKey("correct horse battery".toCharArray(), salt, dbKey)

        val recovered = PasswordKeyDerivation.unwrapDbKey("correct horse battery".toCharArray(), salt, wrapped)

        assertArrayEquals(dbKey, recovered)
    }

    @Test
    fun `unwrap with the wrong password fails loudly instead of returning garbage`() {
        val salt = PasswordKeyDerivation.generateSalt()
        val dbKey = ByteArray(32) { it.toByte() }
        val wrapped = PasswordKeyDerivation.wrapDbKey("correct horse battery".toCharArray(), salt, dbKey)

        // GCM's auth tag check means a wrong key throws rather than silently
        // decrypting to the wrong bytes — this is what VaultSession.unlockWithPassword
        // relies on to detect a wrong master password.
        assertThrows(AEADBadTagException::class.java) {
            PasswordKeyDerivation.unwrapDbKey("wrong password entirely".toCharArray(), salt, wrapped)
        }
    }

    @Test
    fun `different salts produce different wrapped output for the same password and key`() {
        val dbKey = ByteArray(32) { it.toByte() }
        val saltA = PasswordKeyDerivation.generateSalt()
        val saltB = PasswordKeyDerivation.generateSalt()

        val wrappedA = PasswordKeyDerivation.wrapDbKey("same password".toCharArray(), saltA, dbKey)
        val wrappedB = PasswordKeyDerivation.wrapDbKey("same password".toCharArray(), saltB, dbKey)

        assert(!wrappedA.ciphertext.contentEquals(wrappedB.ciphertext))
    }
}
