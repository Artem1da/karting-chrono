package com.karting.chrono.phone.data

import com.karting.chrono.sync.ActiveSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-singleton holding the most recent live status pushed by the watch.
 *
 * Populated by the listener service whenever the active-session DataItem
 * changes; consumed by the phone UI for the "session active" banner.
 *
 * `null` means no session is currently running on the watch.
 */
object ActiveSessionStore {
    private val _state = MutableStateFlow<ActiveSessionStatus?>(null)
    val state: StateFlow<ActiveSessionStatus?> = _state.asStateFlow()

    fun set(status: ActiveSessionStatus?) {
        _state.value = status
    }
}
