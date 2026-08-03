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
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface CredentialDao {
    @Insert
    suspend fun insertCredential(credential: CredentialEntity)

    @Insert
    suspend fun insertMatch(match: CredentialMatchEntity)

    @Query("SELECT * FROM credentials ORDER BY label ASC")
    suspend fun getAll(): List<CredentialEntity>

    // Reactive variant so the vault list updates itself the moment a
    // credential is added/removed, instead of loading once and going stale.
    @Query("SELECT * FROM credentials ORDER BY label ASC")
    fun observeAll(): Flow<List<CredentialEntity>>

    // Core lookup used by both the IME suggestion injector and the Autofill service.
    @Query(
        """
        SELECT c.* FROM credentials c
        INNER JOIN credential_matches m ON m.credentialId = c.id
        WHERE m.matchType = :matchType AND m.matchValue = :matchValue
        """
    )
    suspend fun findByMatch(matchType: MatchType, matchValue: String): List<CredentialEntity>

    @Query("UPDATE credentials SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markUsed(id: String, timestamp: Long)
}

class Converters {
    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name
    @TypeConverter
    fun toMatchType(value: String): MatchType = MatchType.valueOf(value)
}

// exportSchema = false: no schema JSON is emitted (we use fallbackToDestructiveMigration,
// not versioned migration testing) — silences Room's "schema export directory not provided" warning.
@Database(entities = [CredentialEntity::class, CredentialMatchEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao

    companion object {
        @Volatile
        private var nativeLibLoaded = false

        /** sqlcipher-android requires its native lib be loaded before any DB op. Idempotent. */
        private fun ensureNativeLibLoaded() {
            if (nativeLibLoaded) return
            synchronized(this) {
                if (!nativeLibLoaded) {
                    System.loadLibrary("sqlcipher")
                    nativeLibLoaded = true
                }
            }
        }

        /**
         * [passphrase] is the raw DB key, unwrapped moments earlier by
         * VaultSession (password + Keystore, or biometric) — never hardcoded,
         * never logged. The caller passes a private copy: SupportOpenHelperFactory
         * zeroes this array after opening the database.
         */
        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            ensureNativeLibLoaded()
            val factory = SupportOpenHelperFactory(passphrase)
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
