package dev.nekuz.gamemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val delta = 1e-9

    private fun table(curve: Curve, levels: Long) = (Progression.generate(curve, levels) as Checked.Ok).value

    private fun bad(curve: Curve) = assertTrue(curve.toString(), Progression.generate(curve, 5) is Checked.Bad)

    @Test
    fun linearHandComputed() {
        val result = table(Curve.Linear(100.0, 50.0), 3)
        assertEquals(listOf(100.0, 150.0, 200.0), result.values.toList())
        assertEquals(listOf(100.0, 250.0, 450.0), result.totals.toList())
    }

    @Test
    fun linearCanDecrease() {
        assertEquals(listOf(10.0, 7.0, 4.0), table(Curve.Linear(10.0, -3.0), 3).values.toList())
    }

    @Test
    fun polynomialHandComputed() {
        val curve = Curve.Polynomial(100.0, 50.0, 2.0)
        assertEquals(100.0, Progression.valueAt(curve, 1), delta)
        assertEquals(150.0, Progression.valueAt(curve, 2), delta)
        assertEquals(300.0, Progression.valueAt(curve, 3), delta)
        assertEquals(550.0, Progression.valueAt(curve, 4), delta)
    }

    @Test
    fun polynomialPowerZeroIsConstant() {
        val curve = Curve.Polynomial(100.0, 50.0, 0.0)
        assertEquals(150.0, Progression.valueAt(curve, 1), delta)
        assertEquals(150.0, Progression.valueAt(curve, 9), delta)
    }

    @Test
    fun polynomialFractionalPower() {
        assertEquals(20.0, Progression.valueAt(Curve.Polynomial(0.0, 10.0, 0.5), 5), delta)
    }

    @Test
    fun exponentialHandComputed() {
        val curve = Curve.Exponential(100.0, 1.5)
        assertEquals(100.0, Progression.valueAt(curve, 1), delta)
        assertEquals(150.0, Progression.valueAt(curve, 2), delta)
        assertEquals(225.0, Progression.valueAt(curve, 3), delta)
    }

    @Test
    fun exponentialDecay() {
        assertEquals(25.0, Progression.valueAt(Curve.Exponential(100.0, 0.5), 3), delta)
    }

    @Test
    fun exponentialZeroBaseIsNotNan() {
        val result = table(Curve.Exponential(0.0, 1e6), 5000)
        assertEquals(5000, result.size)
        assertTrue(result.values.all { it == 0.0 })
    }

    @Test
    fun polynomialZeroScaleIsNotNan() {
        val result = table(Curve.Polynomial(7.0, 0.0, 64.0), 5000)
        assertTrue(result.values.all { it == 7.0 })
    }

    @Test
    fun logisticHandComputed() {
        val curve = Curve.Logistic(1000.0, 1.0, 10.0)
        assertEquals(500.0, Progression.valueAt(curve, 10), delta)
        assertEquals(731.0585786, Progression.valueAt(curve, 11), 1e-6)
        assertEquals(268.9414214, Progression.valueAt(curve, 9), 1e-6)
    }

    @Test
    fun logisticSaturatesWithoutNan() {
        val curve = Curve.Logistic(1000.0, 1000.0, 1e9)
        assertEquals(0.0, Progression.valueAt(curve, 1), 0.0)
        val high = Curve.Logistic(1000.0, 1000.0, -1e9)
        assertEquals(1000.0, Progression.valueAt(high, 1), 0.0)
    }

    @Test
    fun logisticNegativeMaxNoNegativeZero() {
        val value = Progression.valueAt(Curve.Logistic(-5.0, 1000.0, 1e9), 1)
        assertEquals("0.0", value.toString())
    }

    @Test
    fun levelBelowOneIsTreatedAsOne() {
        val curve = Curve.Linear(100.0, 50.0)
        assertEquals(100.0, Progression.valueAt(curve, 0), delta)
        assertEquals(100.0, Progression.valueAt(curve, -5), delta)
        assertEquals(100.0, Progression.valueAt(curve, Int.MIN_VALUE), delta)
    }

    @Test
    fun hugeLevelsAreCappedAndFlagged() {
        val result = table(Curve.Linear(1.0, 1.0), Long.MAX_VALUE)
        assertEquals(Progression.MAX_LEVELS, result.size)
        assertTrue(result.capped)
        assertEquals(Long.MAX_VALUE, result.requestedLevels)
        assertEquals(5000.0, result.values.last(), delta)
    }

    @Test
    fun capBoundary() {
        assertFalse(table(Curve.Linear(1.0, 1.0), 5000).capped)
        assertTrue(table(Curve.Linear(1.0, 1.0), 5001).capped)
        assertEquals(5000, table(Curve.Linear(1.0, 1.0), 5001).size)
    }

    @Test
    fun zeroOrNegativeLevelsAreRefused() {
        assertTrue(Progression.generate(Curve.Linear(1.0, 1.0), 0) is Checked.Bad)
        assertTrue(Progression.generate(Curve.Linear(1.0, 1.0), -1) is Checked.Bad)
        assertTrue(Progression.generate(Curve.Linear(1.0, 1.0), Long.MIN_VALUE) is Checked.Bad)
    }

    @Test
    fun singleLevel() {
        val result = table(Curve.Linear(7.0, 1.0), 1)
        assertEquals(listOf(7.0), result.values.toList())
        assertNull(result.stoppedAtLevel)
    }

    @Test
    fun overflowStopsTheTableAndNeverContainsInfinity() {
        val result = table(Curve.Exponential(1e15, 1e6), 5000)
        assertTrue(result.stoppedAtLevel != null)
        assertEquals(result.size + 1, result.stoppedAtLevel)
        assertTrue(result.values.all { it.isFinite() })
        assertTrue(result.totals.all { it.isFinite() })
        assertEquals(result.values.size, result.totals.size)
    }

    @Test
    fun negativeOverflowStopsToo() {
        val result = table(Curve.Exponential(-1e15, 1e6), 5000)
        assertTrue(result.stoppedAtLevel != null)
        assertTrue(result.values.all { it.isFinite() })
    }

    @Test
    fun totalOverflowStops() {
        val result = table(Curve.Polynomial(1e15, 1e15, 64.0), 5000)
        assertTrue(result.values.all { it.isFinite() })
        assertTrue(result.totals.all { it.isFinite() })
    }

    @Test
    fun exactOverflowLevelForExponential() {
        val result = table(Curve.Exponential(1.0, 10.0), 400)
        assertEquals(310, result.stoppedAtLevel)
        assertEquals(309, result.size)
    }

    @Test
    fun invalidParametersAreRefused() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e16, -1e16).forEach {
            bad(Curve.Linear(it, 1.0))
            bad(Curve.Linear(1.0, it))
            bad(Curve.Polynomial(it, 1.0, 1.0))
            bad(Curve.Polynomial(1.0, it, 1.0))
            bad(Curve.Exponential(it, 1.0))
            bad(Curve.Logistic(it, 1.0, 1.0))
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 65.0).forEach { bad(Curve.Polynomial(1.0, 1.0, it)) }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 1e7).forEach { bad(Curve.Exponential(1.0, it)) }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 1001.0, -1001.0).forEach { bad(Curve.Logistic(1.0, it, 1.0)) }
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, 2e9, -2e9).forEach { bad(Curve.Logistic(1.0, 1.0, it)) }
    }

    @Test
    fun everyValueIsFiniteAcrossAnExtremeSweep() {
        val magnitudes = listOf(-1e15, -1.0, 0.0, 1e-300, 1.0, 1e15)
        val curves = ArrayList<Curve>()
        magnitudes.forEach { a ->
            magnitudes.forEach { b ->
                curves.add(Curve.Linear(a, b))
                listOf(0.0, 0.5, 1.0, 64.0).forEach { curves.add(Curve.Polynomial(a, b, it)) }
                listOf(1e-300, 0.5, 1.0, 2.0, 1e6).forEach { curves.add(Curve.Exponential(a, it)) }
                listOf(-1000.0, -1.0, 0.0, 1.0, 1000.0).forEach { curves.add(Curve.Logistic(a, it, b.coerceIn(-1e9, 1e9))) }
            }
        }
        curves.forEach { curve ->
            val result = table(curve, 5000)
            assertTrue(curve.toString(), result.values.all { it.isFinite() })
            assertTrue(curve.toString(), result.totals.all { it.isFinite() })
        }
    }
}

