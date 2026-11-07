package dev.nekuz.gamemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasingTest {
    private val delta = 1e-6

    @Test
    fun thereAreThirtyOneEasings() {
        assertEquals(31, Easing.entries.size)
    }

    @Test
    fun endpointsAreExact() {
        Easing.entries.forEach {
            assertEquals(it.label, 0.0, it.eval(0.0), 0.0)
            assertEquals(it.label, 1.0, it.eval(1.0), 0.0)
        }
    }

    @Test
    fun outOfRangeIsClamped() {
        Easing.entries.forEach {
            assertEquals(0.0, it.eval(-1.0), 0.0)
            assertEquals(1.0, it.eval(2.0), 0.0)
            assertEquals(0.0, it.eval(Double.NaN), 0.0)
            assertEquals(1.0, it.eval(Double.POSITIVE_INFINITY), 0.0)
            assertEquals(0.0, it.eval(Double.NEGATIVE_INFINITY), 0.0)
        }
    }

    @Test
    fun everyValueIsFiniteOnAFineGrid() {
        Easing.entries.forEach { easing ->
            for (step in 0..10_000) {
                val value = easing.eval(step / 10_000.0)
                assertTrue("${easing.label} $step", value.isFinite())
                assertTrue("${easing.label} $step", value > -1.0 && value < 2.0)
            }
        }
    }

    @Test
    fun tinyAndNearOneInputsAreFinite() {
        Easing.entries.forEach {
            assertTrue(it.eval(Double.MIN_VALUE).isFinite())
            assertTrue(it.eval(Math.nextDown(1.0)).isFinite())
        }
    }

    @Test
    fun midpointsHandComputed() {
        val expected = mapOf(
            Easing.LINEAR to 0.5,
            Easing.SINE_IN to 0.2928932,
            Easing.SINE_OUT to 0.7071068,
            Easing.SINE_IN_OUT to 0.5,
            Easing.QUAD_IN to 0.25,
            Easing.QUAD_OUT to 0.75,
            Easing.QUAD_IN_OUT to 0.5,
            Easing.CUBIC_IN to 0.125,
            Easing.CUBIC_OUT to 0.875,
            Easing.CUBIC_IN_OUT to 0.5,
            Easing.QUART_IN to 0.0625,
            Easing.QUART_OUT to 0.9375,
            Easing.QUART_IN_OUT to 0.5,
            Easing.QUINT_IN to 0.03125,
            Easing.QUINT_OUT to 0.96875,
            Easing.QUINT_IN_OUT to 0.5,
            Easing.EXPO_IN to 0.03125,
            Easing.EXPO_OUT to 0.96875,
            Easing.EXPO_IN_OUT to 0.5,
            Easing.CIRC_IN to 0.1339746,
            Easing.CIRC_OUT to 0.8660254,
            Easing.CIRC_IN_OUT to 0.5,
            Easing.BACK_IN to -0.0876975,
            Easing.BACK_OUT to 1.0876975,
            Easing.BACK_IN_OUT to 0.5,
            Easing.ELASTIC_IN to -0.015625,
            Easing.ELASTIC_OUT to 1.015625,
            Easing.ELASTIC_IN_OUT to 0.5,
            Easing.BOUNCE_IN to 0.234375,
            Easing.BOUNCE_OUT to 0.765625,
            Easing.BOUNCE_IN_OUT to 0.5,
        )
        assertEquals(Easing.entries.size, expected.size)
        expected.forEach { (easing, value) -> assertEquals(easing.label, value, easing.eval(0.5), delta) }
    }

    @Test
    fun quarterPointsHandComputed() {
        assertEquals(0.0625, Easing.QUAD_IN.eval(0.25), delta)
        assertEquals(0.125, Easing.QUAD_IN_OUT.eval(0.25), delta)
        assertEquals(0.875, Easing.QUAD_IN_OUT.eval(0.75), delta)
        assertEquals(0.0625, Easing.CUBIC_IN_OUT.eval(0.25), delta)
        assertEquals(0.03125, Easing.QUART_IN_OUT.eval(0.25), delta)
        assertEquals(0.015625, Easing.QUINT_IN_OUT.eval(0.25), delta)
        assertEquals(0.015625, Easing.EXPO_IN_OUT.eval(0.25), delta)
        assertEquals(0.47265625, Easing.BOUNCE_OUT.eval(0.25), delta)
    }

    @Test
    fun plainCurvesAreMonotonic() {
        val monotonic = Easing.entries.filter {
            !it.name.startsWith("BACK") && !it.name.startsWith("ELASTIC") && !it.name.startsWith("BOUNCE")
        }
        monotonic.forEach { easing ->
            var previous = 0.0
            for (step in 1..1000) {
                val value = easing.eval(step / 1000.0)
                assertTrue("${easing.label} $step", value >= previous - 1e-12)
                previous = value
            }
        }
    }

    @Test
    fun inAndOutAreMirrored() {
        val pairs = listOf(
            Easing.SINE_IN to Easing.SINE_OUT,
            Easing.QUAD_IN to Easing.QUAD_OUT,
            Easing.CUBIC_IN to Easing.CUBIC_OUT,
            Easing.QUART_IN to Easing.QUART_OUT,
            Easing.QUINT_IN to Easing.QUINT_OUT,
            Easing.CIRC_IN to Easing.CIRC_OUT,
            Easing.BACK_IN to Easing.BACK_OUT,
            Easing.BOUNCE_IN to Easing.BOUNCE_OUT,
        )
        pairs.forEach { (forward, backward) ->
            for (step in 1..99) {
                val t = step / 100.0
                assertEquals(forward.label, forward.eval(t), 1.0 - backward.eval(1.0 - t), 1e-9)
            }
        }
    }

    @Test
    fun csharpIsWellFormed() {
        Easing.entries.forEach { easing ->
            val code = easing.csharp()
            assertTrue(code, code.startsWith("public static float ${easing.method}(float t)\n{\n"))
            assertTrue(code, code.contains("t = Mathf.Clamp01(t);"))
            assertTrue(code, code.contains("return "))
            assertEquals(code, code.count { it == '{' }, code.count { it == '}' })
            assertEquals(code, code.count { it == '(' }, code.count { it == ')' })
            assertFalse(code, code.contains('$'))
        }
    }

    @Test
    fun bounceIncludesItsHelper() {
        assertTrue(Easing.BOUNCE_IN.csharp().contains("public static float EaseOutBounce(float t)"))
        assertTrue(Easing.BOUNCE_IN_OUT.csharp().contains("public static float EaseOutBounce(float t)"))
    }

    @Test
    fun namesAreUnique() {
        assertEquals(Easing.entries.size, Easing.entries.map { it.method }.toSet().size)
        assertEquals(Easing.entries.size, Easing.entries.map { it.label }.toSet().size)
        val pattern = Regex("[A-Za-z][A-Za-z0-9]*")
        Easing.entries.forEach { assertTrue(pattern.matches(it.method)) }
    }

    @Test
    fun fromLabel() {
        Easing.entries.forEach { assertEquals(it, Easing.fromLabel(it.label)) }
        assertEquals(Easing.LINEAR, Easing.fromLabel("bogus"))
        assertEquals(Easing.LINEAR, Easing.fromLabel(null))
    }

    @Test
    fun samples() {
        val values = Easing.QUAD_IN.samples(3)
        assertEquals(listOf(0.0, 0.25, 1.0), values.toList())
        assertEquals(2, Easing.LINEAR.samples(0).size)
        assertEquals(2, Easing.LINEAR.samples(-5).size)
        assertEquals(Easing.MAX_SAMPLES, Easing.LINEAR.samples(Int.MAX_VALUE).size)
    }
}

