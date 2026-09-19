package com.superdriver.app.core

/** The only three answers this app may give for an offer. */
enum class Verdict {
    /** مناسب  — Suitable */
    SUITABLE,

    /** قريب  — Close / borderline */
    NEAR,

    /** غير مناسب  — Not suitable */
    NOT_SUITABLE
}

/**
 * Turns "EGP per km" into one of the three answers.
 *
 * Defaults are tuned for the Egyptian market and can be changed by the driver
 * from the settings screen (>= 10 suitable, >= 6 close, below that unsuitable).
 */
object VerdictEngine {

    const val DEFAULT_GOOD_AT_LEAST = 10.0
    const val DEFAULT_NEAR_AT_LEAST = 6.0

    fun evaluate(
        egpPerKm: Double,
        goodAtLeast: Double = DEFAULT_GOOD_AT_LEAST,
        nearAtLeast: Double = DEFAULT_NEAR_AT_LEAST
    ): Verdict {
        val near = nearAtLeast.coerceAtMost(goodAtLeast)
        return when {
            egpPerKm >= goodAtLeast -> Verdict.SUITABLE
            egpPerKm >= near -> Verdict.NEAR
            else -> Verdict.NOT_SUITABLE
        }
    }
}
