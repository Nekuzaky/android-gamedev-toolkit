package dev.nekuz.gamemath

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CSharpTest {
    private fun ok(result: Checked<String>) = (result as Checked.Ok).value

    private val identifierPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private val hostileNames = listOf(
        "\"; System.IO.File.Delete(\"x\"); //",
        "\\",
        "\\\"",
        "a\nb",
        "a\r\nb",
        "a\u0085b",
        "a\u2028b",
        "a\u2029b",
        "\u202Eevil",
        "\u0000",
        "*/ DROP /*",
        "{0}",
        "$\"{Hack()}\"",
        "@\"verbatim",
        "\"\"\"raw",
        "\\u0022",
        "😀",
        "\uD83D",
        "\uDE00",
        "class",
        "",
        "   ",
    )

    private fun assertStringLiteralIsClosed(literal: String) {
        assertTrue(literal, literal.length >= 2 && literal.first() == '"' && literal.last() == '"')
        var index = 1
        while (index < literal.length - 1) {
            val char = literal[index]
            assertFalse(literal, char == '"')
            assertTrue(literal, char.code in 0x20..0x7E)
            if (char == '\\') {
                index++
                val next = literal[index]
                assertTrue(literal, next in "\"\\nrtu")
                assertTrue(literal, index < literal.length - 1)
                if (next == 'u') {
                    val hex = literal.substring(index + 1, index + 5)
                    assertTrue(literal, hex.all { it in "0123456789ABCDEF" })
                    index += 4
                }
            }
            index++
        }
    }

    @Test
    fun stringLiteralHandComputed() {
        assertEquals("\"Sword\"", CSharp.stringLiteral("Sword"))
        assertEquals("\"\"", CSharp.stringLiteral(""))
        assertEquals("\"a\\\"b\"", CSharp.stringLiteral("a\"b"))
        assertEquals("\"a\\\\b\"", CSharp.stringLiteral("a\\b"))
        assertEquals("\"a\\nb\"", CSharp.stringLiteral("a\nb"))
        assertEquals("\"a\\rb\"", CSharp.stringLiteral("a\rb"))
        assertEquals("\"a\\tb\"", CSharp.stringLiteral("a\tb"))
        assertEquals("\"\\u0000\"", CSharp.stringLiteral("\u0000"))
        assertEquals("\"\\u00E9\"", CSharp.stringLiteral("é"))
        assertEquals("\"\\u2028\"", CSharp.stringLiteral("\u2028"))
        assertEquals("\"\\u202E\"", CSharp.stringLiteral("\u202E"))
        assertEquals("\"\\uD83D\\uDE00\"", CSharp.stringLiteral("😀"))
    }

    @Test
    fun trailingBackslashCannotEatTheClosingQuote() {
        assertEquals("\"x\\\\\"", CSharp.stringLiteral("x\\"))
    }

    @Test
    fun alreadyEscapedTextIsEscapedAgain() {
        assertEquals("\"\\\\u0022\"", CSharp.stringLiteral("\\u0022"))
    }

    @Test
    fun hostileNamesStayInsideTheLiteral() {
        hostileNames.forEach { assertStringLiteralIsClosed(CSharp.stringLiteral(it)) }
    }

    @Test
    fun everyCharInTheBmpStaysInsideTheLiteral() {
        val all = String(CharArray(0x10000) { it.toChar() })
        assertStringLiteralIsClosed(CSharp.stringLiteral(all))
    }

    @Test
    fun identifierHandComputed() {
        assertEquals("Sword", CSharp.identifier("Sword", "F"))
        assertEquals("Iron_Sword", CSharp.identifier("Iron Sword", "F"))
        assertEquals("Iron_Sword", CSharp.identifier("Iron   Sword!!!", "F"))
        assertEquals("_9lives", CSharp.identifier("9lives", "F"))
        assertEquals("_class", CSharp.identifier("class", "F"))
        assertEquals("Class", CSharp.identifier("Class", "F"))
        assertEquals("F", CSharp.identifier("", "F"))
        assertEquals("F", CSharp.identifier(null, "F"))
        assertEquals("F", CSharp.identifier("!!!", "F"))
        assertEquals("F", CSharp.identifier("___", "F"))
        assertEquals("p_e", CSharp.identifier("épée", "F"))
    }

    @Test
    fun allKeywordsAreEscaped() {
        listOf("if", "while", "namespace", "using", "void", "null", "true", "false", "int", "string", "object").forEach {
            assertEquals("_$it", CSharp.identifier(it, "F"))
        }
    }

    @Test
    fun identifierReservedDoubleUnderscoreIsCollapsed() {
        assertEquals("value", CSharp.identifier("value__", "F"))
        assertEquals("a_b", CSharp.identifier("a__b", "F"))
        assertEquals("a_b", CSharp.identifier("__a__b__", "F"))
    }

    @Test
    fun identifierFallbackIsSanitisedToo() {
        assertEquals("a_b", CSharp.identifier("", "a b"))
        assertEquals("_class", CSharp.identifier("", "class"))
        assertEquals("Generated", CSharp.identifier("", "!!!"))
    }

    @Test
    fun hostileIdentifiersMatchTheGrammar() {
        hostileNames.forEach {
            val identifier = CSharp.identifier(it, "Fallback")
            assertTrue(identifier, identifierPattern.matches(identifier))
            assertFalse(identifier, identifier.contains("__"))
        }
    }

    @Test
    fun identifierLengthIsCapped() {
        assertTrue(CSharp.identifier("a".repeat(1_000_000), "F").length <= CSharp.MAX_IDENTIFIER)
        assertTrue(CSharp.identifier("1".repeat(1000), "F").length <= CSharp.MAX_IDENTIFIER + 1)
    }

    @Test
    fun uniqueIdentifiers() {
        assertEquals(
            listOf("Sword", "Sword_2", "Sword_3", "A", "Item5"),
            CSharp.uniqueIdentifiers(listOf("Sword", "Sword!", "Sword?", "A", "!!!"), "Item"),
        )
        assertEquals(listOf("Item_2"), CSharp.uniqueIdentifiers(listOf("Item"), "X", setOf("Item")))
        assertEquals(listOf("a", "A"), CSharp.uniqueIdentifiers(listOf("a", "A"), "X"))
        assertEquals(listOf("a_2", "a_2_2"), CSharp.uniqueIdentifiers(listOf("a_2", "a_2"), "X"))
        assertEquals(listOf("a", "a_2", "a_2_2"), CSharp.uniqueIdentifiers(listOf("a", "a", "a_2"), "X"))
    }

    @Test
    fun numberLiteralHandComputed() {
        assertEquals("3", ok(CSharp.numberLiteral(2.5, NumberType.INT)))
        assertEquals("-2", ok(CSharp.numberLiteral(-2.5, NumberType.INT)))
        assertEquals("1000", ok(CSharp.numberLiteral(1000.0, NumberType.LONG)))
        assertEquals("1.5f", ok(CSharp.numberLiteral(1.5, NumberType.FLOAT)))
        assertEquals("1.5", ok(CSharp.numberLiteral(1.5, NumberType.DOUBLE)))
        assertEquals("1.0E300", ok(CSharp.numberLiteral(1e300, NumberType.DOUBLE)))
        assertEquals("1.0E10f", ok(CSharp.numberLiteral(1e10, NumberType.FLOAT)))
    }

    @Test
    fun intBoundaries() {
        assertEquals("2147483647", ok(CSharp.numberLiteral(2147483647.0, NumberType.INT)))
        assertEquals("-2147483648", ok(CSharp.numberLiteral(-2147483648.0, NumberType.INT)))
        assertTrue(CSharp.numberLiteral(2147483648.0, NumberType.INT) is Checked.Bad)
        assertTrue(CSharp.numberLiteral(-2147483649.0, NumberType.INT) is Checked.Bad)
        assertTrue(CSharp.numberLiteral(1e300, NumberType.INT) is Checked.Bad)
    }

    @Test
    fun longBoundaries() {
        assertEquals("-9223372036854775808", ok(CSharp.numberLiteral(-9.223372036854775808E18, NumberType.LONG)))
        assertTrue(CSharp.numberLiteral(9.223372036854775808E18, NumberType.LONG) is Checked.Bad)
        assertTrue(CSharp.numberLiteral(1e19, NumberType.LONG) is Checked.Bad)
        assertTrue(CSharp.numberLiteral(-1e19, NumberType.LONG) is Checked.Bad)
    }

    @Test
    fun floatBoundaries() {
        assertTrue(CSharp.numberLiteral(1e39, NumberType.FLOAT) is Checked.Bad)
        assertTrue(CSharp.numberLiteral(-1e39, NumberType.FLOAT) is Checked.Bad)
    }

    @Test
    fun nonFiniteNumbersAreRefusedInEveryType() {
        NumberType.entries.forEach { type ->
            listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
                assertTrue(CSharp.numberLiteral(it, type) is Checked.Bad)
            }
        }
    }

    @Test
    fun progressionHandComputed() {
        val expected = "public static class XpCurve\n{\n    public static readonly long[] Values =\n    {\n" +
            "        100, 150,\n    };\n}\n"
        assertEquals(expected, ok(CSharp.progression("XpCurve", doubleArrayOf(100.0, 150.0), NumberType.LONG)))
    }

    @Test
    fun progressionWrapsLines() {
        val text = ok(CSharp.progression("X", DoubleArray(9) { 1.0 }, NumberType.INT))
        assertTrue(text, text.contains("1, 1, 1, 1, 1, 1, 1, 1,\n        1,\n"))
    }

    @Test
    fun progressionRefusesOverflowingType() {
        val result = CSharp.progression("X", doubleArrayOf(1.0, 1e10), NumberType.INT)
        assertEquals(Checked.Bad("Level 2 does not fit in a C# int. Choose a larger type."), result)
    }

    @Test
    fun progressionRefusesNanAndEmpty() {
        assertTrue(CSharp.progression("X", doubleArrayOf(Double.NaN), NumberType.DOUBLE) is Checked.Bad)
        assertTrue(CSharp.progression("X", DoubleArray(0), NumberType.DOUBLE) is Checked.Bad)
    }

    @Test
    fun progressionHostileNameIsSanitised() {
        val text = ok(CSharp.progression("X { } class Evil : Base {", doubleArrayOf(1.0), NumberType.INT))
        assertTrue(text, text.startsWith("public static class X_class_Evil_Base\n"))
        assertFalse(text, text.contains("Evil :"))
    }

    @Test
    fun classNameCannotClashWithMembers() {
        assertTrue(ok(CSharp.progression("Values", doubleArrayOf(1.0), NumberType.INT)).startsWith("public static class ValuesData\n"))
        assertTrue(ok(CSharp.loot("Entry", listOf(LootItem("a", 1.0)), null)).startsWith("public static class EntryData\n"))
        assertTrue(ok(CSharp.loot("Item", listOf(LootItem("a", 1.0)), null)).startsWith("public static class ItemData\n"))
    }

    @Test
    fun lootHandComputed() {
        val text = ok(CSharp.loot("Chest", listOf(LootItem("Sword", 3.0), LootItem("Iron Shield", 1.0)), null))
        assertTrue(text, text.startsWith("public static class Chest\n"))
        assertTrue(text, text.contains("        Sword,\n        Iron_Shield,\n"))
        assertTrue(text, text.contains("new Entry(Item.Sword, \"Sword\", 3.0),"))
        assertTrue(text, text.contains("new Entry(Item.Iron_Shield, \"Iron Shield\", 1.0),"))
        assertTrue(text, text.contains("public const double TotalWeight = 4.0;"))
        assertFalse(text, text.contains("Pity"))
    }

    @Test
    fun lootPity() {
        val text = ok(CSharp.loot("Chest", listOf(LootItem("a", 3.0), LootItem("b", 1.0)), Pity(1, 90)))
        assertTrue(text, text.contains("public const int PityAfter = 90;"))
        assertTrue(text, text.contains("public const Item PityItem = Item.b;"))
    }

    @Test
    fun lootHostileNamesCannotBreakOut() {
        hostileNames.forEach { name ->
            val text = ok(CSharp.loot(name, listOf(LootItem(name, 1.0), LootItem(name, 2.0)), null))
            val lines = text.lines().filter { it.trimStart().startsWith("new Entry(") }
            assertEquals(2, lines.size)
            lines.forEach { line ->
                val start = line.indexOf('"')
                val end = line.lastIndexOf('"')
                assertStringLiteralIsClosed(line.substring(start, end + 1))
            }
            text.forEach { assertTrue(name, it == '\n' || it.code in 0x20..0x7E) }
        }
    }

    @Test
    fun lootEnumMemberCannotBeItem() {
        val text = ok(CSharp.loot("X", listOf(LootItem("Item", 1.0)), null))
        assertTrue(text, text.contains("        Item_2,\n"))
    }

    @Test
    fun lootRefusals() {
        assertTrue(CSharp.loot("X", emptyList(), null) is Checked.Bad)
        assertTrue(CSharp.loot("X", listOf(LootItem("a", Double.NaN)), null) is Checked.Bad)
        assertTrue(CSharp.loot("X", listOf(LootItem("a", 1.0)), Pity(3, 1)) is Checked.Bad)
    }

    @Test
    fun bracesAreBalanced() {
        val text = ok(CSharp.loot("X", listOf(LootItem("a", 1.0)), Pity(0, 5)))
        assertEquals(text.count { it == '{' }, text.count { it == '}' })
    }

    @Test
    fun numberTypeFromLabel() {
        NumberType.entries.forEach { assertEquals(it, NumberType.fromLabel(it.label)) }
        assertEquals(NumberType.LONG, NumberType.fromLabel("bogus"))
        assertEquals(NumberType.LONG, NumberType.fromLabel(null))
    }
}

