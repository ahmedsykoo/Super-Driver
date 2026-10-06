package com.superdriver.engine

enum class ButtonKind { MATCH, ACCEPT }

enum class DistanceSource { KEYWORDS, FALLBACK_ORDER }

enum class UnreadableReason {
    PRICE_MISSING, PRICE_AMBIGUOUS, PRICE_FORMAT,
    DISTANCE_MISSING, DISTANCE_AMBIGUOUS, DISTANCE_FORMAT,
    NON_POSITIVE,
}

data class RideOffer(
    val price: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val button: ButtonKind,
    val distanceSource: DistanceSource,
)

sealed interface ParseResult {
    /** Not a ride-request screen (no Match/Accept button). Ignore silently. */
    data object NotRideScreen : ParseResult

    /** A ride-request screen whose numbers could not be read with certainty. Never show a number. */
    data class Unreadable(val reason: UnreadableReason) : ParseResult

    data class Ok(val offer: RideOffer) : ParseResult
}

/**
 * Parses the texts of the nodes of the Uber driver screen (text + contentDescription, in tree order).
 * Pure Kotlin: no android.* imports.
 */
object RideParser {
    private const val NUM = "\\d+(?:[.,]\\d+)*"
    private const val UNIT = "(?:كلم|كم|km)\\.?"
    private const val CUR = "(?:ج\\.?م(?!\\p{L})|egp(?![a-z]))"
    private val I = RegexOption.IGNORE_CASE

    private val BOOST = Regex("\\+?\\s*boost[^\\d]{0,40}?$NUM(?:\\s*$CUR)?", I)
    private val PRICE_AFTER = Regex("($NUM)\\s*$CUR", I)
    private val PRICE_BEFORE = Regex("(?<![a-z])egp\\s*($NUM)", I)

    private val PICKUP_KEY = Regex("عل[ىي]\\s*بعد")
    private val TRIP_KEY = Regex("مشوار")
    private val PICKUP = Regex("عل[ىي]\\s*بعد[^()]{0,40}\\(\\s*($NUM)\\s*$UNIT\\s*\\)", I)
    private val TRIP = Regex("مشوار[^()]{0,60}\\(\\s*(?:لمسافة\\s*)?($NUM)\\s*$UNIT\\s*\\)", I)
    private val ANY_DISTANCE = Regex("\\(\\s*[^()\\d]{0,20}($NUM)\\s*$UNIT\\s*\\)", I)

    // Button labels are matched against a whole node. T2 diagnostics may show they need loosening.
    private val BUTTONS = mapOf(
        "تطابق" to ButtonKind.MATCH,
        "اقبل" to ButtonKind.ACCEPT,
        "match" to ButtonKind.MATCH,
        "accept" to ButtonKind.ACCEPT,
    )

    fun parse(nodeTexts: List<String>): ParseResult {
        val nodes = nodeTexts.map(TextNormalizer::normalize).filter { it.isNotEmpty() }
            .fold(ArrayList<String>()) { acc, s -> if (acc.lastOrNull() != s) acc.add(s); acc }

        val button = nodes.firstNotNullOfOrNull { n -> BUTTONS[n.trim('.', ' ').lowercase()] }
            ?: return ParseResult.NotRideScreen

        val text = BOOST.replace(nodes.joinToString(" "), " ") // Boost is already inside the price: never add it.

        val prices = LinkedHashSet<Double>()
        for (m in PRICE_AFTER.findAll(text)) prices += parseNumber(m.groupValues[1]) ?: return unreadable(UnreadableReason.PRICE_FORMAT)
        for (m in PRICE_BEFORE.findAll(text)) prices += parseNumber(m.groupValues[1]) ?: return unreadable(UnreadableReason.PRICE_FORMAT)
        if (prices.isEmpty()) return unreadable(UnreadableReason.PRICE_MISSING)
        if (prices.size > 1) return unreadable(UnreadableReason.PRICE_AMBIGUOUS)
        val price = prices.first()

        val pickupVals = numbers(PICKUP, text) ?: return unreadable(UnreadableReason.DISTANCE_FORMAT)
        val tripVals = numbers(TRIP, text) ?: return unreadable(UnreadableReason.DISTANCE_FORMAT)

        val pickup: Double
        val trip: Double
        val source: DistanceSource
        if (pickupVals.isNotEmpty() && tripVals.isNotEmpty()) {
            if (pickupVals.size > 1 || tripVals.size > 1) return unreadable(UnreadableReason.DISTANCE_AMBIGUOUS)
            pickup = pickupVals.first(); trip = tripVals.first(); source = DistanceSource.KEYWORDS
        } else if (!PICKUP_KEY.containsMatchIn(text) && !TRIP_KEY.containsMatchIn(text)) {
            // Unconfirmed English fallback: exactly two distance parentheses, first = pickup, second = trip.
            val all = ANY_DISTANCE.findAll(text).toList()
            if (all.size != 2) return unreadable(UnreadableReason.DISTANCE_MISSING)
            pickup = parseNumber(all[0].groupValues[1]) ?: return unreadable(UnreadableReason.DISTANCE_FORMAT)
            trip = parseNumber(all[1].groupValues[1]) ?: return unreadable(UnreadableReason.DISTANCE_FORMAT)
            source = DistanceSource.FALLBACK_ORDER
        } else {
            return unreadable(UnreadableReason.DISTANCE_MISSING)
        }

        if (price <= 0.0 || trip <= 0.0 || pickup < 0.0) return unreadable(UnreadableReason.NON_POSITIVE)
        return ParseResult.Ok(RideOffer(price, pickup, trip, button, source))
    }

    private fun unreadable(r: UnreadableReason) = ParseResult.Unreadable(r)

    /** Distinct values of group 1 for every match; null if any token has an invalid number format. */
    private fun numbers(re: Regex, text: String): List<Double>? {
        val out = LinkedHashSet<Double>()
        for (m in re.findAll(text)) out += parseNumber(m.groupValues[1]) ?: return null
        return out.toList()
    }

    /**
     * Rule 4: last separator followed by 1-2 digits = decimal (earlier separators = thousands);
     * followed by 3 digits = thousands; anything else = unreadable (null).
     */
    internal fun parseNumber(token: String): Double? {
        val parts = token.split('.', ',')
        if (parts.any { it.isEmpty() }) return null
        if (parts.size == 1) return token.toDoubleOrNull()
        val head = parts.dropLast(1)
        val last = parts.last()
        if (!head.drop(1).all { it.length == 3 }) return null
        return when (last.length) {
            1, 2 -> (head.joinToString("") + "." + last).toDoubleOrNull()
            3 -> if (head.first().length in 1..3) parts.joinToString("").toDoubleOrNull() else null
            else -> null
        }
    }
}
