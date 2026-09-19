package com.superdriver.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Temporary diagnostic: removed once the OCR overlay case is understood. */
class DebugProbeTest {

    @Test
    fun probeOverlayCase() {
        val raw = listOf("EGP 85.50", "2.5 km", "8.5 km", "Super Driver", "18.75 EGP/km", "SUITABLE")
        val prepared = raw.map { TextNormalizer.normalize(TextNormalizer.fixOcrDigits(it)) }
        val merged = TripOfferParser.mergeSplitTokens(prepared)
        val perLine = merged.map { line -> "$line => ${TripOfferParser.extractPrice(listOf(line))}" }
        val price = TripOfferParser.extractPrice(merged)
        val offer = TripOfferParser.parse(raw, ReadSource.OCR)
        assertEquals(
            "prepared=$prepared\n merged=$merged\n perLine=$perLine\n price=$price\n offer=$offer",
            85.50,
            price ?: -1.0,
            0.01
        )
    }
}
