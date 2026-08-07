package com.vaultkey.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MatchType { PACKAGE_NAME, WEB_DOMAIN }

/**
 * One saved login. `usernameCipher` / `passwordCipher` / `notesCipher` are the
 * iv+ciphertext produced by FieldCipher.encrypt() (session-key AES-GCM) — never
 * plaintext, even in this DB, even though the whole SQLite file is itself
 * SQLCipher-encrypted at rest (defense in depth: DB-file compromise still
 * doesn't yield plaintext secrets directly).
 */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val label: String,
    val usernameCipherIv: ByteArray,
    val usernameCipherBytes: ByteArray,
    val passwordCipherIv: ByteArray,
    val passwordCipherBytes: ByteArray,
    val notesCipherIv: ByteArray?,
    val notesCipherBytes: ByteArray?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?
)

/**
 * A credential can match more than one app/site — e.g. the GitHub Android app's
 * package name AND github.com, so one saved login autofills in both places.
 */
@Entity(
    tableName = "credential_matches",
    foreignKeys = [ForeignKey(
        entity = CredentialEntity::class,
        parentColumns = ["id"],
        childColumns = ["credentialId"],
        onDelete = ForeignKey.CASCADE
    )],
    // Index the FK column so cascade deletes / parent updates don't full-scan
    // this table (Room warns about this otherwise).
    indices = [Index(value = ["credentialId"])]
)
data class CredentialMatchEntity(
    @PrimaryKey(autoGenerate = true) val matchId: Long = 0,
    val credentialId: String,
    val matchType: MatchType,
    // e.g. "com.github.android"  or  "github.com"
    val matchValue: String
)
