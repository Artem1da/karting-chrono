package com.karting.chrono.sync

/**
 * Constants for the Wearable Data Layer paths used between watch and phone.
 *
 * Watch is the source of truth — it produces all DataItems. Phone reads them
 * via WearableListenerService and mirrors into its own Room database.
 *
 * Paths:
 *  - [PATH_ACTIVE_SESSION]   single DataItem; present iff a session is
 *                            currently running on the watch. UI uses it for
 *                            the "session active" banner.
 *  - [PATH_SESSION_PREFIX]   one DataItem per finished session, keyed by
 *                            sessionId. Holds the full lap and track payload.
 *  - [MSG_SESSION_STARTED]   fire-and-forget message sent at session start.
 *  - [MSG_SESSION_ENDED]     fire-and-forget message sent at session stop.
 */
object WearablePaths {
    const val PATH_ACTIVE_SESSION: String = "/karting/active"
    const val PATH_SESSION_PREFIX: String = "/karting/session/"

    fun pathForSession(sessionId: Long): String = "$PATH_SESSION_PREFIX$sessionId"

    const val MSG_SESSION_STARTED: String = "/karting/msg/started"
    const val MSG_SESSION_ENDED: String = "/karting/msg/ended"

    /** Capability declared by the watch app, queried by the phone to find peers. */
    const val CAP_WATCH: String = "karting_chrono_watch"
}
