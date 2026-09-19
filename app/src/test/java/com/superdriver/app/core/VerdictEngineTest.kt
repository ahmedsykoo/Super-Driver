package com.superdriver.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictEngineTest {

    @Test
    fun defaultThresholds() {
        assertEquals(Verdict.SUITABLE, VerdictEngine.evaluate(18.75))
        assertEquals(Verdict.SUITABLE, VerdictEngine.evaluate(10.0))
        assertEquals(Verdict.NEAR, VerdictEngine.evaluate(9.99))
        assertEquals(Verdict.NEAR, VerdictEngine.evaluate(6.0))
        assertEquals(Verdict.NOT_SUITABLE, VerdictEngine.evaluate(5.99))
        assertEquals(Verdict.NOT_SUITABLE, VerdictEngine.evaluate(2.05))
    }

    @Test
    fun customThresholds() {
        assertEquals(Verdict.SUITABLE, VerdictEngine.evaluate(12.0, goodAtLeast = 12.0, nearAtLeast = 8.0))
        assertEquals(Verdict.NEAR, VerdictEngine.evaluate(9.0, goodAtLeast = 12.0, nearAtLeast = 8.0))
        assertEquals(
            Verdict.NOT_SUITABLE,
            VerdictEngine.evaluate(7.0, goodAtLeast = 12.0, nearAtLeast = 8.0)
        )
    }

    @Test
    fun theExampleFromTheSpec() {
        // 150 EGP / 8 km = 18.75 EGP per km
        val offer = TripOfferParser.parse(listOf("ج.م 150", "8 كم"))
        assertEquals(150.0, offer!!.priceEgp, 0.01)
        assertEquals(8.0, offer.totalKm, 0.01)
        assertEquals(18.75, offer.egpPerKm!!, 0.01)
        assertEquals(Verdict.SUITABLE, offer.verdict(goodAtLeast = 10.0, nearAtLeast = 6.0))
    }
}