class TextureMemoryTest {
    private fun ok(w: Int, h: Int, format: TextureFormat, mips: Boolean = false, count: Int = 1) =
        (TextureMemory.compute(w, h, format, mips, count) as Checked.Ok).value

    private fun bytes(w: Int, h: Int, format: TextureFormat) = ok(w, h, format).totalBytes

    @Test
    fun uncompressedHandComputed() {
        assertEquals(4_194_304L, bytes(1024, 1024, TextureFormat.RGBA32))
        assertEquals(2_097_152L, bytes(1024, 1024, TextureFormat.RGB565))
        assertEquals(4L, bytes(1, 1, TextureFormat.RGBA32))
        assertEquals(30L, bytes(5, 3, TextureFormat.RGB565))
    }

    @Test
    fun blockFormatsHandComputed() {
        assertEquals(524_288L, bytes(1024, 1024, TextureFormat.BC1))
        assertEquals(1_048_576L, bytes(1024, 1024, TextureFormat.BC3))
        assertEquals(1_048_576L, bytes(1024, 1024, TextureFormat.BC7))
        assertEquals(1_048_576L, bytes(1024, 1024, TextureFormat.ASTC_4))
        assertEquals(524_288L, bytes(1024, 1024, TextureFormat.ETC2_RGB))
        assertEquals(1_048_576L, bytes(1024, 1024, TextureFormat.ETC2_RGBA))
    }

