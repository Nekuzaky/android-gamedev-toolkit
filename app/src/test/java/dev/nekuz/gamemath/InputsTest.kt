package dev.nekuz.gamemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputsTest {
    private fun bad(result: Checked<*>) = assertTrue(result.toString(), result is Checked.Bad)

    private fun anyDouble(raw: String?) = Inputs.double(raw, -1e300, 1e300)

    @Test
    fun plainDecimals() {
        assertEquals(1.5, anyDouble("1.5").valueOrNull()!!, 0.0)
        assertEquals(-2.0, anyDouble("-2").valueOrNull()!!, 0.0)
        assertEquals(2.0, anyDouble("+2").valueOrNull()!!, 0.0)
        assertEquals(0.5, anyDouble(".5").valueOrNull()!!, 0.0)
        assertEquals(5.0, anyDouble("5.").valueOrNull()!!, 0.0)
        assertEquals(1000.0, anyDouble("1e3").valueOrNull()!!, 0.0)
        assertEquals(0.001, anyDouble("1E-3").valueOrNull()!!, 0.0)
        assertEquals(3.0, anyDouble("  3 ").valueOrNull()!!, 0.0)
    }

    @Test
    fun commaIsADecimalSeparatorWhenUnambiguous() {
        assertEquals(1.5, anyDouble("1,5").valueOrNull()!!, 0.0)
        assertEquals(1.25, anyDouble("1,25").valueOrNull()!!, 0.0)
    }

    @Test
    fun ambiguousCommasAreRefused() {
        listOf("1,000", "12,345", "1,000,000", "1.5,2", "1,2,3").forEach {
            assertEquals(it, Checked.Bad(Inputs.AMBIGUOUS_COMMA), anyDouble(it))
        }
    }

    @Test
    fun negativeZeroBecomesZero() {
        val value = anyDouble("-0").valueOrNull()!!
        assertEquals("0.0", value.toString())
    }

    @Test
    fun hostileNumbersAreRefused() {
        listOf(
            "NaN", "nan", "Infinity", "-Infinity", "inf", "0x10", "0x1p3", "1f", "1d", "1L", "1 2", "1e", "e5", "--1",
            "+-1", "1..2", ".", "-", "+", "١٢", "１２", "1_000", "1e5e5", "\u00001", "1\n2", "<script>", "'; DROP",
        ).forEach { bad(anyDouble(it)) }
    }

    @Test
    fun emptyAndNullAreRequired() {
        assertEquals(Checked.Bad(Inputs.REQUIRED), anyDouble(null))
        assertEquals(Checked.Bad(Inputs.REQUIRED), anyDouble(""))
        assertEquals(Checked.Bad(Inputs.REQUIRED), anyDouble("   "))
        assertEquals(Checked.Bad(Inputs.REQUIRED), Inputs.long(null, 0, 1))
        assertEquals(Checked.Bad(Inputs.REQUIRED), Inputs.long("", 0, 1))
    }

    @Test
    fun longTextIsRefusedBeforeParsing() {
        assertEquals(Checked.Bad(Inputs.TOO_LONG), anyDouble("1".repeat(41)))
        assertEquals(Checked.Bad(Inputs.TOO_LONG), Inputs.long("1".repeat(1_000_000), 0, 1))
    }

    @Test
    fun overflowingDoublesAreRefused() {
        bad(Inputs.double("1e309", -Double.MAX_VALUE, Double.MAX_VALUE))
        bad(Inputs.double("-1e309", -Double.MAX_VALUE, Double.MAX_VALUE))
    }

    @Test
    fun rangesAreInclusive() {
        assertEquals(0.0, Inputs.double("0", 0.0, 1.0).valueOrNull()!!, 0.0)
        assertEquals(1.0, Inputs.double("1", 0.0, 1.0).valueOrNull()!!, 0.0)
        bad(Inputs.double("1.0000001", 0.0, 1.0))
        bad(Inputs.double("-0.0000001", 0.0, 1.0))
    }

    @Test
    fun wholeNumbers() {
        assertEquals(12L, Inputs.long("12", 0, 100).valueOrNull())
        assertEquals(-12L, Inputs.long("-12", -100, 100).valueOrNull())
        assertEquals(12L, Inputs.long("+12", 0, 100).valueOrNull())
        assertEquals(Long.MAX_VALUE, Inputs.long("9223372036854775807", 0, Long.MAX_VALUE).valueOrNull())
        assertEquals(Long.MIN_VALUE, Inputs.long("-9223372036854775808", Long.MIN_VALUE, 0).valueOrNull())
    }

    @Test
    fun wholeNumberHostileInput() {
        listOf("1.5", "1e3", "NaN", "0x10", "1,000", "abc", "1 000", "--1", "٣").forEach { bad(Inputs.long(it, -9, 9999)) }
        bad(Inputs.long("9223372036854775808", 0, Long.MAX_VALUE))
        bad(Inputs.long("-9223372036854775809", Long.MIN_VALUE, 0))
        bad(Inputs.long("101", 0, 100))
        bad(Inputs.long("-1", 0, 100))
    }

    @Test
    fun intStaysInsideIntRange() {
        assertEquals(Int.MAX_VALUE, Inputs.int("2147483647", 0, Int.MAX_VALUE).valueOrNull())
        bad(Inputs.int("2147483648", 0, Int.MAX_VALUE))
        bad(Inputs.int("-2147483649", Int.MIN_VALUE, 0))
    }

    @Test
    fun optionalInt() {
        assertEquals(Checked.Ok(null), Inputs.optionalInt("", 1, 5))
        assertEquals(Checked.Ok(null), Inputs.optionalInt("  ", 1, 5))
        assertEquals(Checked.Ok(null), Inputs.optionalInt(null, 1, 5))
        assertEquals(Checked.Ok(3), Inputs.optionalInt("3", 1, 5))
        bad(Inputs.optionalInt("9", 1, 5))
        bad(Inputs.optionalInt("x", 1, 5))
    }

    @Test
    fun valueOrNull() {
        assertEquals(1, Checked.Ok(1).valueOrNull())
        assertNull(Checked.Bad("x").valueOrNull())
    }
}

