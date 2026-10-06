package com.superdriver.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic screens built from the handoff table (section 7); no real ride data. */
class RideEngineTest {
    private fun screen(
        price: String,
        pickupLine: String,
        tripLine: String,
        button: String = "تطابق",
        boost: String? = null,
    ): List<String> = listOfNotNull(
        "UberX", "٤٫٩٠", "$price ج.م.", "صافي رسوم الخدمة", "الدفع النقدي", boost,
        pickupLine, "شارع تجريبي", tripLine, "وجهة تجريبية", button,
    )

    private fun pick(km: String, min: Int = 11) = "على بُعد $min د ($km كلم)"
    private fun trip(km: String, min: Int = 42) = "مشوار لمدة $min د (لمسافة $km كلم)"

    private fun ok(nodes: List<String>): RideOffer {
        val r = RideParser.parse(nodes)
        assertTrue("expected Ok but was $r", r is ParseResult.Ok)
        return (r as ParseResult.Ok).offer
    }

    private fun unreadable(nodes: List<String>, reason: UnreadableReason) =
        assertEquals(ParseResult.Unreadable(reason), RideParser.parse(nodes))

    private class Case(
        val n: Int, val nodes: List<String>, val price: Double, val pickup: Double, val trip: Double,
        val perTrip: Double, val perAll: Double, val verdict: Verdict, val button: ButtonKind,
    )

    private val cases = listOf(
        Case(1, screen("٣٦٠,٢٣", pick("5.0"), trip("43.3")), 360.23, 5.0, 43.3, 8.32, 7.46, Verdict.GOOD, ButtonKind.MATCH),
        Case(2, screen("٨٠٫٦٤", pick("2.3"), trip("6.4")), 80.64, 2.3, 6.4, 12.60, 9.27, Verdict.GOOD, ButtonKind.MATCH),
        Case(3, screen("١٢٣٫٨٠", pick("14.4"), trip("10.3")), 123.80, 14.4, 10.3, 12.02, 5.01, Verdict.NEAR, ButtonKind.MATCH),
        Case(4, screen("٥٠٫٥٨", pick("4.2"), trip("2.9")), 50.58, 4.2, 2.9, 17.44, 7.12, Verdict.GOOD, ButtonKind.MATCH),
        Case(5, screen("٤٣٫٨٣", pick("3.5"), trip("1.3"), button = "اقبل"), 43.83, 3.5, 1.3, 33.72, 9.13, Verdict.GOOD, ButtonKind.ACCEPT),
        Case(6, screen("٢٣٠٫٨٤", pick("2.5"), trip("29.0")), 230.84, 2.5, 29.0, 7.96, 7.33, Verdict.GOOD, ButtonKind.MATCH),
        Case(7, screen("٨٠٫٧٦", pick("6.5"), trip("6.9")), 80.76, 6.5, 6.9, 11.70, 6.03, Verdict.GOOD, ButtonKind.MATCH),
        Case(
            8, screen(
                "١٩٠٫١٣", "على بُعد أقل من دقيقة واحدة (0 كلم)", trip("25.1"), button = "اقبل",
                boost = "يتم تضمين عرض \"+Boost\" بقيمة ٢٨٫٠٢ ج.م.",
            ),
            190.13, 0.0, 25.1, 7.57, 7.57, Verdict.GOOD, ButtonKind.ACCEPT,
        ),
    )

    @Test fun table_cases_parse_and_evaluate() {
        for (c in cases) {
            val o = ok(c.nodes)
            assertEquals("case ${c.n} price", c.price, o.price, 1e-9)
            assertEquals("case ${c.n} pickup", c.pickup, o.pickupKm, 1e-9)
            assertEquals("case ${c.n} trip", c.trip, o.tripKm, 1e-9)
            assertEquals("case ${c.n} button", c.button, o.button)
            assertEquals(DistanceSource.KEYWORDS, o.distanceSource)
            val e = RideEvaluator.evaluate(o, Thresholds.DEFAULT, Basis.INCLUSIVE)
            assertEquals("case ${c.n} per trip", c.perTrip, e.perKmTrip, 0.006)
            assertEquals("case ${c.n} per all", c.perAll, e.perKmAll, 0.006)
            assertEquals("case ${c.n} verdict", c.verdict, e.verdict)
        }
    }

    @Test fun case8_boost_is_not_added() = assertEquals(190.13, ok(cases[7].nodes).price, 1e-9)

    @Test fun boost_sentence_split_across_nodes_is_still_ignored() {
        val nodes = listOf(
            "١٩٠٫١٣ ج.م.", "يتم تضمين عرض \"+Boost\" بقيمة", "٢٨٫٠٢ ج.م.",
            "على بُعد أقل من دقيقة واحدة (0 كلم)", trip("25.1"), "اقبل",
        )
        assertEquals(190.13, ok(nodes).price, 1e-9)
    }

    @Test fun case10_settings_screen_is_ignored() {
        val nodes = listOf("الخدمات", "تصفية المشاوير", "UberX", "UberX Saver", "Intercity")
        assertEquals(ParseResult.NotRideScreen, RideParser.parse(nodes))
    }

    @Test fun no_button_means_not_a_ride_screen() {
        assertEquals(ParseResult.NotRideScreen, RideParser.parse(screen("٨٠٫٦٤", pick("2.3"), trip("6.4"), button = "إلغاء")))
    }

    @Test fun decimal_separator_variants() {
        assertEquals(80.64, ok(screen("٨٠,٦٤", pick("2.3"), trip("6.4"))).price, 1e-9)
        assertEquals(80.64, ok(screen("٨٠٫٦٤", pick("2.3"), trip("6.4"))).price, 1e-9)
        assertEquals(80.64, ok(screen("80.64", pick("2.3"), trip("6.4"))).price, 1e-9)
    }

