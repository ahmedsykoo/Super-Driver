package com.superdriver.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Storage choice (T5): Room for settings and for the local session cache. No trip log — per Ahmed's
 * decision, the app never stores or shows a history of rides read from Uber, on-device or otherwise.
 * No defaults on entity constructors on purpose: Room can pick the wrong constructor when all params have defaults.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int,
    val goodThreshold: Double,
    val nearThreshold: Double,
    val basis: String,
    val vibrate: Boolean,
    val privacyAccepted: Boolean,
    val autoDetectEnabled: Boolean,
)

/**
 * Local cache of the logged-in driver (T7). Supabase is the source of truth; this row only lets the
 * app skip asking for login/profile every time it reopens. One row (id = 1); absent = logged out.
 */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: Int,
    val email: String,
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val name: String,
    val city: String,
    val birthDate: String, // free text as typed (dd/mm/yyyy) — see LoginActivity
)

/**
 * Local cache of subscription status (T7b), refreshed from Supabase — never the source of truth
 * (handoff rule: "مصدر الحقيقة الوحيد السيرفر"). `cachedAtMillis` backs the 24h offline window.
 */
@Entity(tableName = "subscription")
data class SubscriptionEntity(
    @PrimaryKey val id: Int,
    val status: String, // "trial" | "active" | "expired"
    val plan: String, // "" | "monthly" | "yearly"
    val trialEndsAtMillis: Long,
    val activeUntilMillis: Long, // 0 = none
    val cachedAtMillis: Long,
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SettingsEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM session WHERE id = 1")
    fun observe(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE id = 1")
    suspend fun get(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SessionEntity)

    @Query("DELETE FROM session")
    suspend fun clear()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription WHERE id = 1")
    fun observe(): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscription WHERE id = 1")
    suspend fun get(): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SubscriptionEntity)

    @Query("DELETE FROM subscription")
    suspend fun clear()
}

@Database(
    entities = [SettingsEntity::class, SessionEntity::class, SubscriptionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun sessionDao(): SessionDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "superdriver.db").build()
    }
}
