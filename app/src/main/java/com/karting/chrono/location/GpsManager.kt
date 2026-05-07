package com.karting.chrono.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.karting.chrono.core.GpsSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GpsManager(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Cold flow of GPS samples. Requests 1 Hz updates with HIGH_ACCURACY priority;
     * the system delivers as fast as the hardware allows (1 Hz on Pixel Watch 3).
     * Caller is responsible for holding ACCESS_FINE_LOCATION at runtime.
     */
    @SuppressLint("MissingPermission")
    fun samples(): Flow<GpsSample> = callbackFlow {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(250L)
            .setWaitForAccurateLocation(false)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    trySend(loc.toSample())
                }
            }
        }
        client.requestLocationUpdates(req, cb, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(cb) }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): Location? = try {
        client.lastLocation.await()
    } catch (_: Exception) {
        null
    }
}

private fun Location.toSample(): GpsSample = GpsSample(
    timestampMs = time,
    latDeg = latitude,
    lonDeg = longitude,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    bearingDeg = if (hasBearing()) bearing.toDouble() else null,
)