    @Test fun thousands_separators() {
        assertEquals(1234.50, ok(screen("١٬٢٣٤٫٥٠", pick("2.3"), trip("6.4"))).price, 1e-9)
        assertEquals(1234.50, ok(screen("1,234.50", pick("2.3"), trip("6.4"))).price, 1e-9)
        assertEquals(1234.0, ok(screen("1,234", pick("2.3"), trip("6.4"))).price, 1e-9)
    }

    @Test fun malformed_number_is_unreadable() {
        unreadable(screen("1.2345", pick("2.3"), trip("6.4")), UnreadableReason.PRICE_FORMAT)
    }

    @Test fun text_split_over_several_nodes() {
        val nodes = listOf(
            "٨٠٫٦٤", "ج.م.", "على بُعد", "11 د", "(2.3 كلم)", "شارع", "مشوار لمدة", "42 د", "(لمسافة", "6.4 كلم)", "تطابق",
        )
        val o = ok(nodes)
        assertEquals(80.64, o.price, 1e-9)
        assertEquals(2.3, o.pickupKm, 1e-9)
        assertEquals(6.4, o.tripKm, 1e-9)
    }

    @Test fun duplicated_text_and_description_nodes_are_tolerated() {
        val line = pick("2.3")
        val nodes = listOf("80.64 ج.م.", "80.64 ج.م.", line, line, trip("6.4"), trip("6.4"), "تطابق", "تطابق")
        assertEquals(80.64, ok(nodes).price, 1e-9)
    }

    @Test fun two_different_prices_rejected() {
        unreadable(screen("٨٠٫٦٤", pick("2.3"), trip("6.4")) + "٩٠٫٠٠ ج.م.", UnreadableReason.PRICE_AMBIGUOUS)
    }

    @Test fun missing_price_rejected() {
        unreadable(listOf(pick("2.3"), trip("6.4"), "تطابق"), UnreadableReason.PRICE_MISSING)
    }

    @Test fun missing_pickup_rejected() {
        unreadable(listOf("٨٠٫٦٤ ج.م.", trip("6.4"), "تطابق"), UnreadableReason.DISTANCE_MISSING)
    }

    @Test fun missing_trip_rejected() {
        unreadable(listOf("٨٠٫٦٤ ج.م.", pick("2.3"), "تطابق"), UnreadableReason.DISTANCE_MISSING)
    }

    @Test fun non_positive_values_rejected() {
        unreadable(screen("٠", pick("2.3"), trip("6.4")), UnreadableReason.NON_POSITIVE)
        unreadable(screen("٨٠٫٦٤", pick("2.3"), trip("0")), UnreadableReason.NON_POSITIVE)
    }

    @Test fun english_fallback_by_order_is_flagged() {
        val nodes = listOf("UberX", "EGP 80.64", "11 min (2.3 km) away", "Some St", "42 min (6.4 km) trip", "Match")
        val o = ok(nodes)
        assertEquals(DistanceSource.FALLBACK_ORDER, o.distanceSource)
        assertEquals(80.64, o.price, 1e-9)
        assertEquals(2.3, o.pickupKm, 1e-9)
        assertEquals(6.4, o.tripKm, 1e-9)
    }

    @Test fun english_fallback_requires_exactly_two_distances() {
        unreadable(listOf("EGP 80.64", "11 min (2.3 km) away", "Accept"), UnreadableReason.DISTANCE_MISSING)
        unreadable(
            listOf("EGP 80.64", "(1.0 km)", "(2.0 km)", "(3.0 km)", "Accept"),
            UnreadableReason.DISTANCE_MISSING,
        )
    }

    @Test fun trip_only_basis_changes_verdict() {
        val o = ok(cases[2].nodes) // case 3: 12.02 per trip km, 5.01 inclusive
        assertEquals(Verdict.GOOD, RideEvaluator.evaluate(o, Thresholds.DEFAULT, Basis.TRIP_ONLY).verdict)
        assertEquals(Verdict.NEAR, RideEvaluator.evaluate(o, Thresholds.DEFAULT, Basis.INCLUSIVE).verdict)
    }

    @Test fun threshold_boundaries() {
        fun v(price: Double, trip: Double) =
            RideEvaluator.evaluate(RideOffer(price, 0.0, trip, ButtonKind.MATCH, DistanceSource.KEYWORDS), Thresholds.DEFAULT, Basis.INCLUSIVE).verdict
        assertEquals(Verdict.GOOD, v(60.0, 10.0)) // exactly 6.0
        assertEquals(Verdict.NEAR, v(59.0, 10.0)) // 5.9
        assertEquals(Verdict.NEAR, v(45.0, 10.0)) // exactly 4.5
        assertEquals(Verdict.BAD, v(44.0, 10.0)) // 4.4
    }

    @Test fun custom_thresholds() {
        val o = ok(cases[0].nodes) // 7.46 inclusive -> 7.5
        assertEquals(Verdict.NEAR, RideEvaluator.evaluate(o, Thresholds(8.0, 5.0), Basis.INCLUSIVE).verdict)
        assertEquals(Verdict.BAD, RideEvaluator.evaluate(o, Thresholds(9.0, 8.0), Basis.INCLUSIVE).verdict)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thresholds_must_be_ordered() {
        Thresholds(4.0, 5.0)
    }

    @Test fun arabic_formatting() {
        assertEquals("٧٫٥", ArabicFormat.number(7.46))
        assertEquals("7.5", ArabicFormat.number(7.46, arabicDigits = false))
        assertEquals("٣٦٠٫٢٣", ArabicFormat.number(360.23, 2))
    }
}
