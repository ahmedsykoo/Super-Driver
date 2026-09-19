package com.superdriver.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun arabicDigitsBecomeAsciiDigits() {
        assertEquals("150.00", TextNormalizer.digitsToAscii("١٥٠٫٠٠").replace('٫', '.'))
        assertEquals("1234567890", TextNormalizer.digitsToAscii("١٢٣٤٥٦٧٨٩٠"))
        assertEquals("1234567890", TextNormalizer.digitsToAscii("۱۲۳۴۵۶۷۸۹۰"))
    }

    @Test
    fun normalizationUnifiesLettersAndSeparators() {
        assertEquals("الاجره", TextNormalizer.normalize("الأجرة"))
        assertEquals("مكافاه", TextNormalizer.normalize("مُكافأة"))
        assertEquals("1234.50", TextNormalizer.normalize("١٬٢٣٤٫٥٠"))
        assertEquals("2.5 كم", TextNormalizer.normalize("2.5  كم"))
    }

    @Test
    fun numbersWithEitherSeparatorStyle() {
        assertEquals(1234.50, TextNormalizer.number("1,234.50")!!, 0.001)
        assertEquals(1234.50, TextNormalizer.number("1.234,50")!!, 0.001)
        assertEquals(1234.0, TextNormalizer.number("1,234")!!, 0.001)
        assertEquals(2.5, TextNormalizer.number("2,5")!!, 0.001)
        assertEquals(0.25, TextNormalizer.number("0,250")!!, 0.001)
        assertEquals(150.0, TextNormalizer.number("150")!!, 0.001)
    }

    @Test
    fun nonNumbersAreRejected() {
        assertNull(TextNormalizer.number(""))
        assertNull(TextNormalizer.number("كم"))
        assertNull(TextNormalizer.number("12 كم"))
        assertNull(TextNormalizer.number("ج.م"))
    }
}
