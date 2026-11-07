package dev.nekuz.gamemath

import kotlin.math.abs

data class ChartBounds(val min: Double, val max: Double)

object ChartMath {
    fun bounds(values: DoubleArray): ChartBounds {
        var low = Double.POSITIVE_INFINITY
        var high = Double.NEGATIVE_INFINITY
        for (value in values) {
            if (!value.isFinite()) continue
            if (value < low) low = value
            if (value > high) high = value
        }
        if (low > high) return ChartBounds(0.0, 1.0)
        if (low < high) return ChartBounds(low, high)
        val delta = maxOf(1.0, abs(low) / 2.0)
        return ChartBounds(low - delta, low + delta)
    }

    fun fractionY(value: Double, bounds: ChartBounds): Double {
        if (!value.isFinite()) return 0.0
        val span = bounds.max / 2.0 - bounds.min / 2.0
        if (!span.isFinite() || span <= 0.0) return 0.5
        return ((value / 2.0 - bounds.min / 2.0) / span).coerceIn(0.0, 1.0)
    }

    fun fractionX(index: Int, count: Int): Double {
        if (count <= 1) return 0.5
        return (index.toDouble() / (count - 1).toDouble()).coerceIn(0.0, 1.0)
    }

    fun stride(count: Int, pixels: Int): Int {
        if (count <= 0 || pixels <= 0) return 1
        return (count / pixels).coerceAtLeast(1)
    }
}
