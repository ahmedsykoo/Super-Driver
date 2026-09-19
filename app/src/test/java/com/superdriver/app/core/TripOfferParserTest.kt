package com.superdriver.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the reading of Uber Egypt offer screens.
 *
 * Run with:  ./gradlew :app:testDebugUnitTest
 */
class TripOfferParserTest {

    private fun parse(lines: List<String>, ocr: Boolean = false): TripOffer? =
        TripOfferParser.parse(lines, if (ocr) ReadSource.OCR else ReadSource.ACCESSIBILITY)

    private fun offer(vararg lines: String): TripOffer {
        val result = parse(lines.toList())
        assertNotNull("expected an offer for: ${lines.joinToString(" | ")}", result)
        return result!!
    }

    private fun assertRate(offer: TripOffer, expected: Double) {
        val rate = offer.egpPerKm
        assertNotNull("rate must be computable", rate)
        assertEquals(expected, rate!!, 0.01)
    }

    private fun assertKm(offer: TripOffer, pickup: Double?, trip: Double?) {
        assertEquals("pickup", pickup, offer.pickupKm)
        assertEquals("trip", trip, offer.tripKm)
    }

    // ------------------------------------------------------------- Arabic cards

    @Test
    fun arabicClassicCard() {
        val trip = offer(
            "رحلة متاحة",
            "UberX",
            "ج.م 85.50",
            "على بُعد 5 دقائق",
            "2.5 كم",
            "15 دقيقة",
            "8.5 كم",
            "شارع الهرم",
            "مدينة نصر",
            "قبول",
            "رفض"
        )
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
        assertEquals(11.0, trip.totalKm, 0.01)
        assertEquals("UberX", trip.serviceName)
        assertRate(trip, 7.77)
        assertEquals(Verdict.NEAR, trip.verdict(goodAtLeast = 10.0, nearAtLeast = 6.0))
    }

    @Test
    fun arabicIndicDigits() {
        val trip = offer("رحلة جديدة", "٨٥٫٥٠ ج.م", "على بُعد ٥ دقائق", "٢٫٥ كم", "١٥ دقيقة", "٨٫٥ كم")
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
        assertRate(trip, 7.77)
    }

