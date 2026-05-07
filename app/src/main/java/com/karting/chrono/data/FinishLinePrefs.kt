package com.karting.chrono.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.karting.chrono.core.FinishLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.finishLineStore by preferencesDataStore(name = "finish_line")

private val A_LAT = doublePreferencesKey("a_lat")
private val A_LON = doublePreferencesKey("a_lon")
private val B_LAT = doublePreferencesKey("b_lat")
private val B_LON = doublePreferencesKey("b_lon")

class FinishLinePrefs(private val context: Context) {

    val flow: Flow<FinishLine?> = context.finishLineStore.data.map { p ->
        val aLat = p[A_LAT] ?: return@map null
        val aLon = p[A_LON] ?: return@map null
        val bLat = p[B_LAT] ?: return@map null
        val bLon = p[B_LON] ?: return@map null
        FinishLine(aLat, aLon, bLat, bLon)
    }

    suspend fun save(line: FinishLine) {
        context.finishLineStore.edit {
            it[A_LAT] = line.aLatDeg
            it[A_LON] = line.aLonDeg
            it[B_LAT] = line.bLatDeg
            it[B_LON] = line.bLonDeg
        }
    }

    suspend fun clear() {
        context.finishLineStore.edit { it.clear() }
    }
}
