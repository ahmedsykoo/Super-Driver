package com.superdriver.app.data

import com.superdriver.engine.Basis
import com.superdriver.engine.Thresholds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class Settings(
    val thresholds: Thresholds,
    val basis: Basis,
    val vibrate: Boolean,
    val privacyAccepted: Boolean,
    /** In-app only, no OS permission behind it — see OnboardingActivity's step 1 (Captain Pro reference). */
    val autoDetectEnabled: Boolean,
) {
    companion object {
        val DEFAULT = Settings(Thresholds.DEFAULT, Basis.INCLUSIVE, vibrate = true, privacyAccepted = false, autoDetectEnabled = false)
    }
}

private fun SettingsEntity.toSettings(): Settings = try {
    Settings(Thresholds(goodThreshold, nearThreshold), Basis.valueOf(basis), vibrate, privacyAccepted, autoDetectEnabled)
} catch (e: IllegalArgumentException) {
    Settings.DEFAULT.copy(vibrate = vibrate, privacyAccepted = privacyAccepted, autoDetectEnabled = autoDetectEnabled) // corrupted row: fall back to defaults
}

private fun Settings.toEntity() =
    SettingsEntity(1, thresholds.good, thresholds.near, basis.name, vibrate, privacyAccepted, autoDetectEnabled)

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