    @Test
    fun astcRoundsUpToWholeBlocks() {
        assertEquals(4624L, bytes(100, 100, TextureFormat.ASTC_6))
        assertEquals(2704L, bytes(100, 100, TextureFormat.ASTC_8))
        assertEquals(16L, bytes(1, 1, TextureFormat.ASTC_8))
        assertEquals(16L, bytes(8, 8, TextureFormat.ASTC_8))
        assertEquals(32L, bytes(9, 8, TextureFormat.ASTC_8))
    }

    @Test
    fun smallBlockTexturesTakeOneBlock() {
        assertEquals(8L, bytes(1, 1, TextureFormat.BC1))
        assertEquals(8L, bytes(4, 4, TextureFormat.BC1))
        assertEquals(16L, bytes(5, 4, TextureFormat.BC1))
    }

    @Test
    fun mipLevelsHandComputed() {
        assertEquals(1, TextureMemory.mipLevels(1, 1))
        assertEquals(2, TextureMemory.mipLevels(2, 2))
        assertEquals(11, TextureMemory.mipLevels(1024, 1024))
        assertEquals(11, TextureMemory.mipLevels(1024, 1))
        assertEquals(11, TextureMemory.mipLevels(1, 1024))
        assertEquals(10, TextureMemory.mipLevels(1023, 7))
        assertEquals(17, TextureMemory.mipLevels(65536, 65536))
    }

    @Test
    fun mipChainHandComputed() {
        val rgba = ok(1024, 1024, TextureFormat.RGBA32, mips = true)
        assertEquals(5_592_404L, rgba.bytesPerTexture)
        assertEquals(11, rgba.mipLevels)
        assertEquals(4_194_304L, rgba.topLevelBytes)
        assertEquals(699_064L, ok(1024, 1024, TextureFormat.BC1, mips = true).bytesPerTexture)
    }

    @Test
    fun rectangularMipChainHandComputed() {
        assertEquals(44L, ok(4, 2, TextureFormat.RGBA32, mips = true).bytesPerTexture)
        assertEquals(3, ok(4, 2, TextureFormat.RGBA32, mips = true).mipLevels)
    }

    @Test
    fun noMipsMeansOneLevel() {
        val result = ok(1024, 1024, TextureFormat.RGBA32)
        assertEquals(1, result.mipLevels)
        assertEquals(result.topLevelBytes, result.bytesPerTexture)
    }

    @Test
    fun countMultiplies() {
        assertEquals(40L, ok(1, 1, TextureFormat.RGBA32, count = 10).totalBytes)
        assertEquals(4L, ok(1, 1, TextureFormat.RGBA32, count = 10).bytesPerTexture)
    }

    @Test
    fun largestInputsDoNotOverflow() {
        val result = ok(TextureMemory.MAX_SIZE, TextureMemory.MAX_SIZE, TextureFormat.RGBA32, mips = true, count = TextureMemory.MAX_COUNT)
        assertTrue(result.totalBytes > 0)
        assertEquals(result.bytesPerTexture * TextureMemory.MAX_COUNT, result.totalBytes)
    }

    @Test
    fun invalidSizesAreRefused() {
        listOf(0 to 1, 1 to 0, -1 to 1, 1 to -1, 65537 to 1, 1 to 65537, Int.MAX_VALUE to Int.MAX_VALUE, Int.MIN_VALUE to 1).forEach { (w, h) ->
            assertTrue(TextureMemory.compute(w, h, TextureFormat.RGBA32, true, 1) is Checked.Bad)
        }
    }

