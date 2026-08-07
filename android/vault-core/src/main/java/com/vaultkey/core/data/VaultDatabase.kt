package com.vaultkey.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import net.zetetic.database.sqlcipher.SupportFactory

@Dao
interface CredentialDao {
    @Insert
    suspend fun insertCredential(credential: CredentialEntity)

    @Insert
    suspend fun insertMatch(match: CredentialMatchEntity)

    @Query("SELECT * FROM credentials ORDER BY label ASC")
    suspend fun getAll(): List<CredentialEntity>

    // Core lookup used by both the IME suggestion injector and the Autofill service.
    @Query(
        """
        SELECT c.* FROM credentials c
        INNER JOIN credential_matches m ON m.credentialId = c.id
        WHERE m.matchType = :matchType AND m.matchValue = :matchValue
        """
    )
    suspend fun findByMatch(matchType: MatchType, matchValue: String): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CredentialEntity?

    @Query("SELECT * FROM credential_matches WHERE credentialId = :id")
    suspend fun matchesFor(id: String): List<CredentialMatchEntity>

    @Update
    suspend fun updateCredential(credential: CredentialEntity)

    @Delete
    suspend fun deleteCredential(credential: CredentialEntity)

    @Query("DELETE FROM credential_matches WHERE credentialId = :id")
    suspend fun deleteMatchesFor(id: String)

    @Query("UPDATE credentials SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markUsed(id: String, timestamp: Long)
}

class Converters {
    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name
    @TypeConverter
    fun toMatchType(value: String): MatchType = MatchType.valueOf(value)
}

@Database(entities = [CredentialEntity::class, CredentialMatchEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao

    companion object {
        /**
         * [passphrase] is the raw DB key, itself unwrapped moments earlier via
         * PasswordKeyDerivation (master password) or BiometricUnlock (Keystore)
         * — never hardcoded, never logged.
         */
        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            // net.zetetic:sqlcipher-android's SupportFactory zeroes the
            // passphrase array once the DB is opened (clearPassphrase defaults
            // to true). Hand it a throwaway clone so it does NOT wipe the live
            // session key held in VaultSession.rawDbKey (and the FieldCipher
            // derived from it) out from under us on the first query.
            val factory = SupportFactory(passphrase.clone())
            return Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                .openHelperFactory(factory)
                // NO destructive fallback: a version bump without a matching
                // Migration must fail loudly rather than silently wipe a real
                // user's vault. When the schema changes, bump `version` above
                // and register the Migration here via `.addMigrations(...)`.
                .build()
        }
    }
}

// Matching now lives in CredentialRepository (which wraps this DAO plus
// FieldCipher for decryption) — see data/CredentialRepository.kt. Keeping a
// single repository, rather than a parallel MatchingEngine, means the
// keyboard, the autofill service, and the vault UI all read through the
// exact same decrypt path.
