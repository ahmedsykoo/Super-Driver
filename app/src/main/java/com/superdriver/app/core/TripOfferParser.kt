package com.superdriver.app.core

/**
 * Turns the raw texts of the Uber Driver screen (accessibility nodes or OCR
 * lines) into a [TripOffer].
 *
 * Design rules:
 *  - Egyptian pound only: "ج.م" / "جنيه" / "EGP" / "LE" / "E£". No other
 *    currency is ever recognized, and a screen without an EGP price is ignored
 *    instead of being guessed.
 *  - Arabic and English layouts are both supported, with ASCII or Arabic-Indic
 *    digits, and with the value written before or after the unit (RTL).
 *  - Nothing is returned unless a price AND a distance were actually read; a
 *    wrong number is worse than no answer at all.
 */
object TripOfferParser {

    private const val MIN_PRICE = 5.0
    private const val MAX_PRICE = 200_000.0
    private const val MIN_KM = 0.02
    private const val MAX_KM = 400.0

    // ------------------------------------------------------------------ price

    /** "ج.م" / "ج م" / "جنيه" / "جم" — not followed by another Arabic letter. */
    private const val AR_CURRENCY = "(?:ج\\s?\\.\\s?م|ج\\s?م|جنيهان|جنيها|جنيه|جم)\\.?(?![ء-ي])"

    /** "EGP" / "LE" / "L.E." / "E£" */
    private const val EN_CURRENCY = "(?:egp|le|l\\.e\\.?|e£)"

    private const val NUM = "([0-9]+(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]+)?)"

    private val PRICE_AR_PREFIX = Regex("""$AR_CURRENCY\s*$NUM""")
    private val PRICE_AR_SUFFIX = Regex("""$NUM\s*$AR_CURRENCY""")
    private val PRICE_EN_PREFIX = Regex("""(?i)(?<![a-z])$EN_CURRENCY\s*$NUM(?![a-z])""")
    private val PRICE_EN_SUFFIX = Regex("""(?i)$NUM\s*$EN_CURRENCY(?![a-z])""")

    private val PRICE_PATTERNS =
        listOf(PRICE_AR_PREFIX, PRICE_AR_SUFFIX, PRICE_EN_PREFIX, PRICE_EN_SUFFIX)

    private const val ANY_CURRENCY_AFTER = """(?!\s*(?:ج\s?\.\s?م|ج\s?م|جنيه|جم|egp|le|l\.e\.?|e£))"""

    // --------------------------------------------------------------- distance

    private const val KM_UNIT = "(كيلومترات|كيلومتر|كيلو|كم|kms|km|kilometers|kilometres|kilometer|kilometre)"
    private const val M_UNIT = "(أمتار|مترات|متر|م|m|meters|metres|meter|metre|mtrs|mtr)"

    /** "8.5 كم" */
    private val KM_LTR = Regex("""(?i)(?<![0-9])$NUM\s*$KM_UNIT(?![0-9a-zء-ي])""")
    /** "كم 8.5" — RTL order: the unit must start the line or follow a space,
     *  and the number must not be the price of the trip. */
    private val KM_RTL = Regex("""(?i)(?<![^\s])$KM_UNIT\s*$NUM(?![0-9.,])$ANY_CURRENCY_AFTER""")
    /** "500 م" */
    private val M_LTR = Regex("""(?i)(?<![0-9])$NUM\s*$M_UNIT(?![0-9a-zء-ي])""")
    /** "م 500" — same guard: "ج.م 100" must never be read as 100 metres. */
    private val M_RTL = Regex("""(?i)(?<![^\s])$M_UNIT\s*$NUM(?![0-9.,])$ANY_CURRENCY_AFTER""")

    // ------------------------------------------------------- context keywords

    /** Lines about the pickup leg (distance from the driver to the rider). */
    private val PICKUP_HINTS = Regex(
        """(?i)(pick ?up|pickup|away|arrive|وصول|الوصول|استلام|التقاط|اليك|انتظار|الانتظار|على بعد|للوصول|ركوب|من موقعك|مكانك)"""
    )

    /** Lines about the paid leg (rider pickup -> destination). */
    private val TRIP_HINTS = Regex(
        """(?i)(trip|destination|drop ?off|رحله|الرحله|الوجهه|وجهه|توصيل|مشوار|المشوار|طول الرحله|الي الوجهه)"""
    )

    /** Lines that explicitly name the fare of the current offer. */
    private val FARE_HINTS = Regex(
        """(?i)(fare|price|total|اجره|الاجره|السعر|المبلغ|سعر الرحله|قيمه الرحله|التكلفه)"""
    )

    /** Lines that carry money but are NOT the fare of this offer. */
    private val MONEY_NOISE = Regex(
        """(?i)(مكافاه|حافز|bonus|incentiv|promo|tip|بقشيش|اكراميه|رصيد|محفظه|wallet|earnings|ارباح|اسبوعيه|weekly|كاش)"""
    )