    @Test
    fun invalidCountsAreRefused() {
        listOf(0, -1, TextureMemory.MAX_COUNT + 1, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
            assertTrue(TextureMemory.compute(4, 4, TextureFormat.RGBA32, true, it) is Checked.Bad)
        }
    }

    @Test
    fun notes() {
        assertTrue(ok(1024, 1024, TextureFormat.BC1, mips = true).notes.isEmpty())
        assertEquals(1, ok(1000, 1000, TextureFormat.RGBA32, mips = true).notes.size)
        assertTrue(ok(1000, 1000, TextureFormat.RGBA32).notes.isEmpty())
        assertEquals(1, ok(1001, 1000, TextureFormat.BC1).notes.size)
        assertEquals(2, ok(1001, 1000, TextureFormat.BC1, mips = true).notes.size)
    }

    @Test
    fun fromLabel() {
        TextureFormat.entries.forEach { assertEquals(it, TextureFormat.fromLabel(it.label)) }
        assertEquals(TextureFormat.RGBA32, TextureFormat.fromLabel("bogus"))
        assertEquals(TextureFormat.RGBA32, TextureFormat.fromLabel(null))
    }
}

class FrameBudgetTest {
    private fun ok(fps: Double, vararg costs: Double) =
        (FrameBudget.compute(fps, costs.map { CostEntry("x", it) }) as Checked.Ok).value

    @Test
    fun fpsToMsHandComputed() {
        assertEquals(16.6666667, FrameBudget.budgetMs(60.0).valueOrNull()!!, 1e-6)
        assertEquals(33.3333333, FrameBudget.budgetMs(30.0).valueOrNull()!!, 1e-6)
        assertEquals(8.3333333, FrameBudget.budgetMs(120.0).valueOrNull()!!, 1e-6)
        assertEquals(1000.0, FrameBudget.budgetMs(1.0).valueOrNull()!!, 0.0)
        assertEquals(1.0, FrameBudget.budgetMs(1000.0).valueOrNull()!!, 0.0)
    }

    @Test
    fun invalidFpsIsRefused() {
        listOf(0.0, -60.0, 0.5, 1000.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE).forEach {
            assertTrue(FrameBudget.budgetMs(it) is Checked.Bad)
            assertTrue(FrameBudget.compute(it, emptyList()) is Checked.Bad)
        }
    }

    @Test
    fun remainingHandComputed() {
        val result = ok(50.0, 6.0, 4.0)
        assertEquals(20.0, result.budgetMs, 1e-12)
        assertEquals(10.0, result.usedMs, 1e-12)
        assertEquals(10.0, result.remainingMs, 1e-12)
        assertEquals(0.5, result.usedFraction, 1e-12)
        assertFalse(result.overBudget)
        assertEquals(0.3, result.entries[0].fraction, 1e-12)
    }

    @Test
    fun overBudget() {
        val result = ok(100.0, 12.0)
        assertTrue(result.overBudget)
        assertEquals(-2.0, result.remainingMs, 1e-12)
        assertEquals(1.2, result.usedFraction, 1e-12)
    }

    @Test
    fun exactlyOnBudgetIsNotOver() {
        assertFalse(ok(100.0, 10.0).overBudget)
    }

    @Test
    fun noEntries() {
        val result = ok(60.0)
        assertEquals(0.0, result.usedMs, 0.0)
        assertEquals(result.budgetMs, result.remainingMs, 0.0)
        assertFalse(result.overBudget)
    }

    @Test
    fun invalidCostsAreRefused() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e7).forEach {
            assertTrue(FrameBudget.compute(60.0, listOf(CostEntry("x", it))) is Checked.Bad)
        }
    }

    @Test
    fun tooManyEntriesAreRefused() {
        assertTrue(FrameBudget.compute(60.0, List(101) { CostEntry("x", 1.0) }) is Checked.Bad)
        assertTrue(FrameBudget.compute(60.0, List(100) { CostEntry("x", 1.0) }) is Checked.Ok)
    }

    @Test
    fun extremeButValidStaysFinite() {
        val result = (FrameBudget.compute(1000.0, List(100) { CostEntry("x", FrameBudget.MAX_COST_MS) }) as Checked.Ok).value
        assertTrue(result.usedMs.isFinite() && result.remainingMs.isFinite() && result.usedFraction.isFinite())
    }

    @Test
    fun cleanName() {
        assertEquals("System 2", FrameBudget.cleanName("  ", 1))
        assertEquals("ab", FrameBudget.cleanName("a‮b", 0))
    }
}

