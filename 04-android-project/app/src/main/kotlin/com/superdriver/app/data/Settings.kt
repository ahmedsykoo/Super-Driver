package com.superdriver.app.data

import com.superdriver.engine.Basis
import com.superdriver.engine.Evaluation
import com.superdriver.engine.RideOffer
import com.superdriver.engine.Thresholds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Settings(
    val thresholds: Thresholds,
    val basis: Basis,
    val vibrate: Boolean,
    val privacyAccepted: Boolean,
) {
    companion object {
        val DEFAULT = Settings(Thresholds.DEFAULT, Basis.INCLUSIVE, vibrate = true, privacyAccepted = false)
    }
}

private fun SettingsEntity.toSettings(): Settings = try {
    Settings(Thresholds(goodThreshold, nearThreshold), Basis.valueOf(basis), vibrate, privacyAccepted)
} catch (e: IllegalArgumentException) {
    Settings.DEFAULT.copy(vibrate = vibrate, privacyAccepted = privacyAccepted) // corrupted row: fall back to defaults
}

private fun Settings.toEntity() =
    SettingsEntity(1, thresholds.good, thresholds.near, basis.name, vibrate, privacyAccepted)

class SettingsRepository(private val dao: SettingsDao, scope: CoroutineScope) {
    /** In-memory copy for the service; starts at defaults (privacy not accepted) until the DB is read. */
    val settings: StateFlow<Settings> = dao.observe()
        .map { it?.toSettings() ?: Settings.DEFAULT }
        .stateIn(scope, SharingStarted.Eagerly, Settings.DEFAULT)

    suspend fun current(): Settings = dao.get()?.toSettings() ?: Settings.DEFAULT

    suspend fun update(change: (Settings) -> Settings) {
        dao.upsert(change(current()).toEntity())
    }
}

class TripLog(private val dao: TripDao, private val scope: CoroutineScope) {
    companion object { const val KEEP = 500 }

    fun record(offer: RideOffer, eval: Evaluation) {
        scope.launch {
            dao.insert(TripEntity(0, System.currentTimeMillis(), offer.price, offer.pickupKm, offer.tripKm, eval.judgedPerKm, eval.verdict.name))
            dao.trim(KEEP)
        }
    }

    fun clear() { scope.launch { dao.clear() } }
}