    @Test
    fun arabicCurrencyAfterNumberAndSpelledOut() {
        val trip = offer("85.50 جنيه", "2.5 كم", "8.5 كم")
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun arabicDistanceAndTimeOnTheSameLineIsNotReadTwice() {
        val trip = offer("ج.م 85.50", "2.5 كم 5 دقيقة", "8.5 كم 15 دقيقة")
        assertKm(trip, 2.5, 8.5)
        assertEquals(11.0, trip.totalKm, 0.01)
    }

    @Test
    fun arabicLabelledLegs() {
        val trip = offer("المسافة إليك: 2.5 كم", "مسافة الرحلة: 8.5 كم", "الأجرة 150.00 ج.م")
        assertEquals(150.00, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
        assertEquals(Verdict.SUITABLE, trip.verdict(goodAtLeast = 10.0, nearAtLeast = 6.0))
    }

    @Test
    fun valueAndUnitSplitAcrossViewsAreRejoined() {
        val trip = offer("150.00", "ج.م", "2.5", "كم", "8.5", "كم")
        assertEquals(150.00, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun priceSplitAcrossThreeViews() {
        val trip = offer("85", ".50", "ج.م", "2.5", "كم", "8.5", "كم")
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun englishPriceSplitAcrossThreeViews() {
        val trip = offer("EGP", "85", ".50", "2.5 km", "8.5 km")
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    /**
     * Regression: several currency numbers can be on the card at once. An
     * explicit fare wins, and otherwise the biggest plausible amount wins —
     * never the smallest.
     */
    @Test
    fun namedFareWinsOverOtherAmounts() {
        val trip = offer("Fare 60 EGP", "Previous trip 300 EGP", "2 km", "8 km")
        assertEquals(60.0, trip.priceEgp, 0.01)
    }

    @Test
    fun whenNothingIsNamedTheBiggestAmountWins() {
        val trip = offer("EGP 85.50", "2.5 km", "8.5 km", "Super Driver", "18.75 EGP/km", "SUITABLE")
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun arabicUnitBeforeValue() {
        val trip = offer("ج.م 150", "كم 2.5", "كم 8.5")
        assertEquals(150.00, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun metresAreConvertedToKilometres() {
        val trip = offer("ج.م 40", "500 م", "3.2 كم")
        assertKm(trip, 0.5, 3.2)
        assertEquals(3.7, trip.totalKm, 0.01)
    }

    @Test
    fun singleUnlabelledDistanceIsStillUsable() {
        val trip = offer("ج.م 60", "الرحلة 6 كم")
        assertKm(trip, null, 6.0)
        assertRate(trip, 10.0)
    }

    @Test
    fun bonusLineIsNotTheFare() {
        val trip = offer("ج.م 85.50", "2.5 كم", "8.5 كم", "+120 ج.م مكافأة")
        assertEquals(85.50, trip.priceEgp, 0.01)
    }

    @Test
    fun walletAndEarningsLinesAreNotTheFare() {
        val trip = offer("السعر 85.50 ج.م", "المحفظة 1,240.00 ج.م", "2.5 كم", "8.5 كم")
        assertEquals(85.50, trip.priceEgp, 0.01)
    }

    @Test
    fun thousandsSeparators() {
        val trip = offer("ج.م 1,234.50", "12.5 كم")
        assertEquals(1234.50, trip.priceEgp, 0.01)
        assertEquals(12.5, trip.totalKm, 0.01)
    }

    @Test
    fun arabicThousandsAndDecimalSeparators() {
        val trip = offer("١٬٢٣٤٫٥٠ ج.م", "12.5 كم")
        assertEquals(1234.50, trip.priceEgp, 0.01)
    }

    @Test
    fun pickupBracketFormWithArabicDigits() {
        val trip = offer("ج.م 150", "على بُعد ٥ دقائق (٢٫٥ كم)", "١٥ دقيقة (٨٫٥ كم)")
        assertKm(trip, 2.5, 8.5)
        assertEquals(Verdict.SUITABLE, trip.verdict(goodAtLeast = 10.0, nearAtLeast = 6.0))
    }

    @Test
    fun arabicServiceNames() {
        assertEquals("UberXL", offer("أوبر إكس إل", "ج.م 220", "4 كم", "12 كم").serviceName)
        assertEquals("Uber Comfort", offer("كومفورت", "ج.م 95", "3 كم", "7 كم").serviceName)
        assertEquals("UberX", offer("أوبر إكس", "ج.م 45", "12 كم").serviceName)
    }

    @Test
    fun unsuitableArabicOffer() {
        val trip = offer("ج.م 45", "10 كم", "12 كم")
        assertRate(trip, 2.05)
        assertEquals(Verdict.NOT_SUITABLE, trip.verdict(goodAtLeast = 10.0, nearAtLeast = 6.0))
    }

    // ------------------------------------------------------------ English cards

    @Test
    fun englishClassicCard() {
        val trip = offer(
            "New trip request",
            "UberX",
            "EGP 85.50",
            "5 min away (2.5 km)",
            "15 min trip (8.5 km)",
            "Accept",
            "Decline"
        )
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
        assertEquals("UberX", trip.serviceName)
    }

    @Test
    fun englishCurrencyAfterNumber() {
        val trip = offer("85.50 EGP", "Pickup: 5 min (2.5 km)", "Trip: 15 min (8.5 km)")
        assertEquals(85.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun englishEuropeanSeparators() {
        val trip = offer("1.234,50 LE", "2,5 km", "8,5 km")
        assertEquals(1234.50, trip.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun englishPoundSignGluedToNumber() {
        val trip = offer("E£150.00", "2.5 km", "8.5 km", "Accept")
        assertEquals(150.00, trip.priceEgp, 0.01)
        assertEquals(Verdict.SUITABLE, trip.verdict(goodAtLeast = 10.0, nearAtLeast = 6.0))
    }

    @Test
    fun englishMetresAndWordUnits() {
        val trip = offer("EGP 33", "400 m", "4.2 kilometers")
        assertKm(trip, 0.4, 4.2)
        assertEquals(4.6, trip.totalKm, 0.01)
    }

    @Test
    fun englishPickupAndDropoffLabels() {
        val trip = offer("EGP 70", "Pickup 2 km away", "Dropoff 5 km")
        assertKm(trip, 2.0, 5.0)
    }

    @Test
    fun englishBothLegsOnOneLine() {
        val trip = offer("EGP 150", "2.5 km away, 8.5 km trip")
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun fractionalDistance() {
        val trip = offer("ج.م 25", "0.5 كم", "2.0 كم")
        assertKm(trip, 0.5, 2.0)
        assertRate(trip, 10.0)
    }

    // ------------------------------------------------------------------ OCR path

    @Test
    fun ocrDigitConfusionsAreRepaired() {
        val trip = parse(listOf("EGP 85.S0", "2.S km", "8.S km"), ocr = true)
        assertNotNull(trip)
        assertEquals(85.50, trip!!.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    @Test
    fun ourOwnOverlayIsNeverParsed() {
        val trip = parse(
            listOf("EGP 85.50", "2.5 km", "8.5 km", "Super Driver", "18.75 EGP/km", "SUITABLE"),
            ocr = true
        )
        assertNotNull(trip)
        assertEquals(85.50, trip!!.priceEgp, 0.01)
        assertKm(trip, 2.5, 8.5)
    }

    // ---------------------------------------------------------------- rejections

    @Test
    fun otherCurrenciesAreNeverAccepted() {
        assertNull(parse(listOf("$85.50", "2.5 km", "8.5 km", "Accept")))
        assertNull(parse(listOf("ARS 8.500", "2.5 km", "8.5 km")))
        assertNull(parse(listOf("USD 85.50", "2.5 km", "8.5 km")))
        assertNull(parse(listOf("85.50 USD", "2.5 km", "8.5 km")))
    }

    @Test
    fun incompleteScreensAreIgnored() {
        assertNull(parse(emptyList()))
        assertNull(parse(listOf("ج.م 85.50", "قبول", "رفض")))
        assertNull(parse(listOf("2.5 كم", "8.5 كم", "قبول")))
        assertNull(parse(listOf("ج.م 85.50", "5 دقيقة", "15 دقيقة")))
    }

    @Test
    fun uberHomeScreenIsIgnored() {
        assertNull(parse(listOf("أنت متصل", "ابدأ الرحلة", "الأرباح", "الرصيد 320 ج.م")))
    }

    @Test
    fun offerKeyChangesWhenTheOfferChanges() {
        val first = offer("ج.م 100", "10 كم")
        val second = offer("ج.م 120", "10 كم")
        assertTrue(first.key != second.key)
        assertEquals(first.key, offer("ج.م 100", "10 كم").key)
    }
}