class DamageTest {
    private fun input(
        formula: DamageFormula = DamageFormula.SUBTRACTIVE,
        attack: Double = 100.0,
        defense: Double = 40.0,
        crit: Double = 25.0,
        multiplier: Double = 2.0,
        aps: Double = 1.5,
        minimum: Double = 1.0,
    ) = DamageInput(formula, attack, defense, crit, multiplier, aps, minimum)

    private fun ok(value: DamageInput) = (Damage.compute(value) as Checked.Ok).value

    @Test
    fun subtractiveHandComputed() {
        val result = ok(input())
        assertEquals(60.0, result.normalHit, 1e-12)
        assertEquals(120.0, result.critHit, 1e-12)
        assertEquals(75.0, result.perHit, 1e-12)
        assertEquals(112.5, result.perSecond, 1e-12)
    }

    @Test
    fun minimumDamageAppliesWhenDefenseIsHigher() {
        val result = ok(input(attack = 10.0, defense = 500.0, minimum = 3.0, crit = 0.0))
        assertEquals(3.0, result.normalHit, 0.0)
        assertEquals(3.0, result.perHit, 0.0)
    }

    @Test
    fun armorHandComputed() {
        assertEquals(50.0, ok(input(DamageFormula.ARMOR, defense = 100.0, crit = 0.0)).normalHit, 1e-12)
        assertEquals(100.0, ok(input(DamageFormula.ARMOR, defense = 0.0, crit = 0.0)).normalHit, 1e-12)
    }

    @Test
    fun ratioHandComputed() {
        assertEquals(50.0, ok(input(DamageFormula.RATIO, defense = 100.0, crit = 0.0)).normalHit, 1e-12)
        assertEquals(100.0, ok(input(DamageFormula.RATIO, defense = 0.0, crit = 0.0)).normalHit, 1e-12)
    }

    @Test
    fun zeroAttackAndDefenseIsNotNan() {
        val result = ok(input(DamageFormula.RATIO, attack = 0.0, defense = 0.0, minimum = 0.0))
        assertEquals(0.0, result.perSecond, 0.0)
    }

    @Test
    fun critChanceEdges() {
        assertEquals(60.0, ok(input(crit = 0.0)).perHit, 1e-12)
        assertEquals(120.0, ok(input(crit = 100.0)).perHit, 1e-12)
    }

    @Test
    fun zeroAttacksPerSecond() {
        assertEquals(0.0, ok(input(aps = 0.0)).perSecond, 0.0)
    }

    @Test
    fun multiplierBelowOneIsAPenalty() {
        assertEquals(30.0, ok(input(multiplier = 0.5, crit = 100.0)).perHit, 1e-12)
    }

