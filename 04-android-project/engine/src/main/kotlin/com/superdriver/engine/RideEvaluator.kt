package com.superdriver.engine

import java.math.BigDecimal
import java.math.RoundingMode

enum class Verdict { GOOD, NEAR, BAD }

enum class Basis { INCLUSIVE, TRIP_ONLY }

data class Thresholds(val good: Double, val near: Double) {
    init {
        require(near > 0.0 && good > near) { "need 0 < near < good" }
    }

    companion object {
        const val DEFAULT_GOOD = 6.0
        const val DEFAULT_NEAR = 4.5
        val DEFAULT = Thresholds(DEFAULT_GOOD, DEFAULT_NEAR)
    }
}

data class Evaluation(
    val perKmTrip: Double,
    val perKmAll: Double,
    val basis: Basis,
    /** The judged value rounded to one decimal, i.e. exactly what the driver sees. */
    val judgedPerKm: Double,
    val verdict: Verdict,
)

object RideEvaluator {
    fun evaluate(o: RideOffer, t: Thresholds, basis: Basis): Evaluation {
        val perTrip = o.price / o.tripKm
        val perAll = o.price / (o.pickupKm + o.tripKm)
        val judged = round1(if (basis == Basis.INCLUSIVE) perAll else perTrip)
        val verdict = when {
            judged >= t.good -> Verdict.GOOD
            judged >= t.near -> Verdict.NEAR
            else -> Verdict.BAD
        }
        return Evaluation(perTrip, perAll, basis, judged, verdict)
    }

    fun round1(v: Double): Double = BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toDouble()
}

object ArabicFormat {
    /** "7.5" -> "٧٫٥" (or Latin digits when [arabicDigits] is false). */
    fun number(v: Double, decimals: Int = 1, arabicDigits: Boolean = true): String {
        val plain = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
        if (!arabicDigits) return plain
        return buildString {
            for (c in plain) append(
                when (c) {
                    in '0'..'9' -> '٠' + (c - '0')
                    '.' -> '٫'
                    else -> c
                }
            )
        }
    }
}