class JsonExportTest {
    private fun ok(result: Checked<String>) = JSONObject((result as Checked.Ok).value)

    @Test
    fun progressionRoundTrip() {
        val json = ok(JsonExport.progression("Xp", "Linear", doubleArrayOf(100.0, 150.5), NumberType.DOUBLE))
        assertEquals("Xp", json.getString("name"))
        assertEquals("Linear", json.getString("curve"))
        assertEquals(1, json.getInt("firstLevel"))
        assertEquals(100.0, json.getJSONArray("values").getDouble(0), 0.0)
        assertEquals(150.5, json.getJSONArray("values").getDouble(1), 0.0)
    }

    @Test
    fun progressionRounding() {
        val json = ok(JsonExport.progression("Xp", "Linear", doubleArrayOf(1.5), NumberType.LONG))
        assertEquals(2L, json.getJSONArray("values").getLong(0))
    }

    @Test
    fun progressionRefusals() {
        assertTrue(JsonExport.progression("X", "L", DoubleArray(0), NumberType.DOUBLE) is Checked.Bad)
        assertTrue(JsonExport.progression("X", "L", doubleArrayOf(Double.NaN), NumberType.DOUBLE) is Checked.Bad)
        assertTrue(JsonExport.progression("X", "L", doubleArrayOf(Double.POSITIVE_INFINITY), NumberType.DOUBLE) is Checked.Bad)
        assertTrue(JsonExport.progression("X", "L", doubleArrayOf(1e10), NumberType.INT) is Checked.Bad)
        assertTrue(JsonExport.progression("X", "L", doubleArrayOf(1e39), NumberType.FLOAT) is Checked.Bad)
    }

