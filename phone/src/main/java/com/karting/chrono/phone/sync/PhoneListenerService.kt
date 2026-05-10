package com.karting.chrono.phone.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.karting.chrono.phone.data.ActiveSessionStore
import com.karting.chrono.phone.data.PhoneLapEntity
import com.karting.chrono.phone.data.PhoneSessionDatabase
import com.karting.chrono.phone.data.PhoneSessionEntity
import com.karting.chrono.phone.data.PhoneTrackPointEntity
import com.karting.chrono.sync.ActiveSessionStatus
import com.karting.chrono.sync.SessionPayload
import com.karting.chrono.sync.WearablePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives Data Layer traffic from the paired watch and writes it to phone
 * Room storage. Always-on, declared in the manifest with intent filters
 * scoped to the /karting/ path prefix.
 */
class PhoneListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            val path = event.dataItem.uri.path ?: continue
            when {
                path == WearablePaths.PATH_ACTIVE_SESSION -> handleActiveSession(event)
                path.startsWith(WearablePaths.PATH_SESSION_PREFIX) -> handleFinishedSession(event)
            }
        }
    }

    private fun handleActiveSession(event: DataEvent) {
        if (event.type == DataEvent.TYPE_DELETED) {
            ActiveSessionStore.set(null)
            return
        }
        val map = DataMapItem.fromDataItem(event.dataItem).dataMap
        val status = ActiveSessionStatus.readFrom(map)
        ActiveSessionStore.set(status)
    }

    private fun handleFinishedSession(event: DataEvent) {
        if (event.type != DataEvent.TYPE_CHANGED) return
        val map = DataMapItem.fromDataItem(event.dataItem).dataMap
        val payload = runCatching { SessionPayload.readFrom(map) }
            .onFailure { Log.w(TAG, "Bad session payload", it) }
            .getOrNull() ?: return

        val dao = PhoneSessionDatabase.get(applicationContext).dao()
        scope.launch {
            val best = payload.laps.minOfOrNull { it.durationMs }
            dao.upsertSession(
                PhoneSessionEntity(
                    watchSessionId = payload.sessionId,
                    startedAt = payload.startedAtMs,
                    endedAt = payload.endedAtMs,
                    lineALat = payload.finishLine.aLatDeg,
                    lineALon = payload.finishLine.aLonDeg,
                    lineBLat = payload.finishLine.bLatDeg,
                    lineBLon = payload.finishLine.bLonDeg,
                    lapCount = payload.laps.size,
                    bestLapMs = best,
                )
            )
            dao.upsertLaps(payload.laps.map {
                PhoneLapEntity(
                    sessionId = payload.sessionId,
                    lapIndex = it.index,
                    startMs = it.startTimestampMs,
                    endMs = it.endTimestampMs,
                )
            })
            // Track points: replace wholesale rather than append, so retries
            // don't produce duplicates.
            dao.clearTrack(payload.sessionId)
            dao.insertTrack(payload.track.map {
                PhoneTrackPointEntity(
                    sessionId = payload.sessionId,
                    tsMs = it.timestampMs,
                    lat = it.latDeg,
                    lon = it.lonDeg,
                )
            })
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        // Messages are advisory; the DataItem at PATH_ACTIVE_SESSION is the
        // source of truth. We use the stop message to clear the live banner
        // promptly even before TYPE_DELETED arrives.
        when (event.path) {
            WearablePaths.MSG_SESSION_ENDED -> ActiveSessionStore.set(null)
        }
    }

    companion object {
        private const val TAG = "PhoneListenerSvc"
    }
}
