package com.superdriver.app.core

/**
 * Best effort detection of the Uber service name shown on the offer card
 * (UberX, Comfort, UberXL, …). It is cosmetic only: when the name cannot be
 * read the offer is still analysed normally.
 *
 * Arabic keys use the normalized alphabet produced by [TextNormalizer]
 * (أ/إ/آ -> ا, ة -> ه).
 */
internal data class ServiceSpec(val display: String, val keys: List<String>) {
    constructor(display: String, vararg keys: String) : this(display, keys.toList())
}

object UberServices {

    /** Ordered from the most specific name to the most generic one. */
    private val SPECS = listOf(
        ServiceSpec(
            "Uber Comfort",
            "uber comfort", "comfort", "كومفورت", "كمفورت"
        ),
        ServiceSpec(
            "Uber Black",
            "uber black", "black", "اوبر بلاك", "بلاك"
        ),
        ServiceSpec(
            "UberXL",
            "uberxl", "uber xl", "اوبر اكس ال", "اكس ال"
        ),
        ServiceSpec(
            "UberX Share",
            "uberx share", "uber share", "share", "مشاركه", "شير"
        ),
        ServiceSpec(
            "Uber Connect",
            "uber connect", "connect", "كونكت", "كونيكت"
        ),
        ServiceSpec(
            "UberX",
            "uberx", "uber x", "اوبر اكس", "اوبراكس"
        ),
        ServiceSpec(
            "Uber Moto",
            "uber moto", "moto", "اوبر موتو", "موتو"
        ),
        ServiceSpec(
            "Uber Pet",
            "uber pet", "pet", "بيت"
        ),
        ServiceSpec(
            "Uber Assist",
            "uber assist", "assist", "مساعده"
        )
    )

    /**
     * Returns the canonical service name found in [lines], or null when the
     * screen does not show any known service.
     */
    fun detect(lines: List<String>): String? {
        for (line in lines) {
            val text = TextNormalizer.normalize(line).lowercase()
            if (text.isBlank()) continue
            for (spec in SPECS) {
                if (spec.keys.any { key -> text.contains(key) }) return spec.display
            }
        }
        return null
    }
}
