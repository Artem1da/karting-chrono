package com.karting.chrono.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.GpsSample
import com.karting.chrono.data.FinishLinePrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * View model for the setup screen. Holds the latest GPS fix used to define
 * the start/finish line, and the persisted line itself.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = FinishLinePrefs(app)

    data class GpsState(
        val latDeg: Double? = null,
        val lonDeg: Double? = null,
        val accuracyM: Double? = null,
        val headingDeg: Double? = null,
        val hasFix: Boolean = false,
    )

    private val _gps = MutableStateFlow(GpsState())
    val gps: StateFlow<GpsState> = _gps.asStateFlow()

    private val _line = MutableStateFlow<FinishLine?>(null)
    val line: StateFlow<FinishLine?> = _line.asStateFlow()

    private val _pendingPointA = MutableStateFlow<Pair<Double, Double>?>(null)
    val pendingPointA: StateFlow<Pair<Double, Double>?> = _pendingPointA.asStateFlow()

    private var collectJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.flow.collect { _line.value = it }
        }
    }

    fun onSample(sample: GpsSample, headingDeg: Double?) {
        _gps.update {
            it.copy(
                latDeg = sample.latDeg,
                lonDeg = sample.lonDeg,
                accuracyM = sample.accuracyM,
                headingDeg = headingDeg ?: it.headingDeg,
                hasFix = true,
            )
        }
    }

    fun captureStartLine() {
        val s = _gps.value
        if (!s.hasFix || s.latDeg == null || s.lonDeg == null) return
        _pendingPointA.value = s.latDeg to s.lonDeg
    }

    fun captureLineEnd() {
        val s = _gps.value
        val a = _pendingPointA.value ?: return
        if (!s.hasFix || s.latDeg == null || s.lonDeg == null) return
        val line = FinishLine(a.first, a.second, s.latDeg, s.lonDeg)
        viewModelScope.launch { prefs.save(line) }
        _pendingPointA.value = null
    }

    fun captureSinglePointWithHeading(widthM: Double = 10.0) {
        val s = _gps.value
        if (!s.hasFix || s.latDeg == null || s.lonDeg == null) return
        val heading = s.headingDeg ?: return
        val line = FinishLine.centeredOn(s.latDeg, s.lonDeg, heading, widthM)
        viewModelScope.launch { prefs.save(line) }
    }

    fun cancelPendingLine() {
        _pendingPointA.value = null
    }

    fun clearLine() {
        viewModelScope.launch { prefs.clear() }
    }
}
