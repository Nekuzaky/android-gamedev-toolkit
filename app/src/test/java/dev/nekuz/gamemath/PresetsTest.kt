package dev.nekuz.gamemath

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PresetCodecTest {
    private val sample = Preset("Boss", mapOf("attack" to "100", "defense" to "40"), listOf(listOf("Sword", "3"), listOf("Shield", "1")))

    @Test
    fun roundTrip() {
        assertEquals(listOf(sample), PresetCodec.decode(PresetCodec.encode(listOf(sample))))
    }

    @Test
    fun roundTripOne() {
        assertEquals(sample, PresetCodec.decodeOne(PresetCodec.encodeOne(sample)))
    }

    @Test
    fun emptyListRoundTrip() {
        assertEquals(emptyList<Preset>(), PresetCodec.decode(PresetCodec.encode(emptyList())))
    }

    @Test
    fun corruptTextGivesEmpty() {
        listOf(
            null, "", " ", "{", "[", "null", "[]", "{}", "12", "\"x\"", "{\"presets\":5}", "{\"presets\":{}}",
            "{\"presets\":\"x\"}", "{\"presets\":[", "not json at all", "\u0000\u0001", "{\"presets\":null}",
        ).forEach { assertEquals(it.toString(), emptyList<Preset>(), PresetCodec.decode(it)) }
    }

    @Test
    fun corruptOneGivesNull() {
        listOf(null, "", "{", "[]", "12", "{}", "{\"name\":5}", "{\"name\":\"\"}", "{\"name\":\"‮\"}").forEach {
            assertNull(it.toString(), PresetCodec.decodeOne(it))
        }
    }

    @Test
    fun deepNestingDoesNotCrash() {
        val deep = "[".repeat(500_000)
        assertEquals(emptyList<Preset>(), PresetCodec.decode(deep))
        assertEquals(emptyList<Preset>(), PresetCodec.decode("{\"presets\":$deep"))
        assertNull(PresetCodec.decodeOne("{\"name\":\"a\",\"fields\":" + "{\"a\":".repeat(200_000)))
    }

    @Test
    fun entriesOfTheWrongTypeAreSkipped() {
        val text = """{"presets":[5,"x",null,[],{"name":3},{"name":"ok"},{"name":"mix","fields":{"a":5,"b":"v","c":{},"d":null},"rows":[5,["x",5,null],"y"]}]}"""
        val decoded = PresetCodec.decode(text)
        assertEquals(listOf("ok", "mix"), decoded.map { it.name })
        assertEquals(mapOf("b" to "v"), decoded[1].fields)
        assertEquals(listOf(listOf("x", "", "")), decoded[1].rows)
    }

    @Test
    fun missingFieldsAreEmpty() {
        val decoded = PresetCodec.decode("""{"presets":[{"name":"a"}]}""")
        assertEquals(listOf(Preset("a", emptyMap(), emptyList())), decoded)
    }

    @Test
    fun namesAreCleaned() {
        val preset = Preset("a‮b\u0000c\nd", emptyMap(), emptyList())
        assertEquals("abcd", PresetCodec.decode(PresetCodec.encode(listOf(preset)))[0].name)
        assertEquals("abcd", PresetCodec.cleanName("a‮b\u0000c\nd"))
        assertEquals("", PresetCodec.cleanName("‮"))
        assertEquals("", PresetCodec.cleanName(null))
    }

    @Test
    fun hostileNameStaysData() {
        val hostile = "\"},{\"name\":\"evil\"}]}"
        val decoded = PresetCodec.decode(PresetCodec.encode(listOf(Preset(hostile, mapOf("a" to hostile), emptyList()))))
        assertEquals(1, decoded.size)
        assertEquals(hostile, decoded[0].name)
        assertEquals(hostile, decoded[0].fields["a"])
    }

    @Test
    fun sizesAreBounded() {
        val value = "v".repeat(100_000)
        val json = JSONObject()
        val presets = JSONArray()
        repeat(150) { index ->
            val fields = JSONObject()
            repeat(100) { fields.put("k$it", value.take(100)) }
            fields.put("x".repeat(100), "long key")
            val rows = JSONArray()
            repeat(150) { rows.put(JSONArray(listOf("a", "b", "c", "d", "e", "f"))) }
            presets.put(JSONObject().put("name", "n$index" + "n".repeat(200)).put("fields", fields).put("rows", rows))
        }
        json.put("presets", presets)
        val decoded = PresetCodec.decode(json.toString())
        assertEquals(PresetCodec.MAX_PRESETS, decoded.size)
        decoded.forEach { preset ->
            assertTrue(preset.name.length <= PresetCodec.MAX_NAME + 1)
            assertTrue(preset.fields.size <= PresetCodec.MAX_FIELDS)
            assertTrue(preset.fields.keys.all { it.length <= PresetCodec.MAX_KEY })
            assertTrue(preset.fields.values.all { it.length <= PresetCodec.MAX_VALUE + 1 })
            assertEquals(PresetCodec.MAX_ROWS, preset.rows.size)
            assertTrue(preset.rows.all { it.size == PresetCodec.MAX_COLUMNS })
        }
    }

    @Test
    fun duplicatesAreDropped() {
        val text = """{"presets":[{"name":"a","fields":{"x":"1"}},{"name":"a","fields":{"x":"2"}}]}"""
        val decoded = PresetCodec.decode(text)
        assertEquals(1, decoded.size)
        assertEquals("1", decoded[0].fields["x"])
    }

    @Test
    fun encodeCapsItsInput() {
        val many = List(200) { Preset("n$it", emptyMap(), emptyList()) }
        assertEquals(PresetCodec.MAX_PRESETS, PresetCodec.decode(PresetCodec.encode(many)).size)
    }

    @Test
    fun upsertAddsAndReplaces() {
        val first = PresetCodec.upsert(emptyList(), sample)!!
        assertEquals(listOf(sample), first)
        val changed = sample.copy(fields = mapOf("attack" to "1"))
        assertEquals(listOf(changed), PresetCodec.upsert(first, changed))
        assertEquals(2, PresetCodec.upsert(first, sample.copy(name = "Other"))!!.size)
    }

    @Test
    fun upsertRefusesWhenFullButStillReplaces() {
        val full = List(PresetCodec.MAX_PRESETS) { Preset("n$it", emptyMap(), emptyList()) }
        assertNull(PresetCodec.upsert(full, Preset("new", emptyMap(), emptyList())))
        val replaced = PresetCodec.upsert(full, Preset("n5", mapOf("a" to "b"), emptyList()))!!
        assertEquals(PresetCodec.MAX_PRESETS, replaced.size)
        assertEquals("b", replaced[5].fields["a"])
    }

    @Test
    fun remove() {
        val list = listOf(sample, sample.copy(name = "Other"))
        assertEquals(listOf("Other"), PresetCodec.remove(list, "Boss").map { it.name })
        assertEquals(2, PresetCodec.remove(list, "Nope").size)
    }
}

class PresetFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun target() = File(folder.root, "sub/presets.json")

    private val sample = Preset("Boss", mapOf("a" to "1"), listOf(listOf("x", "1")))

    @Test
    fun writeThenRead() {
        val file = target()
        assertTrue(PresetFile.write(file, "héllo 😀"))
        assertEquals("héllo 😀", PresetFile.read(file))
    }

    @Test
    fun noTempFileIsLeftBehind() {
        val file = target()
        assertTrue(PresetFile.write(file, "x"))
        assertFalse(File(file.parentFile, file.name + ".tmp").exists())
        assertEquals(listOf(file.name), file.parentFile!!.list()!!.toList())
    }

    @Test
    fun overwriteReplacesContent() {
        val file = target()
        PresetFile.write(file, "first, a long value")
        PresetFile.write(file, "second")
        assertEquals("second", PresetFile.read(file))
    }

    @Test
    fun staleTempFileDoesNotBreakWriting() {
        val file = target()
        file.parentFile!!.mkdirs()
        File(file.parentFile, file.name + ".tmp").writeText("stale partial")
        assertTrue(PresetFile.write(file, "fresh"))
        assertEquals("fresh", PresetFile.read(file))
    }

    @Test
    fun missingFileReadsAsNull() {
        assertNull(PresetFile.read(target()))
        assertEquals(emptyList<Preset>(), PresetFile.load(target()))
    }

    @Test
    fun directoryReadsAsNull() {
        assertNull(PresetFile.read(folder.root))
    }

    @Test
    fun oversizedFileIsRefused() {
        val file = target()
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(PresetFile.MAX_BYTES + 1) { 'a'.code.toByte() })
        assertNull(PresetFile.read(file))
        assertEquals(emptyList<Preset>(), PresetFile.load(file))
    }

    @Test
    fun largestAllowedFileReads() {
        val file = target()
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(PresetFile.MAX_BYTES) { 'a'.code.toByte() })
        assertNotNull(PresetFile.read(file))
    }

    @Test
    fun oversizedWriteIsRefusedAndKeepsTheOldFile() {
        val file = target()
        assertTrue(PresetFile.write(file, "keep"))
        assertFalse(PresetFile.write(file, "x".repeat(PresetFile.MAX_BYTES + 1)))
        assertEquals("keep", PresetFile.read(file))
    }

    @Test
    fun corruptFileLoadsEmptyAndCanBeReplaced() {
        val file = target()
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0, -1, -2, 123, 34, 0x7F))
        assertEquals(emptyList<Preset>(), PresetFile.load(file))
        assertTrue(PresetFile.save(file, listOf(sample)))
        assertEquals(listOf(sample), PresetFile.load(file))
    }

    @Test
    fun truncatedFileLoadsEmpty() {
        val file = target()
        assertTrue(PresetFile.save(file, listOf(sample)))
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))
        assertEquals(emptyList<Preset>(), PresetFile.load(file))
    }

    @Test
    fun blockedDirectoryFailsWithoutThrowing() {
        val blocker = File(folder.root, "blocker")
        blocker.writeText("a file where a directory is needed")
        assertFalse(PresetFile.write(File(blocker, "presets.json"), "x"))
    }

    @Test
    fun saveThenLoad() {
        val file = target()
        assertTrue(PresetFile.save(file, listOf(sample, sample.copy(name = "Two"))))
        assertEquals(listOf("Boss", "Two"), PresetFile.load(file).map { it.name })
    }
}