    // ------------------------------------------------ tokens split over nodes

    private val CURRENCY_ONLY = Regex("""(?i)^(?:ج\s?\.\s?م|ج\s?م|جنيهان|جنيها|جنيه|جم|egp|le|l\.e\.?|e£)\.?$""")
    private val NUMBER_ONLY = Regex("""^[0-9]+(?:[.,][0-9]+)?$""")
    /** ".50" — the fraction rendered in its own view. */
    private val FRACTION_ONLY = Regex("""^[.,][0-9]+$""")
    private val DISTANCE_UNIT_ONLY = Regex(
        """(?i)^(?:كيلومترات|كيلومتر|كيلو|كم|kms|km|kilometers|kilometres|kilometer|kilometre|أمتار|مترات|متر|م|meters|metres|meter|metre|mtrs|mtr)$"""
    )

    /**
     * Lines produced by our own overlay: they can leak into an OCR pass and
     * must never be read as an offer. Matched on the raw text as well, because
     * the OCR digit fix turns "Super Driver" into "5uper Driver".
     */
    private val OWN_OVERLAY = Regex(
        """(?i)(super ?driver|سوبر درايفر|ج\.م/كم|egp/km|في انتظار|waiting for|مناسب|suitable)"""
    )

    /** Order in which split tokens are re-attached. See [mergeSplitTokens]. */
    private enum class MergeRule {
        FRACTION,
        CURRENCY_AFTER_VALUE,
        CURRENCY_BEFORE_VALUE,
        DISTANCE_UNIT
    }

    private data class PriceHit(val value: Double, val namedFare: Boolean)

    private data class DistanceHit(
        val km: Double,
        val lineIndex: Int,
        val unitIndex: Int,
        val line: String
    )

