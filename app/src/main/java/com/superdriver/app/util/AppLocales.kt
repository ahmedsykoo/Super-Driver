package com.superdriver.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The app exists in Arabic and English only. Anything else is removed:
 *  - resourceConfigurations in build.gradle.kts strips unused translations,
 *  - @xml/locales_config tells Android 13+ the supported list,
 *  - this object applies the choice made on the settings screen.
 */
object AppLocales {

    const val ARABIC = "ar"
    const val ENGLISH = "en"

    fun apply(tag: String) {
        val locales = when (tag) {
            ARABIC, ENGLISH -> LocaleListCompat.forLanguageTags(tag)
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Arabic is the default when the app has never been set explicitly. */
    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ARABIC
        return locales.get(0)?.language ?: ARABIC
    }
}
