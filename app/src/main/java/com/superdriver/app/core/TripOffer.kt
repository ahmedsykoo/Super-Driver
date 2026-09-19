package com.superdriver.app.core

/** Where the text of an offer came from. Useful for logging and debugging. */
enum class ReadSource {
    ACCESSIBILITY,
    OCR
}

/**
 * A single trip offer read from the Uber Driver screen.
 *
 * Only three values are extracted: price (EGP), pickup distance and trip
 * distance (km), plus the service name when it is visible. The only derived
 * value is [egpPerKm] = price / total distance. Nothing else is computed.
 */
data class TripOffer(
    val priceEgp: Double,
    val pickupKm: Double? = null,
    val tripKm: Double? = null,
    val serviceName: String? = null,
    val source: ReadSource = ReadSource.ACCESSIBILITY
) {

    /**
     * Distance used for the verdict: pickup distance + trip distance, i.e. every
     * kilometre the driver has to drive for this offer.
     */
    val totalKm: Double = (pickupKm ?: 0.0) + (tripKm ?: 0.0)

    /** The only calculation performed by this app: price / distance. */
    val egpPerKm: Double?
        get() = if (totalKm > 0.0) priceEgp / totalKm else null

    /** Stable identity of an offer, used to avoid re-announcing the same card. */
    val key: String
        get() = listOf(
            serviceName ?: "-",
            "%.2f".format(java.util.Locale.US, priceEgp),
            "%.2f".format(java.util.Locale.US, pickupKm ?: -1.0),
            "%.2f".format(java.util.Locale.US, tripKm ?: -1.0)
        ).joinToString("|")

    fun verdict(goodAtLeast: Double, nearAtLeast: Double): Verdict? =
        egpPerKm?.let { VerdictEngine.evaluate(it, goodAtLeast, nearAtLeast) }
}