class FmtTest {
    @Test
    fun numbers() {
        assertEquals("0", Fmt.number(0.0))
        assertEquals("0", Fmt.number(-0.0))
        assertEquals("1.5", Fmt.number(1.5))
        assertEquals("100", Fmt.number(100.0))
        assertEquals("-3.25", Fmt.number(-3.25))
        assertEquals("0.3333", Fmt.number(1.0 / 3.0))
        assertEquals("0.667", Fmt.number(2.0 / 3.0, 3))
    }

    @Test
    fun neverShowsNanOrInfinity() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertEquals(Fmt.NOT_AVAILABLE, Fmt.number(it))
            assertEquals(Fmt.NOT_AVAILABLE, Fmt.percent(it))
        }
    }

    @Test
    fun extremesAreScientific() {
        assertEquals("1.000e+15", Fmt.number(1e15))
        assertEquals("-1.000e+300", Fmt.number(-1e300))
        assertEquals("1.000e-05", Fmt.number(1e-5))
        assertEquals("1.798e+308", Fmt.number(Double.MAX_VALUE))
        assertEquals("4.900e-324", Fmt.number(Double.MIN_VALUE))
    }

    @Test
    fun percents() {
        assertEquals("50%", Fmt.percent(0.5))
        assertEquals("12.5%", Fmt.percent(0.125))
        assertEquals("100%", Fmt.percent(1.0))
        assertEquals("0%", Fmt.percent(0.0))
    }

    @Test
    fun bytes() {
        assertEquals("0 B", Fmt.bytes(0))
        assertEquals("1023 B", Fmt.bytes(1023))
        assertEquals("1.00 KiB", Fmt.bytes(1024))
        assertEquals("4.00 MiB", Fmt.bytes(4_194_304))
        assertEquals("8.00 EiB", Fmt.bytes(Long.MAX_VALUE))
        assertEquals(Fmt.NOT_AVAILABLE, Fmt.bytes(-1))
    }

    @Test
    fun preview() {
        assertEquals("abc", Fmt.preview("abc", 10))
        assertEquals("ab", Fmt.preview("abc", 2))
        assertEquals("", Fmt.preview("abc", -1))
    }
}