    @Test
    fun invalidInputsAreRefused() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0).forEach {
            assertTrue(Damage.compute(input(attack = it)) is Checked.Bad)
            assertTrue(Damage.compute(input(defense = it)) is Checked.Bad)
            assertTrue(Damage.compute(input(crit = it)) is Checked.Bad)
            assertTrue(Damage.compute(input(multiplier = it)) is Checked.Bad)
            assertTrue(Damage.compute(input(aps = it)) is Checked.Bad)
            assertTrue(Damage.compute(input(minimum = it)) is Checked.Bad)
        }
        assertTrue(Damage.compute(input(crit = 100.5)) is Checked.Bad)
        assertTrue(Damage.compute(input(attack = 1e13)) is Checked.Bad)
        assertTrue(Damage.compute(input(defense = 1e13)) is Checked.Bad)
        assertTrue(Damage.compute(input(multiplier = 1e7)) is Checked.Bad)
        assertTrue(Damage.compute(input(aps = 1001.0)) is Checked.Bad)
        assertTrue(Damage.compute(input(minimum = 1e13)) is Checked.Bad)
    }

    @Test
    fun largestValidInputsStayFinite() {
        DamageFormula.entries.forEach {
            val result = ok(input(it, Damage.MAX_STAT, Damage.MAX_STAT, 100.0, Damage.MAX_MULTIPLIER, Damage.MAX_ATTACKS_PER_SECOND, Damage.MAX_STAT))
            assertTrue(result.perSecond.isFinite())
            assertTrue(result.critHit.isFinite())
        }
    }

    @Test
    fun fromLabel() {
        DamageFormula.entries.forEach { assertEquals(it, DamageFormula.fromLabel(it.label)) }
        assertEquals(DamageFormula.SUBTRACTIVE, DamageFormula.fromLabel("bogus"))
        assertEquals(DamageFormula.SUBTRACTIVE, DamageFormula.fromLabel(null))
    }
}

class ScreenMathTest {
    private fun <T> ok(result: Checked<T>) = (result as Checked.Ok).value

    @Test
    fun gcdHandComputed() {
        assertEquals(120L, ScreenMath.gcd(1920, 1080))
        assertEquals(5L, ScreenMath.gcd(0, 5))
        assertEquals(5L, ScreenMath.gcd(5, 0))
        assertEquals(0L, ScreenMath.gcd(0, 0))
        assertEquals(6L, ScreenMath.gcd(-12, 18))
    }

    @Test
    fun aspectHandComputed() {
        val hd = ok(ScreenMath.aspect(1920, 1080))
        assertEquals(16L, hd.ratioWidth)
        assertEquals(9L, hd.ratioHeight)
        assertEquals(1.7777778, hd.decimal, 1e-6)
        assertEquals(2.0736, hd.megapixels, 1e-9)
        val phone = ok(ScreenMath.aspect(2400, 1080))
        assertEquals(20L, phone.ratioWidth)
        assertEquals(9L, phone.ratioHeight)
        val odd = ok(ScreenMath.aspect(1366, 768))
        assertEquals(683L, odd.ratioWidth)
        assertEquals(384L, odd.ratioHeight)
    }

    @Test
    fun aspectRefusals() {
        listOf(0 to 1, 1 to 0, -1 to 1, 1 to -1, 1_000_001 to 1, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (w, h) ->
            assertTrue(ScreenMath.aspect(w, h) is Checked.Bad)
        }
    }

    @Test
    fun heightForHandComputed() {
        assertEquals(720L, ok(ScreenMath.heightFor(1280, 16, 9)))
        assertEquals(960L, ok(ScreenMath.heightFor(1280, 4, 3)))
        assertEquals(1L, ok(ScreenMath.heightFor(1, 3, 2)))
    }

    @Test
    fun heightForRefusals() {
        assertTrue(ScreenMath.heightFor(0, 16, 9) is Checked.Bad)
        assertTrue(ScreenMath.heightFor(1280, 0, 9) is Checked.Bad)
        assertTrue(ScreenMath.heightFor(1280, 16, 0) is Checked.Bad)
        assertTrue(ScreenMath.heightFor(1280, -16, 9) is Checked.Bad)
        assertTrue(ScreenMath.heightFor(1280, 100_001, 9) is Checked.Bad)
    }

    @Test
    fun largestHeightForIsFine() {
        assertEquals(100_000_000_000L, ok(ScreenMath.heightFor(1_000_000, 1, 100_000)))
    }

    @Test
    fun safeAreaHandComputed() {
        val safe = ok(ScreenMath.safeArea(1080, 2400, 0, 100, 0, 50))
        assertEquals(1080, safe.safeWidth)
        assertEquals(2250, safe.safeHeight)
        assertEquals(0.0, safe.anchorMinX, 0.0)
        assertEquals(50.0 / 2400.0, safe.anchorMinY, 1e-12)
        assertEquals(1.0, safe.anchorMaxX, 0.0)
        assertEquals(2300.0 / 2400.0, safe.anchorMaxY, 1e-12)
    }

