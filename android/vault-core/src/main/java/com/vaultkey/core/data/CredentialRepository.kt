package com.vaultkey.core.data

import androidx.room.withTransaction
import com.vaultkey.core.crypto.EncryptedBlob
import com.vaultkey.core.crypto.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plaintext view of a credential — only ever exists transiently, in memory, after unlock. */
data class DecryptedCredential(
    val id: String,
    val label: String,
    val username: String,
    val password: String,
    val notes: String?,
    // Present only when fetched for the detail/edit screen (see getDetailById);
    // the list and autofill/keyboard match paths leave these null to avoid an
    // extra per-row query they don't need.
    val webDomain: String? = null,
    val packageName: String? = null
)

/**
 * Password-free row for the vault list. Decrypting only the username (never the
 * password/notes) for the list means the plaintext of every stored password is
 * never materialised in memory just to render a list of labels.
 */
data class CredentialSummary(
    val id: String,
    val label: String,
    val username: String
)

/**
 * The single entry point for reading/writing credentials, used identically
 * by the vault UI, the keyboard's CredentialSuggestionInjector, and
 * VaultAutofillService — none of those three ever touch VaultDatabase,
 * FieldCipher, or the entity classes directly.
 *
 * Decryption of multiple rows is dispatched to Dispatchers.Default so a large
 * vault doesn't run AES over every field on the caller's (often main) thread.
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
        val usernameBlob = cipher.encrypt(username.toByteArray(Charsets.UTF_8))
        val passwordBlob = cipher.encrypt(password.toByteArray(Charsets.UTF_8))
        val notesBlob = notes?.let { cipher.encrypt(it.toByteArray(Charsets.UTF_8)) }

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
        dao().insertCredential(entity)
        matches.forEach { (type, value) ->
            dao().insertMatch(CredentialMatchEntity(credentialId = entity.id, matchType = type, matchValue = value))
        }
    }

    /**
     * Edit an existing credential in place. Preserves the original id and
     * createdAt; re-encrypts every field with a fresh IV; replaces the whole
     * match set. Wrapped in a DB transaction so a failure can't leave the row
     * updated but its matches half-rewritten.
     */
    suspend fun updateCredential(
        id: String,
        label: String,
        username: String,
        password: String,
        notes: String?,
        matches: List<Pair<MatchType, String>>
    ) {
        val existing = dao().getById(id) ?: return
        val cipher = session.currentFieldCipher()
        val usernameBlob = cipher.encrypt(username.toByteArray(Charsets.UTF_8))
        val passwordBlob = cipher.encrypt(password.toByteArray(Charsets.UTF_8))
        val notesBlob = notes?.let { cipher.encrypt(it.toByteArray(Charsets.UTF_8)) }

        val updated = existing.copy(
            label = label,
            usernameCipherIv = usernameBlob.iv,
            usernameCipherBytes = usernameBlob.ciphertext,
            passwordCipherIv = passwordBlob.iv,
            passwordCipherBytes = passwordBlob.ciphertext,
            notesCipherIv = notesBlob?.iv,
            notesCipherBytes = notesBlob?.ciphertext,
            updatedAt = System.currentTimeMillis()
        )

        session.currentDatabase().withTransaction {
            dao().updateCredential(updated)
            dao().deleteMatchesFor(id)
            matches.forEach { (type, value) ->
                dao().insertMatch(CredentialMatchEntity(credentialId = id, matchType = type, matchValue = value))
            }
        }
    }

    suspend fun deleteCredential(id: String) {
        val existing = dao().getById(id) ?: return
        dao().deleteCredential(existing) // FK onDelete=CASCADE removes its matches
    }

    /** Password-free list for the vault UI (username decrypted, password/notes never touched). */
    suspend fun getAllSummaries(): List<CredentialSummary> = withContext(Dispatchers.Default) {
        val cipher = session.currentFieldCipher()
        dao().getAll().map {
            CredentialSummary(
                id = it.id,
                label = it.label,
                username = String(cipher.decrypt(EncryptedBlob(it.usernameCipherIv, it.usernameCipherBytes)), Charsets.UTF_8)
            )
        }
    }

    suspend fun getAll(): List<DecryptedCredential> =
        withContext(Dispatchers.Default) { dao().getAll().map { it.decrypt() } }

    suspend fun getById(id: String): DecryptedCredential? =
        withContext(Dispatchers.Default) { dao().getById(id)?.decrypt() }

    /** Full detail including the credential's web-domain / package-name matches, for the edit screen. */
    suspend fun getDetailById(id: String): DecryptedCredential? = withContext(Dispatchers.Default) {
        val entity = dao().getById(id) ?: return@withContext null
        val matches = dao().matchesFor(id)
        entity.decrypt().copy(
            webDomain = matches.firstOrNull { it.matchType == MatchType.WEB_DOMAIN }?.matchValue,
            packageName = matches.firstOrNull { it.matchType == MatchType.PACKAGE_NAME }?.matchValue
        )
    }

    suspend fun findForPackageName(packageName: String): List<DecryptedCredential> =
        withContext(Dispatchers.Default) { dao().findByMatch(MatchType.PACKAGE_NAME, packageName).map { it.decrypt() } }

    suspend fun findForWebDomain(domain: String): List<DecryptedCredential> =
        withContext(Dispatchers.Default) { dao().findByMatch(MatchType.WEB_DOMAIN, normalizeDomain(domain)).map { it.decrypt() } }

    suspend fun markUsed(id: String) = dao().markUsed(id, System.currentTimeMillis())

    private fun CredentialEntity.decrypt(): DecryptedCredential {
        val cipher = session.currentFieldCipher()
        val username = String(cipher.decrypt(EncryptedBlob(usernameCipherIv, usernameCipherBytes)), Charsets.UTF_8)
        val password = String(cipher.decrypt(EncryptedBlob(passwordCipherIv, passwordCipherBytes)), Charsets.UTF_8)
        val notes = if (notesCipherIv != null && notesCipherBytes != null) {
            String(cipher.decrypt(EncryptedBlob(notesCipherIv, notesCipherBytes)), Charsets.UTF_8)
        } else null
        return DecryptedCredential(id, label, username, password, notes)
    }

    private fun normalizeDomain(raw: String): String =
        raw.substringAfter("://").removePrefix("www.").substringBefore("/").lowercase()
}
