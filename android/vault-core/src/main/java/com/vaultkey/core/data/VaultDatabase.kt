package com.vaultkey.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import net.sqlcipher.database.SupportFactory

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
         * CryptoManager from the Keystore — never hardcoded, never logged.
         */
        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // replace with real migrations before shipping
                .build()
        }
    }
}

// Matching now lives in CredentialRepository (which wraps this DAO plus
// FieldCipher for decryption) — see data/CredentialRepository.kt. Keeping a
// single repository, rather than a parallel MatchingEngine, means the
// keyboard, the autofill service, and the vault UI all read through the
// exact same decrypt path.
