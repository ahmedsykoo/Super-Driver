package com.superdriver.app.prefs

import android.content.Context
import com.superdriver.app.core.VerdictEngine
import com.superdriver.app.util.AppLocales

/** Tiny wrapper around SharedPreferences. No database, no history, no stats. */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** From this rate upwards the offer is "مناسب". */
    var goodAtLeast: Double
        get() = prefs.getFloat(KEY_GOOD, VerdictEngine.DEFAULT_GOOD_AT_LEAST.toFloat()).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_GOOD, value.toFloat()).apply()
        }

    /** From this rate upwards the offer is "قريب". */
    var nearAtLeast: Double
        get() = prefs.getFloat(KEY_NEAR, VerdictEngine.DEFAULT_NEAR_AT_LEAST.toFloat()).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_NEAR, value.toFloat()).apply()
        }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_OVERLAY, value).apply()
        }

    var ocrEnabled: Boolean
        get() = prefs.getBoolean(KEY_OCR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_OCR, value).apply()
        }

    var languageTag: String
        get() = prefs.getString(KEY_LANGUAGE, AppLocales.ARABIC) ?: AppLocales.ARABIC
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
        }

    var overlayX: Int
        get() = prefs.getInt(KEY_OVERLAY_X, Int.MIN_VALUE)
        set(value) {
            prefs.edit().putInt(KEY_OVERLAY_X, value).apply()
        }

    var overlayY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, DEFAULT_OVERLAY_Y)
        set(value) {
            prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "super_driver_settings"
        private const val KEY_GOOD = "good_at_least"
        private const val KEY_NEAR = "near_at_least"
        private const val KEY_OVERLAY = "overlay_enabled"
        private const val KEY_OCR = "ocr_enabled"
        private const val KEY_LANGUAGE = "language_tag"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"

        const val DEFAULT_OVERLAY_Y = 120
    }
}
