package dev.nekuz.gamemath

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

sealed class Curve {
    data class Linear(val base: Double, val slope: Double) : Curve()

    data class Polynomial(val base: Double, val scale: Double, val power: Double) : Curve()

    data class Exponential(val base: Double, val growth: Double) : Curve()

    data class Logistic(val max: Double, val steepness: Double, val midpoint: Double) : Curve()
}

class CurveTable(
    val values: DoubleArray,
    val totals: DoubleArray,
    val requestedLevels: Long,
    val stoppedAtLevel: Int?,
) {
    val size: Int get() = values.size
    val capped: Boolean get() = requestedLevels > Progression.MAX_LEVELS
}

object Progression {
    const val MAX_LEVELS = 5000
    const val MAX_MAGNITUDE = 1e15
    const val MAX_GROWTH = 1e6
    const val MAX_POWER = 64.0
    const val MAX_STEEPNESS = 1e3
    const val MAX_MIDPOINT = 1e9

    fun validate(curve: Curve): Checked<Curve> {
        val problem = when (curve) {
            is Curve.Linear -> magnitude("Base", curve.base) ?: magnitude("Slope", curve.slope)
            is Curve.Polynomial -> magnitude("Base", curve.base)
                ?: magnitude("Scale", curve.scale)
                ?: within("Power", curve.power, 0.0, MAX_POWER)
            is Curve.Exponential -> magnitude("Base", curve.base)
                ?: if (!curve.growth.isFinite() || curve.growth <= 0.0 || curve.growth > MAX_GROWTH) {
                    "Growth must be above 0 and at most ${Fmt.number(MAX_GROWTH)}"
                } else {
                    null
                }
            is Curve.Logistic -> magnitude("Maximum", curve.max)
                ?: within("Steepness", curve.steepness, -MAX_STEEPNESS, MAX_STEEPNESS)
                ?: within("Midpoint", curve.midpoint, -MAX_MIDPOINT, MAX_MIDPOINT)
        }
        return if (problem == null) Checked.Ok(curve) else Checked.Bad(problem)
    }

    fun valueAt(curve: Curve, level: Int): Double {
        val safeLevel = level.coerceAtLeast(1).toDouble()
        val step = safeLevel - 1.0
        val value = when (curve) {
            is Curve.Linear -> curve.base + curve.slope * step
            is Curve.Polynomial ->
                if (curve.scale == 0.0) curve.base else curve.base + curve.scale * step.pow(curve.power)
            is Curve.Exponential ->
                if (curve.base == 0.0) 0.0 else curve.base * curve.growth.pow(step)
            is Curve.Logistic -> curve.max / (1.0 + exp(-curve.steepness * (safeLevel - curve.midpoint)))
        }
        return value + 0.0
    }

    fun generate(curve: Curve, levels: Long): Checked<CurveTable> {
        if (levels < 1) return Checked.Bad("Levels must be at least 1")
        when (val valid = validate(curve)) {
            is Checked.Bad -> return valid
            is Checked.Ok -> Unit
        }
        val count = minOf(levels, MAX_LEVELS.toLong()).toInt()
        val values = DoubleArray(count)
        val totals = DoubleArray(count)
        var total = 0.0
        for (index in 0 until count) {
            val value = valueAt(curve, index + 1)
            val next = total + value
            if (!value.isFinite() || !next.isFinite()) {
                if (index == 0) return Checked.Bad("The first level does not fit in a number")
                return Checked.Ok(CurveTable(values.copyOf(index), totals.copyOf(index), levels, index + 1))
            }
            total = next
            values[index] = value
            totals[index] = total
        }
        return Checked.Ok(CurveTable(values, totals, levels, null))
    }

    private fun magnitude(name: String, value: Double): String? =
        if (value.isFinite() && abs(value) <= MAX_MAGNITUDE) {
            null
        } else {
            "$name must be between ${Fmt.number(-MAX_MAGNITUDE)} and ${Fmt.number(MAX_MAGNITUDE)}"
        }

    private fun within(name: String, value: Double, min: Double, max: Double): String? =
        if (value.isFinite() && value >= min && value <= max) {
            null
        } else {
            "$name must be between ${Fmt.number(min)} and ${Fmt.number(max)}"
        }
}