    @Test
    fun safeAreaRefusals() {
        assertTrue(ScreenMath.safeArea(100, 100, 50, 0, 50, 0) is Checked.Bad)
        assertTrue(ScreenMath.safeArea(100, 100, 0, 60, 0, 60) is Checked.Bad)
        assertTrue(ScreenMath.safeArea(100, 100, -1, 0, 0, 0) is Checked.Bad)
        assertTrue(ScreenMath.safeArea(0, 100, 0, 0, 0, 0) is Checked.Bad)
        assertTrue(ScreenMath.safeArea(100, 100, Int.MAX_VALUE, 0, Int.MAX_VALUE, 0) is Checked.Bad)
    }

    @Test
    fun cameraHandComputed() {
        val camera = ok(ScreenMath.camera(1920, 1080, 100.0, 1.0))
        assertEquals(5.4, camera.orthographicSize, 1e-12)
        assertEquals(10.8, camera.worldHeight, 1e-12)
        assertEquals(19.2, camera.worldWidth, 1e-12)
        assertEquals(0.01, camera.unitsPerPixel, 1e-12)
    }

    @Test
    fun cameraZoomHandComputed() {
        assertEquals(2.7, ok(ScreenMath.camera(1920, 1080, 100.0, 2.0)).orthographicSize, 1e-12)
        assertEquals(11.25, ok(ScreenMath.camera(1280, 720, 16.0, 2.0)).orthographicSize, 1e-12)
    }

    @Test
    fun cameraRefusals() {
        listOf(0.0, -1.0, 1e-320, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, 100_001.0).forEach {
            assertTrue(ScreenMath.camera(100, 100, it, 1.0) is Checked.Bad)
        }
        listOf(0.0, -1.0, 1e-320, 0.001, Double.NaN, Double.POSITIVE_INFINITY, 1001.0).forEach {
            assertTrue(ScreenMath.camera(100, 100, 100.0, it) is Checked.Bad)
        }
        assertTrue(ScreenMath.camera(0, 100, 100.0, 1.0) is Checked.Bad)
    }

    @Test
    fun cameraExtremeValuesAreFinite() {
        val camera = ok(ScreenMath.camera(1_000_000, 1_000_000, ScreenMath.MIN_PPU, ScreenMath.MIN_ZOOM))
        assertTrue(camera.worldWidth.isFinite() && camera.orthographicSize.isFinite())
    }

    @Test
    fun tileGridHandComputed() {
        val grid = ok(ScreenMath.tileGrid(1920, 1080, 32, 1.0))
        assertEquals(60L, grid.fullColumns)
        assertEquals(33L, grid.fullRows)
        assertEquals(60L, grid.visibleColumns)
        assertEquals(34L, grid.visibleRows)
        assertEquals(1980L, grid.fullTiles)
        assertEquals(2040L, grid.visibleTiles)
    }

    @Test
    fun tileGridZoom() {
        val grid = ok(ScreenMath.tileGrid(1920, 1080, 32, 2.0))
        assertEquals(30L, grid.fullColumns)
        assertEquals(16L, grid.fullRows)
        assertEquals(17L, grid.visibleRows)
    }

    @Test
    fun tileLargerThanScreen() {
        val grid = ok(ScreenMath.tileGrid(10, 10, 32, 1.0))
        assertEquals(0L, grid.fullColumns)
        assertEquals(1L, grid.visibleColumns)
    }

    @Test
    fun tileGridExtremeDoesNotOverflow() {
        val grid = ok(ScreenMath.tileGrid(1_000_000, 1_000_000, 1, ScreenMath.MIN_ZOOM))
        assertEquals(100_000_000L, grid.fullColumns)
        assertEquals(10_000_000_000_000_000L, grid.fullTiles)
    }

    @Test
    fun tileGridRefusals() {
        assertTrue(ScreenMath.tileGrid(100, 100, 0, 1.0) is Checked.Bad)
        assertTrue(ScreenMath.tileGrid(100, 100, -32, 1.0) is Checked.Bad)
        assertTrue(ScreenMath.tileGrid(100, 100, 65537, 1.0) is Checked.Bad)
        assertTrue(ScreenMath.tileGrid(100, 100, 32, 0.0) is Checked.Bad)
        assertTrue(ScreenMath.tileGrid(100, 100, 32, Double.NaN) is Checked.Bad)
        assertTrue(ScreenMath.tileGrid(0, 100, 32, 1.0) is Checked.Bad)
    }

