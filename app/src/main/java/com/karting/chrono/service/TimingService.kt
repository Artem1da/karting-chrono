package com.karting.chrono.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.karting.chrono.KartingApp
import com.karting.chrono.MainActivity
import com.karting.chrono.R
import com.karting.chrono.core.DetectorEvent
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.GpsSample
import com.karting.chrono.core.Lap
import com.karting.chrono.core.LapDetector
import com.karting.chrono.core.LapDetectorConfig
import com.karting.chrono.data.FinishLinePrefs
import com.karting.chrono.data.LapEntity
import com.karting.chrono.data.SessionDatabase
import com.karting.chrono.data.SessionEntity
import com.karting.chrono.data.TrackPointEntity
import com.karting.chrono.location.GpsManager
import com.karting.chrono.sync.ActiveSessionStatus
import com.karting.chrono.sync.WatchSync
import com.karting.chrono.sync.WearablePaths
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the live timing session.
 *
 * Lifecycle:
 *  - START_TIMING: load the persisted finish line, open a Room session row,
 *    start a partial wake lock, subscribe to GPS, run the lap detector.
 *  - STOP_TIMING: close the session, persist any remaining laps, release
 *    resources, stop the service.
 *
 * The service exposes its current state via [State] (held in a singleton
 * [stateFlow]) so the UI can observe lap progress without binding.
 */
class TimingService : LifecycleService() {

    data class State(
        val running: Boolean = false,
        val sessionId: Long? = null,
        val lapStartMs: Long? = null,
        val lapCount: Int = 0,
        val lastLapMs: Long? = null,
        val bestLapMs: Long? = null,
        val laps: List<Lap> = emptyList(),
        /** Recent GPS samples for the live track view, capped to keep memory bounded. */
        val trackPoints: List<GpsSample> = emptyList(),
        val gpsHasFix: Boolean = false,
        val gpsAccuracyM: Double? = null,
        val gpsLastFixAtMs: Long? = null,
    )

