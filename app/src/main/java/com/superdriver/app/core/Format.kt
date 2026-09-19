package com.superdriver.app.core

import java.util.Locale

/**
 * Display formatting. Western digits are used on purpose: they are the digits
 * drivers see on the Uber screen in Egypt.
 */
object Format {

    /** "150" or "85.50" — no trailing zeros when the amount is round. */
    fun money(value: Double): String =
        if (value % 1.0 == 0.0) "%.0f".format(Locale.US, value) else "%.2f".format(Locale.US, value)

    /** "8" / "8.5" / "0.25" */
    fun km(value: Double): String = when {
        value >= 100 -> "%.0f".format(Locale.US, value)
        value * 10 % 1.0 == 0.0 -> "%.1f".format(Locale.US, value)
        else -> "%.2f".format(Locale.US, value)
    }

    /** "18.75" */
    fun rate(value: Double): String = "%.2f".format(Locale.US, value)
}
