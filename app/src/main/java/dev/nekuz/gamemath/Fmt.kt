package dev.nekuz.gamemath

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

object Fmt {
    const val NOT_AVAILABLE = "n/a"
    const val PREVIEW_LENGTH = 8000
    const val ALMOST_CERTAIN = "> 99.9999%"

    private const val SCIENTIFIC_ABOVE = 1e15
    private const val SCIENTIFIC_BELOW = 1e-4
    private val BYTE_UNITS = listOf("KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

    fun number(value: Double, decimals: Int = 4): String {
        if (!value.isFinite()) return NOT_AVAILABLE
        val size = abs(value)
        if (size != 0.0 && (size >= SCIENTIFIC_ABOVE || size < SCIENTIFIC_BELOW)) {
            return String.format(Locale.ROOT, "%.3e", value)
        }
        val text = BigDecimal(value)
            .setScale(decimals.coerceIn(0, 12), RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return if (text == "-0") "0" else text
    }

    fun percent(probability: Double): String {
        if (!probability.isFinite()) return NOT_AVAILABLE
        val text = number(probability * 100.0) + "%"
        if (text == "100%" && probability < 1.0) return ALMOST_CERTAIN
        return text
    }

    fun bytes(count: Long): String {
        if (count < 0) return NOT_AVAILABLE
        if (count < 1024) return "$count B"
        var value = count.toDouble() / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < BYTE_UNITS.lastIndex) {
            value /= 1024.0
            unit++
        }
        return String.format(Locale.ROOT, "%.2f %s", value, BYTE_UNITS[unit])
    }

    fun preview(text: String, limit: Int = PREVIEW_LENGTH): String =
        if (text.length <= limit) text else text.substring(0, limit.coerceAtLeast(0))
}
