package com.azeemzsyed.yardready.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user preferences, kept in SharedPreferences and exposed as flows. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("yard_ready_settings", Context.MODE_PRIVATE)

    private val _snowThresholdCm = MutableStateFlow(prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD_CM))
    val snowThresholdCm: StateFlow<Float> = _snowThresholdCm.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ALERTS, true))
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    fun setSnowThresholdCm(value: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, value).apply()
        _snowThresholdCm.value = value
    }

    fun setAlertsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ALERTS, value).apply()
        _alertsEnabled.value = value
    }

    companion object {
        const val DEFAULT_THRESHOLD_CM = 5f
        private const val KEY_THRESHOLD = "snow_threshold_cm"
        private const val KEY_ALERTS = "snow_alerts_enabled"
    }
}