    private var wakeLock: PowerManager.WakeLock? = null
    private var timingJob: Job? = null
    private var detector: LapDetector? = null
    private var sessionId: Long = -1L
    private var sessionStartedAtMs: Long = 0L
    private var sessionLine: FinishLine? = null
    private val sync by lazy { WatchSync(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTiming()
            ACTION_STOP -> stopTiming()
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startTiming() {
        if (timingJob != null) return
        startInForeground()
        acquireWakeLock()

        timingJob = lifecycleScope.launch {
            val ctx = this@TimingService
            val line: FinishLine = FinishLinePrefs(ctx).flow.first()
                ?: run { stopTiming(); return@launch }

            val db = SessionDatabase.get(ctx)
            val now = System.currentTimeMillis()
            sessionId = db.sessionDao().insertSession(
                SessionEntity(
                    startedAt = now,
                    endedAt = null,
                    lineALat = line.aLatDeg,
                    lineALon = line.aLonDeg,
                    lineBLat = line.bLatDeg,
                    lineBLon = line.bLonDeg,
                )
            )
            sessionStartedAtMs = now
            sessionLine = line

            val det = LapDetector(line, LapDetectorConfig())
            detector = det
            _state.update { it.copy(running = true, sessionId = sessionId) }

            // Phone-side banner: tell the companion a session is starting,
            // and post the initial active-session DataItem so any phone that
            // opens the app right now sees the live status.
            sync.broadcast(WearablePaths.MSG_SESSION_STARTED)
            sync.publishActiveSession(currentStatus())

            GpsManager(ctx).samples().collect { sample ->
                onGpsSample(sample, det, db)
            }
        }
    }

    private suspend fun onGpsSample(
        sample: GpsSample,
        det: LapDetector,
        db: SessionDatabase,
    ) {
        // Persist every fix to the track-point table for the post-session view.
        db.sessionDao().insertPoint(
            TrackPointEntity(
                sessionId = sessionId,
                tsMs = sample.timestampMs,
                lat = sample.latDeg,
                lon = sample.lonDeg,
            )
        )
        _state.update {
            it.copy(
                gpsHasFix = true,
                gpsAccuracyM = sample.accuracyM,
                gpsLastFixAtMs = System.currentTimeMillis(),
                lapStartMs = det.currentLapStartMs() ?: it.lapStartMs,
                // Keep the last ~1 hour at 1 Hz to bound memory.
                trackPoints = (it.trackPoints + sample).takeLast(MAX_LIVE_POINTS),
            )
        }
        when (val ev = det.onSample(sample)) {
            is DetectorEvent.LapCompleted -> {
                db.sessionDao().insertLap(
                    LapEntity(
                        sessionId = sessionId,
                        lapIndex = ev.lap.index,
                        startMs = ev.lap.startTimestampMs,
                        endMs = ev.lap.endTimestampMs,
                    )
                )
                val laps = det.laps()
                val best = laps.minOfOrNull { it.durationMs }
                _state.update {
                    it.copy(
                        laps = laps,
                        lapCount = laps.size,
                        lastLapMs = ev.lap.durationMs,
                        bestLapMs = best,
                        lapStartMs = det.currentLapStartMs(),
                    )
                }
                refreshNotification()
                sync.publishActiveSession(currentStatus())
            }
            is DetectorEvent.Idle,
            is DetectorEvent.FirstFix,
            is DetectorEvent.CrossingIgnored -> Unit
        }
    }

    private fun currentStatus(): ActiveSessionStatus {
        val s = _state.value
        return ActiveSessionStatus(
            sessionId = sessionId,
            startedAtMs = sessionStartedAtMs,
            lapStartMs = s.lapStartMs,
            lapCount = s.lapCount,
            bestLapMs = s.bestLapMs,
            lastLapMs = s.lastLapMs,
        )
    }

    private fun stopTiming() {
        val job = timingJob
        timingJob = null
        job?.cancel()

        val sid = sessionId
        val startedAt = sessionStartedAtMs
        val line = sessionLine
        val finalLaps = _state.value.laps
        val finalTrack = _state.value.trackPoints
        val endedAt = System.currentTimeMillis()

        if (sid > 0) {
            val db = SessionDatabase.get(this)
            lifecycleScope.launch {
                db.sessionDao().finishSession(sid, endedAt)
                // Push final session payload to the phone so the companion
                // app gets the full lap and track listing for offline browsing.
                if (line != null) {
                    sync.publishFinishedSession(
                        sessionId = sid,
                        startedAtMs = startedAt,
                        endedAtMs = endedAt,
                        line = line,
                        laps = finalLaps,
                        track = finalTrack,
                    )
                }
                sync.clearActiveSession()
                sync.broadcast(WearablePaths.MSG_SESSION_ENDED)
            }
        }
        sessionId = -1L
        sessionStartedAtMs = 0L
        sessionLine = null
        detector = null
        releaseWakeLock()
        // Keep the laps and track in state so the Summary screen can show them
        // immediately. Mark running=false so the UI knows the session ended.
        _state.update { it.copy(running = false, lapStartMs = null) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "karting:timing").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun startInForeground() {
        val notif = buildNotification(_state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun refreshNotification() {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(NOTIF_ID, buildNotification(_state.value))
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted; the foreground notification
                // posted at start time is still visible — silently skip updates.
            }
        }
    }

    private fun buildNotification(s: State): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = buildString {
            append("Lap ").append(s.lapCount)
            s.bestLapMs?.let { append(" • best ").append(formatLap(it)) }
        }
        return NotificationCompat.Builder(this, KartingApp.TIMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Karting session active")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val MAX_LIVE_POINTS = 3600 // 1 hr @ 1 Hz
        const val ACTION_START = "com.karting.chrono.START"
        const val ACTION_STOP = "com.karting.chrono.STOP"

        private val _state = MutableStateFlow(State())
        val stateFlow: StateFlow<State> = _state.asStateFlow()

        fun start(context: Context) {
            val i = Intent(context, TimingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TimingService::class.java).setAction(ACTION_STOP))
        }
    }
}

internal fun formatLap(ms: Long): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val hundredths = (ms % 1000) / 10
    return "%d:%02d.%02d".format(minutes, seconds, hundredths)
}
