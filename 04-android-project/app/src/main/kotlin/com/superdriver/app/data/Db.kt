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
 * Storage choice (T5): Room for both settings and trip log. Room is already required for T6, so a single
 * storage dependency is used instead of adding DataStore. Settings are one row (id = 1).
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
)

/** Only numbers and the verdict are stored. No addresses, no street names. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val timeMillis: Long,
    val price: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val judgedPerKm: Double,
    val verdict: String,
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
interface TripDao {
    @Insert
    suspend fun insert(t: TripEntity)

    @Query("SELECT * FROM trips ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripEntity>>

    @Query("DELETE FROM trips WHERE id NOT IN (SELECT id FROM trips ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM trips")
    suspend fun clear()
}

@Database(entities = [SettingsEntity::class, TripEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun tripDao(): TripDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "superdriver.db").build()
    }
}
