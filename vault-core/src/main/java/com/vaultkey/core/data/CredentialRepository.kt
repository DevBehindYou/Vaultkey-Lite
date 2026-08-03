package com.vaultkey.core.data

import androidx.room.withTransaction
import com.vaultkey.core.crypto.EncryptedBlob
import com.vaultkey.core.crypto.VaultSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Plaintext view of a credential — only ever exists transiently, in memory, after unlock. */
data class DecryptedCredential(
    val id: String,
    val label: String,
    val username: String,
    val password: String,
    val notes: String?
)

/**
 * The single entry point for reading/writing credentials, used identically
 * by the vault UI, the keyboard's CredentialSuggestionInjector, and
 * VaultAutofillService — none of those three ever touch VaultDatabase,
 * FieldCipher, or the entity classes directly.
 */
class CredentialRepository(private val session: VaultSession) {

    private fun dao(): CredentialDao = session.currentDatabase().credentialDao()

    suspend fun addCredential(
        label: String,
        username: String,
        password: String,
        notes: String?,
        matches: List<Pair<MatchType, String>>
    ) {
        val cipher = session.currentFieldCipher()
        val now = System.currentTimeMillis()
        val usernameBlob = cipher.encrypt(username.toByteArray())
        val passwordBlob = cipher.encrypt(password.toByteArray())
        val notesBlob = notes?.let { cipher.encrypt(it.toByteArray()) }

        val entity = CredentialEntity(
            label = label,
            usernameCipherIv = usernameBlob.iv,
            usernameCipherBytes = usernameBlob.ciphertext,
            passwordCipherIv = passwordBlob.iv,
            passwordCipherBytes = passwordBlob.ciphertext,
            notesCipherIv = notesBlob?.iv,
            notesCipherBytes = notesBlob?.ciphertext,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null
        )
        // Single transaction: a credential is never persisted without its
        // match rows (a half-written credential would be invisible to lookups).
        session.currentDatabase().withTransaction {
            dao().insertCredential(entity)
            matches.forEach { (type, value) ->
                // Normalize web domains on WRITE with the same rule the lookup
                // uses on READ (findForWebDomain), otherwise "www.GitHub.com" or
                // "https://github.com" would save a match that never matches.
                val matchValue = if (type == MatchType.WEB_DOMAIN) normalizeDomain(value) else value
                dao().insertMatch(CredentialMatchEntity(credentialId = entity.id, matchType = type, matchValue = matchValue))
            }
        }
    }

    suspend fun getAll(): List<DecryptedCredential> = dao().getAll().map { it.decrypt() }

    /** Reactive vault list — emits a fresh decrypted list on every DB change. */
    fun observeAll(): Flow<List<DecryptedCredential>> =
        dao().observeAll()
            .map { rows -> rows.map { it.decrypt() } }
            .catch { emit(emptyList()) } // vault locked out from under us -> emit empty rather than crash

    suspend fun findForPackageName(packageName: String): List<DecryptedCredential> =
        dao().findByMatch(MatchType.PACKAGE_NAME, packageName).map { it.decrypt() }

    suspend fun findForWebDomain(domain: String): List<DecryptedCredential> =
        dao().findByMatch(MatchType.WEB_DOMAIN, normalizeDomain(domain)).map { it.decrypt() }

    suspend fun markUsed(id: String) = dao().markUsed(id, System.currentTimeMillis())

    private fun CredentialEntity.decrypt(): DecryptedCredential {
        val cipher = session.currentFieldCipher()
        val username = String(cipher.decrypt(EncryptedBlob(usernameCipherIv, usernameCipherBytes)))
        val password = String(cipher.decrypt(EncryptedBlob(passwordCipherIv, passwordCipherBytes)))
        val notes = if (notesCipherIv != null && notesCipherBytes != null) {
            String(cipher.decrypt(EncryptedBlob(notesCipherIv, notesCipherBytes)))
        } else null
        return DecryptedCredential(id, label, username, password, notes)
    }

    private fun normalizeDomain(raw: String): String =
        raw.substringAfter("://").removePrefix("www.").substringBefore("/").lowercase()
}
