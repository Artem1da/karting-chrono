package com.karting.chrono

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class KartingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    TIMING_CHANNEL_ID,
                    "Karting timing session",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }
    }

    companion object {
        const val TIMING_CHANNEL_ID = "timing_session"
    }
}