    /** Entry point. Returns null when the screen does not show a readable offer. */
    fun parse(rawLines: List<String>, source: ReadSource = ReadSource.ACCESSIBILITY): TripOffer? {
        val prepared = rawLines
            .asSequence()
            .filterNot { OWN_OVERLAY.containsMatchIn(it) }
            .map { if (source == ReadSource.OCR) TextNormalizer.fixOcrDigits(it) else it }
            .map { TextNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .filterNot { OWN_OVERLAY.containsMatchIn(it) }
            .toList()

        if (prepared.isEmpty()) return null

        val lines = mergeSplitTokens(prepared)
        val price = extractPrice(lines) ?: return null

        val hits = extractDistances(lines)
        if (hits.isEmpty()) return null
        val (pickupKm, tripKm) = splitDistances(hits)

        return TripOffer(
            priceEgp = price,
            pickupKm = pickupKm,
            tripKm = tripKm,
            serviceName = UberServices.detect(lines),
            source = source
        )
    }

    /**
     * Uber often renders a value and its unit in two sibling views
     * ("150.00" | "ج.م", "2.5" | "كم"), which arrive as two separate lines.
     * Re-attaching them here keeps the regexes simple.
     */
    internal fun mergeSplitTokens(lines: List<String>): List<String> {
        var result = lines
        var attempt = 0
        while (attempt < 3) {
            attempt++
            var next = mergePass(result, MergeRule.FRACTION)
            next = mergePass(next, MergeRule.CURRENCY_AFTER_VALUE)
            next = mergePass(next, MergeRule.CURRENCY_BEFORE_VALUE)
            next = mergePass(next, MergeRule.DISTANCE_UNIT)
            if (next.size == result.size) break
            result = next
        }
        return result
    }

    private fun mergePass(lines: List<String>, rule: MergeRule): List<String> {
        val out = ArrayList<String>(lines.size)
        var index = 0
        while (index < lines.size) {
            val current = lines[index]
            val next = lines.getOrNull(index + 1)
            if (next != null && isSplittablePair(current, next, rule)) {
                // A fraction is glued to its value, everything else is spaced.
                out.add(if (rule == MergeRule.FRACTION) current + next else "$current $next")
                index += 2
                continue
            }
            out.add(current)
            index += 1
        }
        return out
    }

    private fun isSplittablePair(first: String, second: String, rule: MergeRule): Boolean {
        val firstNumber = NUMBER_ONLY.matches(first)
        val secondNumber = NUMBER_ONLY.matches(second)
        val firstCurrency = CURRENCY_ONLY.matches(first)
        val secondCurrency = CURRENCY_ONLY.matches(second)
        val firstUnit = DISTANCE_UNIT_ONLY.matches(first)
        val secondUnit = DISTANCE_UNIT_ONLY.matches(second)
        val secondFraction = FRACTION_ONLY.matches(second)

        return when (rule) {
            MergeRule.FRACTION -> firstNumber && secondFraction
            MergeRule.CURRENCY_AFTER_VALUE -> firstNumber && secondCurrency
            MergeRule.CURRENCY_BEFORE_VALUE -> firstCurrency && secondNumber
            MergeRule.DISTANCE_UNIT -> (firstNumber && secondUnit) || (firstUnit && secondNumber)
        }
    }

    /**
     * Picks the fare of the offer. Currency-anchored numbers win; a line that
     * explicitly names the fare wins over the others; otherwise the biggest
     * plausible amount on the card is used (the fare is the prominent number).
     */
    internal fun extractPrice(lines: List<String>): Double? {
        val hits = ArrayList<PriceHit>()

        for (line in lines) {
            if (MONEY_NOISE.containsMatchIn(line)) continue
            val namedFare = FARE_HINTS.containsMatchIn(line)
            for (pattern in PRICE_PATTERNS) {
                for (match in pattern.findAll(line)) {
                    val value = TextNormalizer.number(match.groupValues[1]) ?: continue
                    if (value < MIN_PRICE || value > MAX_PRICE) continue
                    hits.add(PriceHit(value, namedFare))
                }
            }
        }

        // Ascending comparator + maxWithOrNull: an explicit "fare" line wins,
        // otherwise the biggest plausible amount on the card wins.
        // (A descending comparator with maxWithOrNull returns the SMALLEST
        // element, which is exactly what we do not want here.)
        val best = hits.maxWithOrNull(
            compareBy<PriceHit> { it.namedFare }.thenBy { it.value }
        )
        if (best != null) return best.value

        // No currency symbol on the card: only trust a line that names the fare.
        for (line in lines) {
            if (!FARE_HINTS.containsMatchIn(line) || MONEY_NOISE.containsMatchIn(line)) continue
            val value = Regex(NUM).findAll(line)
                .mapNotNull { TextNormalizer.number(it.groupValues[1]) }
                .filter { it in MIN_PRICE..MAX_PRICE }
                .maxOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun extractDistances(lines: List<String>): List<DistanceHit> {
        val raw = ArrayList<DistanceHit>()

        lines.forEachIndexed { lineIndex, line ->
            for (match in KM_LTR.findAll(line)) {
                addDistance(raw, match, 1.0, lineIndex, line, numberGroup = 1, unitGroup = 2, unitAtEnd = true)
            }
            for (match in KM_RTL.findAll(line)) {
                addDistance(raw, match, 1.0, lineIndex, line, numberGroup = 2, unitGroup = 1, unitAtEnd = false)
            }
            for (match in M_LTR.findAll(line)) {
                addDistance(raw, match, 0.001, lineIndex, line, numberGroup = 1, unitGroup = 2, unitAtEnd = true)
            }
            for (match in M_RTL.findAll(line)) {
                addDistance(raw, match, 0.001, lineIndex, line, numberGroup = 2, unitGroup = 1, unitAtEnd = false)
            }
        }

        // "8.5 كم 15 دقيقه" matches both LTR ("8.5 كم") and RTL ("كم 15"):
        // the same unit token is used twice, so keep the first reading only.
        val byUnit = LinkedHashMap<String, DistanceHit>()
        for (hit in raw) {
            byUnit.getOrPut("${hit.lineIndex}:${hit.unitIndex}") { hit }
        }

        return byUnit.values.sortedWith(
            compareBy<DistanceHit> { it.lineIndex }.thenBy { it.unitIndex }
        )
    }

    private fun addDistance(
        hits: MutableList<DistanceHit>,
        match: MatchResult,
        factor: Double,
        lineIndex: Int,
        line: String,
        numberGroup: Int,
        unitGroup: Int,
        unitAtEnd: Boolean
    ) {
        val value = TextNormalizer.number(match.groupValues[numberGroup]) ?: return
        val km = value * factor
        if (km < MIN_KM || km > MAX_KM) return
        val unitLength = match.groupValues[unitGroup].length
        val unitIndex = if (unitAtEnd) match.range.last - unitLength + 1 else match.range.first
        hits.add(DistanceHit(km, lineIndex, unitIndex, line))
    }

    /**
     * Splits the distances found on the card into the pickup leg and the trip
     * leg. When the card does not label them, reading order is used: Uber always
     * shows "to the rider" first and "to the destination" second.
     */
    private fun splitDistances(hits: List<DistanceHit>): Pair<Double?, Double?> {
        if (hits.isEmpty()) return null to null

        var pickup = hits.indexOfFirst { isPickupLine(it.line) && !isTripLine(it.line) }
        var trip = hits.indexOfFirst { isTripLine(it.line) && !isPickupLine(it.line) }

        when {
            pickup < 0 && trip < 0 -> {
                pickup = 0
                trip = 1
            }
            pickup < 0 -> pickup = hits.indices.firstOrNull { it != trip } ?: -1
            trip < 0 -> trip = hits.indices.firstOrNull { it != pickup } ?: -1
        }

        return hits.getOrNull(pickup)?.km to hits.getOrNull(trip)?.km
    }

    private fun isPickupLine(line: String): Boolean = PICKUP_HINTS.containsMatchIn(line)

    private fun isTripLine(line: String): Boolean = TRIP_HINTS.containsMatchIn(line)
}
