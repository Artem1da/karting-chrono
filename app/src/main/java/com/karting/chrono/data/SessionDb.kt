package com.karting.chrono.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "line_a_lat") val lineALat: Double,
    @ColumnInfo(name = "line_a_lon") val lineALon: Double,
    @ColumnInfo(name = "line_b_lat") val lineBLat: Double,
    @ColumnInfo(name = "line_b_lon") val lineBLon: Double,
)

@Entity(
    tableName = "lap",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("session_id")],
)
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "lap_index") val lapIndex: Int,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
)

@Entity(
    tableName = "track_point",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("session_id")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "ts_ms") val tsMs: Long,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Query("UPDATE session SET ended_at = :endedAt WHERE id = :id")
    suspend fun finishSession(id: Long, endedAt: Long)

    @Insert
    suspend fun insertLap(l: LapEntity): Long

    @Insert
    suspend fun insertPoint(p: TrackPointEntity): Long

    @Query("SELECT * FROM session ORDER BY started_at DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Query("SELECT * FROM session ORDER BY started_at DESC LIMIT 1")
    suspend fun getLatestSession(): SessionEntity?

    @Query("SELECT * FROM lap WHERE session_id = :sessionId ORDER BY lap_index")
    suspend fun getLaps(sessionId: Long): List<LapEntity>

    @Query("SELECT * FROM lap WHERE session_id = :sessionId ORDER BY lap_index")
    fun observeLaps(sessionId: Long): Flow<List<LapEntity>>

    @Query("SELECT * FROM track_point WHERE session_id = :sessionId ORDER BY ts_ms")
    suspend fun getPoints(sessionId: Long): List<TrackPointEntity>
}

@Database(
    entities = [SessionEntity::class, LapEntity::class, TrackPointEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: SessionDatabase? = null

        fun get(context: Context): SessionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SessionDatabase::class.java,
                "karting.db",
            )
                // Schema bumped from v1 to v2 (added track_point). On upgrade we
                // drop and recreate — old sessions are not worth preserving here.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