    @Test
    fun atlasSizeHandComputed() {
        val atlas = ok(ScreenMath.atlasSize(16, 16, 32, 32, 2, 1))
        assertEquals(544L, atlas.width)
        assertEquals(544L, atlas.height)
        assertEquals(1024L, atlas.powerOfTwoWidth)
        assertEquals(256L, atlas.tiles)
    }

    @Test
    fun atlasWithoutPadding() {
        val atlas = ok(ScreenMath.atlasSize(4, 2, 32, 16, 0, 0))
        assertEquals(128L, atlas.width)
        assertEquals(32L, atlas.height)
        assertEquals(128L, atlas.powerOfTwoWidth)
        assertEquals(32L, atlas.powerOfTwoHeight)
    }

    @Test
    fun atlasSingleTileHasNoPadding() {
        assertEquals(34L, ok(ScreenMath.atlasSize(1, 1, 32, 32, 100, 1)).width)
    }

    @Test
    fun atlasLargestDoesNotOverflow() {
        val atlas = ok(ScreenMath.atlasSize(100_000, 100_000, 65536, 65536, 4096, 4096))
        assertEquals(100_000L * 65536 + 99_999L * 4096 + 8192, atlas.width)
        assertEquals(10_000_000_000L, atlas.tiles)
    }

    @Test
    fun atlasCapacityHandComputed() {
        val capacity = ok(ScreenMath.atlasCapacity(1024, 1024, 32, 32, 2, 1))
        assertEquals(30L, capacity.columns)
        assertEquals(30L, capacity.rows)
        assertEquals(900L, capacity.tiles)
    }

    @Test
    fun atlasCapacityRoundTrip() {
        val size = ok(ScreenMath.atlasSize(7, 5, 24, 16, 3, 2))
        val capacity = ok(ScreenMath.atlasCapacity(size.width.toInt(), size.height.toInt(), 24, 16, 3, 2))
        assertEquals(7L, capacity.columns)
        assertEquals(5L, capacity.rows)
    }

    @Test
    fun atlasCapacityTooSmall() {
        assertEquals(0L, ok(ScreenMath.atlasCapacity(16, 16, 32, 32, 0, 0)).tiles)
        assertEquals(0L, ok(ScreenMath.atlasCapacity(32, 32, 32, 32, 0, 1)).tiles)
        assertEquals(1L, ok(ScreenMath.atlasCapacity(32, 32, 32, 32, 4096, 0)).tiles)
    }

    @Test
    fun atlasRefusals() {
        assertTrue(ScreenMath.atlasSize(0, 1, 32, 32, 0, 0) is Checked.Bad)
        assertTrue(ScreenMath.atlasSize(1, 100_001, 32, 32, 0, 0) is Checked.Bad)
        assertTrue(ScreenMath.atlasSize(1, 1, 0, 32, 0, 0) is Checked.Bad)
        assertTrue(ScreenMath.atlasSize(1, 1, 32, 32, -1, 0) is Checked.Bad)
        assertTrue(ScreenMath.atlasSize(1, 1, 32, 32, 0, 4097) is Checked.Bad)
        assertTrue(ScreenMath.atlasCapacity(0, 1, 32, 32, 0, 0) is Checked.Bad)
        assertTrue(ScreenMath.atlasCapacity(1, 1, -32, 32, 0, 0) is Checked.Bad)
    }

    @Test
    fun nextPowerOfTwoHandComputed() {
        assertEquals(1L, ScreenMath.nextPowerOfTwo(0))
        assertEquals(1L, ScreenMath.nextPowerOfTwo(-5))
        assertEquals(1L, ScreenMath.nextPowerOfTwo(1))
        assertEquals(2L, ScreenMath.nextPowerOfTwo(2))
        assertEquals(4L, ScreenMath.nextPowerOfTwo(3))
        assertEquals(1024L, ScreenMath.nextPowerOfTwo(1024))
        assertEquals(2048L, ScreenMath.nextPowerOfTwo(1025))
    }
}
