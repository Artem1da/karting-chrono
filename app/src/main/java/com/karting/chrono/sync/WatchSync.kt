package com.karting.chrono.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.GpsSample
import com.karting.chrono.core.Lap
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around the Wearable DataClient + MessageClient used by the
 * watch app to broadcast session state to the paired phone.
 *
 * Failures are logged but never thrown — the watch app keeps timing even if
 * the phone is out of range.
 */
class WatchSync(private val context: Context) {

    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun publishActiveSession(status: ActiveSessionStatus) {
        runCatching {
            val req = PutDataMapRequest.create(WearablePaths.PATH_ACTIVE_SESSION).apply {
                status.writeTo(dataMap)
                setUrgent()
            }
            dataClient.putDataItem(req.asPutDataRequest()).await()
        }.onFailure { Log.w(TAG, "publishActiveSession failed", it) }
    }

    suspend fun clearActiveSession() {
        runCatching {
            val uri = android.net.Uri.parse("wear://*${WearablePaths.PATH_ACTIVE_SESSION}")
            dataClient.deleteDataItems(uri).await()
        }.onFailure { Log.w(TAG, "clearActiveSession failed", it) }
    }

    suspend fun publishFinishedSession(
        sessionId: Long,
        startedAtMs: Long,
        endedAtMs: Long?,
        line: FinishLine,
        laps: List<Lap>,
        track: List<GpsSample>,
    ) {
        runCatching {
            val payload = SessionPayload(
                sessionId = sessionId,
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                finishLine = line,
                laps = laps,
                track = track,
            )
            val req = PutDataMapRequest.create(
                WearablePaths.pathForSession(sessionId)
            ).apply {
                payload.writeTo(dataMap)
                setUrgent()
            }
            dataClient.putDataItem(req.asPutDataRequest()).await()
        }.onFailure { Log.w(TAG, "publishFinishedSession failed", it) }
    }

    suspend fun broadcast(messagePath: String, bytes: ByteArray = ByteArray(0)) {
        runCatching {
            val nodes = nodeClient.connectedNodes.await()
            for (n in nodes) {
                messageClient.sendMessage(n.id, messagePath, bytes).await()
            }
        }.onFailure { Log.w(TAG, "broadcast $messagePath failed", it) }
    }

    companion object {
        private const val TAG = "WatchSync"
    }
}