    @Test
    fun lootRoundTrip() {
        val json = ok(JsonExport.loot("Chest", listOf(LootItem("a", 3.0), LootItem("b", 1.0)), Pity(1, 90)))
        assertEquals("Chest", json.getString("name"))
        assertEquals(4.0, json.getDouble("totalWeight"), 0.0)
        val items = json.getJSONArray("items")
        assertEquals(2, items.length())
        assertEquals("a", items.getJSONObject(0).getString("name"))
        assertEquals(0.75, items.getJSONObject(0).getDouble("probability"), 1e-12)
        assertEquals(0.25, items.getJSONObject(1).getDouble("probability"), 1e-12)
        assertEquals(1, json.getJSONObject("pity").getInt("itemIndex"))
        assertEquals(90, json.getJSONObject("pity").getInt("after"))
    }

    @Test
    fun lootWithoutPityHasNoPityKey() {
        assertFalse(ok(JsonExport.loot("C", listOf(LootItem("a", 1.0)), null)).has("pity"))
    }

    @Test
    fun hostileNamesRoundTripAsData() {
        val hostile = "\"},{\"name\":\"evil\\</script>"
        val json = ok(JsonExport.loot(hostile, listOf(LootItem(hostile, 1.0)), null))
        assertEquals(hostile, json.getString("name"))
        assertEquals(1, json.getJSONArray("items").length())
        assertEquals(hostile, json.getJSONArray("items").getJSONObject(0).getString("name"))
    }

    @Test
    fun controlAndBidiCharactersAreStripped() {
        val json = ok(JsonExport.loot("a\u202Eb\u0000c\nd", listOf(LootItem("x\u2028y", 1.0)), null))
        assertEquals("abcd", json.getString("name"))
        assertEquals("xy", json.getJSONArray("items").getJSONObject(0).getString("name"))
    }

    @Test
    fun longNameIsCapped() {
        val json = ok(JsonExport.loot("n".repeat(100_000), listOf(LootItem("a", 1.0)), null))
        assertTrue(json.getString("name").length <= 65)
    }

    @Test
    fun lootRefusals() {
        assertTrue(JsonExport.loot("X", emptyList(), null) is Checked.Bad)
        assertTrue(JsonExport.loot("X", listOf(LootItem("a", Double.NaN)), null) is Checked.Bad)
        assertTrue(JsonExport.loot("X", listOf(LootItem("a", 1.0)), Pity(5, 1)) is Checked.Bad)
    }

    @Test
    fun zeroTotalDoesNotDivideByZero() {
        val json = ok(JsonExport.loot("X", listOf(LootItem("a", 0.0)), null))
        assertEquals(0.0, json.getJSONArray("items").getJSONObject(0).getDouble("probability"), 0.0)
    }
}