class ChartMathTest {
    @Test
    fun boundsOfNormalValues() {
        assertEquals(ChartBounds(1.0, 5.0), ChartMath.bounds(doubleArrayOf(3.0, 1.0, 5.0)))
    }

    @Test
    fun emptyBoundsAreSafe() {
        assertEquals(ChartBounds(0.0, 1.0), ChartMath.bounds(DoubleArray(0)))
    }

    @Test
    fun nonFiniteValuesAreIgnored() {
        assertEquals(
            ChartBounds(1.0, 2.0),
            ChartMath.bounds(doubleArrayOf(Double.NaN, 1.0, Double.POSITIVE_INFINITY, 2.0, Double.NEGATIVE_INFINITY)),
        )
        assertEquals(ChartBounds(0.0, 1.0), ChartMath.bounds(doubleArrayOf(Double.NaN)))
    }

    @Test
    fun flatLineGetsSpan() {
        val bounds = ChartMath.bounds(doubleArrayOf(5.0, 5.0))
        assertTrue(bounds.min < 5.0 && bounds.max > 5.0)
        assertEquals(0.5, ChartMath.fractionY(5.0, bounds), 1e-9)
    }

    @Test
    fun hugeFlatLineGetsSpan() {
        val bounds = ChartMath.bounds(doubleArrayOf(1e300))
        assertTrue(bounds.min < bounds.max)
        assertEquals(0.5, ChartMath.fractionY(1e300, bounds), 1e-9)
    }

