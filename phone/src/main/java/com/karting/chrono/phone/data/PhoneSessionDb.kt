package com.karting.chrono.phone.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Phone-side mirror of the watch's session database. Populated entirely by
 * [com.karting.chrono.phone.sync.PhoneListenerService] from Wearable Data
 * Layer events.
 *
 * The watch's sessionId is used as the primary key here so a re-delivery of
 * a DataItem is idempotent (REPLACE strategy).
 */
@Entity(tableName = "session")
data class PhoneSessionEntity(
    @PrimaryKey @ColumnInfo(name = "watch_session_id") val watchSessionId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "line_a_lat") val lineALat: Double,
    @ColumnInfo(name = "line_a_lon") val lineALon: Double,
    @ColumnInfo(name = "line_b_lat") val lineBLat: Double,
    @ColumnInfo(name = "line_b_lon") val lineBLon: Double,
    @ColumnInfo(name = "lap_count") val lapCount: Int,
    @ColumnInfo(name = "best_lap_ms") val bestLapMs: Long?,
)

@Entity(
    tableName = "lap",
    primaryKeys = ["session_id", "lap_index"],
    foreignKeys = [ForeignKey(
        entity = PhoneSessionEntity::class,
        parentColumns = ["watch_session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("session_id")],
)
data class PhoneLapEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "lap_index") val lapIndex: Int,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
)

@Entity(
    tableName = "track_point",
    foreignKeys = [ForeignKey(
        entity = PhoneSessionEntity::class,
        parentColumns = ["watch_session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("session_id")],
)
data class PhoneTrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "ts_ms") val tsMs: Long,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double,
)

@Dao
interface PhoneSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(s: PhoneSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLaps(laps: List<PhoneLapEntity>)

    @Query("DELETE FROM track_point WHERE session_id = :sessionId")
    suspend fun clearTrack(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(points: List<PhoneTrackPointEntity>)

    @Query("SELECT * FROM session ORDER BY started_at DESC")
    fun observeSessions(): Flow<List<PhoneSessionEntity>>

    @Query("SELECT * FROM session WHERE watch_session_id = :id")
    fun observeSession(id: Long): Flow<PhoneSessionEntity?>

    @Query("SELECT * FROM lap WHERE session_id = :id ORDER BY lap_index")
    fun observeLaps(id: Long): Flow<List<PhoneLapEntity>>

    @Query("SELECT * FROM track_point WHERE session_id = :id ORDER BY ts_ms")
    fun observeTrack(id: Long): Flow<List<PhoneTrackPointEntity>>
}

@Database(
    entities = [PhoneSessionEntity::class, PhoneLapEntity::class, PhoneTrackPointEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PhoneSessionDatabase : RoomDatabase() {
    abstract fun dao(): PhoneSessionDao

    companion object {
        @Volatile private var instance: PhoneSessionDatabase? = null

        fun get(context: Context): PhoneSessionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PhoneSessionDatabase::class.java,
                "karting-phone.db",
            ).build().also { instance = it }
        }
    }
}
