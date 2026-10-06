package com.superdriver.engine

/** Step 1 of the parsing rules: strip diacritics/tatweel/bidi marks, map digits to Latin, unify separators. */
object TextNormalizer {
    private val STRIP = Regex("[\\u064B-\\u065F\\u0670\\u0640\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\u061C\\uFEFF]")
    private val SPACES = Regex("\\s+")

    fun normalize(input: String): String {
        val src = STRIP.replace(input, "")
        val sb = StringBuilder(src.length)
        for (ch in src) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                '٫' -> sb.append('.') // Arabic decimal separator
                '٬' -> Unit // Arabic thousands separator: dropped
                ' ', ' ', ' ' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return SPACES.replace(sb, " ").trim()
    }
}