    @Test
    fun fullDoubleRangeDoesNotOverflow() {
        val bounds = ChartMath.bounds(doubleArrayOf(-Double.MAX_VALUE, Double.MAX_VALUE))
        assertEquals(0.0, ChartMath.fractionY(-Double.MAX_VALUE, bounds), 1e-9)
        assertEquals(1.0, ChartMath.fractionY(Double.MAX_VALUE, bounds), 1e-9)
        assertEquals(0.5, ChartMath.fractionY(0.0, bounds), 1e-9)
    }

    @Test
    fun fractionYHandComputed() {
        val bounds = ChartBounds(0.0, 10.0)
        assertEquals(0.25, ChartMath.fractionY(2.5, bounds), 1e-12)
        assertEquals(0.0, ChartMath.fractionY(-5.0, bounds), 0.0)
        assertEquals(1.0, ChartMath.fractionY(50.0, bounds), 0.0)
    }

    @Test
    fun fractionYBadInputs() {
        assertEquals(0.0, ChartMath.fractionY(Double.NaN, ChartBounds(0.0, 1.0)), 0.0)
        assertEquals(0.0, ChartMath.fractionY(Double.POSITIVE_INFINITY, ChartBounds(0.0, 1.0)), 0.0)
        assertEquals(0.5, ChartMath.fractionY(1.0, ChartBounds(1.0, 1.0)), 0.0)
        assertEquals(0.5, ChartMath.fractionY(1.0, ChartBounds(Double.NaN, 1.0)), 0.0)
    }

    @Test
    fun fractionX() {
        assertEquals(0.5, ChartMath.fractionX(0, 1), 0.0)
        assertEquals(0.5, ChartMath.fractionX(0, 0), 0.0)
        assertEquals(0.0, ChartMath.fractionX(0, 5), 0.0)
        assertEquals(1.0, ChartMath.fractionX(4, 5), 0.0)
        assertEquals(0.5, ChartMath.fractionX(2, 5), 0.0)
        assertEquals(1.0, ChartMath.fractionX(9, 5), 0.0)
    }

    @Test
    fun stride() {
        assertEquals(1, ChartMath.stride(100, 1000))
        assertEquals(5, ChartMath.stride(5000, 1000))
        assertEquals(1, ChartMath.stride(0, 100))
        assertEquals(1, ChartMath.stride(100, 0))
        assertEquals(1, ChartMath.stride(-1, -1))
    }
}
