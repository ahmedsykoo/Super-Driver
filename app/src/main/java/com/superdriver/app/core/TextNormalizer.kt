package com.superdriver.app.core

/**
 * Text utilities shared by every reading path (accessibility tree and OCR).
 *
 * Uber Egypt renders the same offer card either in Arabic or in English, and
 * depending on the device locale it may use Arabic-Indic digits (٠١٢٣…) or
 * ASCII digits, and the Arabic decimal separator (٫).
 *
 * Everything is normalized before any regex runs, so the parser only ever deals
 * with ASCII digits, unified Arabic letters and ASCII punctuation.
 */
object TextNormalizer {

    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"
    private const val EXTENDED_ARABIC_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    /** Harakat, tanween, tatweel and Quranic marks: meaningless for us. */
    private val DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u0640\\u06D6-\\u06ED]")

    /** آ أ إ ا ٱ ٰ ٱً  ->  ا */
    private val ALEF_VARIANTS = Regex("[\\u0622\\u0623\\u0625\\u0627\\u0671\\u0672\\u0673]")

    /** NBSP, thin space, narrow NBSP, BOM, LRM, RLM. */
    private val INVISIBLE_SPACES = Regex("[\\u00A0\\u2007\\u2009\\u202F\\uFEFF\\u200E\\u200F]")

    private val MULTI_SPACE = Regex("\\s+")

    fun digitsToAscii(input: String): String {
        val out = StringBuilder(input.length)
        for (ch in input) {
            val arabicIndex = ARABIC_DIGITS.indexOf(ch)
            if (arabicIndex >= 0) {
                out.append(('0'.code + arabicIndex).toChar())
                continue
            }
            val extendedIndex = EXTENDED_ARABIC_DIGITS.indexOf(ch)
            if (extendedIndex >= 0) {
                out.append(('0'.code + extendedIndex).toChar())
                continue
            }
            out.append(ch)
        }
        return out.toString()
    }

    /**
     * Full normalization used before matching: ASCII digits, unified Arabic
     * letters, no diacritics, single spaces, ASCII decimal point.
     */
    fun normalize(input: String): String {
        var text = digitsToAscii(input)
        text = INVISIBLE_SPACES.replace(text, " ")
        text = DIACRITICS.replace(text, "")
        text = ALEF_VARIANTS.replace(text, "ا")
        text = text
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace('٪', '%')
            .replace("٬", "")   // Arabic thousands separator: drop it
            .replace('٫', '.')  // Arabic decimal separator
        text = MULTI_SPACE.replace(text, " ")
        return text.trim()
    }

    /**
     * Parses a numeric token that may use either "1,234.56" or "1.234,56".
     * Returns null when the token is not a plain number.
     */
    fun number(raw: String): Double? {
        if (raw.isBlank()) return null

        val token = raw
            .replace(" ", "")
            .replace("٬", "")
            .replace("'", "")
            .replace("’", "")

        if (token.isBlank()) return null
        if (!token.matches(Regex("-?[0-9]+(?:[.,][0-9]+)*"))) return null

        val lastComma = token.lastIndexOf(',')
        val lastDot = token.lastIndexOf('.')

        return when {
            lastComma < 0 && lastDot < 0 -> token.toDoubleOrNull()
            lastComma > lastDot -> {
                val integerPart = token.substring(0, lastComma).replace(".", "")
                val fractionPart = token.substring(lastComma + 1)
                // "1,234" is a thousands group in Egypt, not 1.234 — but "0,250"
                // stays a decimal fraction.
                if (lastDot < 0 && fractionPart.length == 3 && integerPart != "0") {
                    (integerPart + fractionPart).toDoubleOrNull()
                } else {
                    "$integerPart.$fractionPart".toDoubleOrNull()
                }
            }
            else -> {
                val integerPart = token.substring(0, lastDot).replace(",", "")
                val fractionPart = token.substring(lastDot + 1)
                "$integerPart.$fractionPart".toDoubleOrNull()
            }
        }
    }

    /**
     * Conservative OCR clean-up. It is only applied by the OCR path and only to
     * characters that are commonly confused with digits by ML Kit.
     */
    fun fixOcrDigits(line: String): String {
        val out = StringBuilder(line.length)
        for (ch in line) {
            out.append(
                when (ch) {
                    'O', 'o' -> '0'
                    'l', 'I', '|', '!' -> '1'
                    'S' -> '5'
                    'B' -> '8'
                    'Z' -> '2'
                    else -> ch
                }
            )
        }
        return out.toString()
    }
}
