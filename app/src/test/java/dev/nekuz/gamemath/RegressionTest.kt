package dev.nekuz.gamemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeviceFindingsTest {
    @Test
    fun nearlyCertainIsNotShownAsCertain() {
        assertEquals(Fmt.ALMOST_CERTAIN, Fmt.percent(Loot.atLeastOne(0.7, 20)))
        assertEquals(Fmt.ALMOST_CERTAIN, Fmt.percent(Math.nextDown(1.0)))
        assertEquals("100%", Fmt.percent(1.0))
        assertEquals("99.6829%", Fmt.percent(Loot.atLeastOne(0.25, 20)))
    }

    @Test
    fun tinyChancesStayVisible() {
        assertEquals("0%", Fmt.percent(0.0))
        assertEquals("1.000e-05%", Fmt.percent(1e-7))
    }

    @Test
    fun malformedCommaTextIsNotCalledAmbiguous() {
        assertEquals(Checked.Bad(Inputs.NOT_A_NUMBER), Inputs.double(",.", 0.0, 1.0))
        assertEquals(Checked.Bad(Inputs.NOT_A_NUMBER), Inputs.double(",,", 0.0, 1.0))
        assertEquals(Checked.Bad(Inputs.NOT_A_NUMBER), Inputs.double("a,b", 0.0, 1.0))
        assertEquals(Checked.Bad(Inputs.AMBIGUOUS_COMMA), Inputs.double("1,000", 0.0, 1e9))
        assertEquals(Checked.Bad(Inputs.AMBIGUOUS_COMMA), Inputs.double("1,000.5", 0.0, 1e9))
    }
}

class TileGridExactTest {
    private fun grid(width: Int, height: Int, tile: Int, zoom: Double) =
        (ScreenMath.tileGrid(width, height, tile, zoom) as Checked.Ok).value

    @Test
    fun wholeTilesAcrossAFloatBoundary() {
        val result = grid(1920, 1080, 48, 1.6)
        assertEquals(25L, result.fullColumns)
        assertEquals(14L, result.fullRows)
        assertEquals(350L, result.fullTiles)
    }

    @Test
    fun tilesToDrawAcrossAFloatBoundary() {
        val result = grid(1920, 1080, 24, 0.6)
        assertEquals(134L, result.visibleColumns)
        assertEquals(75L, result.visibleRows)
        assertEquals(10050L, result.visibleTiles)
    }

    @Test
    fun exactDivisionHasEqualFullAndVisibleCounts() {
        val result = grid(1920, 1080, 24, 2.5)
        assertEquals(32L, result.fullColumns)
        assertEquals(32L, result.visibleColumns)
        assertEquals(18L, result.fullRows)
        assertEquals(18L, result.visibleRows)
    }

    @Test
    fun aRealRemainderStillRoundsBothWays() {
        val result = grid(100, 100, 3, 1.0)
        assertEquals(33L, result.fullColumns)
        assertEquals(34L, result.visibleColumns)
    }

    @Test
    fun smallestRealRemainderIsNotSnappedAway() {
        val result = grid(1_000_000, 1_000_000, 3, 1.0)
        assertEquals(333_333L, result.fullColumns)
        assertEquals(333_334L, result.visibleColumns)
        val close = grid(999_999, 1, 1, 1.00001)
        assertEquals(999_989L, close.fullColumns)
        assertEquals(999_990L, close.visibleColumns)
    }

    @Test
    fun sweepOfCommonSizesAndZoomStepsMatchesExactArithmetic() {
        val screens = listOf(
            320 to 180, 640 to 360, 1280 to 720, 1366 to 768, 1600 to 900, 1920 to 1080,
            2160 to 1080, 2400 to 1080, 2560 to 1440, 2960 to 1440, 3840 to 2160, 1080 to 2400,
        )
        val tiles = listOf(8, 12, 16, 24, 32, 48, 64, 100, 128, 256)
        for ((width, height) in screens) {
            for (tile in tiles) {
                for (tenths in 1..100) {
                    val zoom = tenths / 10.0
                    val result = grid(width, height, tile, zoom)
                    val divisor = tile.toLong() * tenths
                    val label = "$width x $height tile $tile zoom $zoom"
                    assertEquals(label, width * 10L / divisor, result.fullColumns)
                    assertEquals(label, height * 10L / divisor, result.fullRows)
                    assertEquals(label, (width * 10L + divisor - 1) / divisor, result.visibleColumns)
                    assertEquals(label, (height * 10L + divisor - 1) / divisor, result.visibleRows)
                }
            }
        }
    }

    @Test
    fun parsedZoomIsTheDecimalTheUserTyped() {
        val zoom = Inputs.double("1.6", ScreenMath.MIN_ZOOM, ScreenMath.MAX_ZOOM).valueOrNull()!!
        assertEquals(25L, grid(1920, 1080, 48, zoom).fullColumns)
    }
}

class FrameBudgetToleranceTest {
    private fun ok(fps: Double, vararg costs: Double) =
        (FrameBudget.compute(fps, costs.map { CostEntry("x", it) }) as Checked.Ok).value

    @Test
    fun floatNoiseIsNotOverBudget() {
        assertTrue(0.3 + 8.3 + 1.4 > 10.0)
        val result = ok(100.0, 0.3, 8.3, 1.4)
        assertEquals(0.0, result.remainingMs, 0.0)
        assertFalse(result.overBudget)
        assertEquals("0", Fmt.number(result.remainingMs))
    }

    @Test
    fun noiseBelowTheBudgetSnapsToZeroToo() {
        val budget = 1000.0 / 60.0
        val result = ok(60.0, budget - 1e-12)
        assertEquals(0.0, result.remainingMs, 0.0)
        assertFalse(result.overBudget)
    }

    @Test
    fun aRealOverrunIsStillOverBudget() {
        val result = ok(100.0, 10.0000001)
        assertTrue(result.overBudget)
        assertTrue(result.remainingMs < 0.0)
    }

    @Test
    fun aRealRemainderIsKept() {
        val result = ok(100.0, 9.9999999)
        assertFalse(result.overBudget)
        assertEquals(1e-7, result.remainingMs, 1e-12)
    }

    @Test
    fun remainderNeverShowsAsNegativeZero() {
        assertEquals("0.0", ok(100.0, 10.0).remainingMs.toString())
    }
}

class FloatUnderflowTest {
    private val type = NumberType.FLOAT

    @Test
    fun nonZeroThatBecomesZeroIsRefused() {
        assertEquals(Checked.Bad("does not fit in a C# float"), CSharp.numberLiteral(1e-46, type))
        assertEquals(Checked.Bad("does not fit in a C# float"), CSharp.numberLiteral(-1e-46, type))
        assertTrue(CSharp.numberLiteral(Double.MIN_VALUE, type) is Checked.Bad)
    }

    @Test
    fun realZeroAndSmallestFloatAreFine() {
        assertEquals(Checked.Ok("0.0f"), CSharp.numberLiteral(0.0, type))
        assertEquals(Checked.Ok("1.4E-45f"), CSharp.numberLiteral(1e-45, type))
    }

    @Test
    fun exponentialDecayNamesTheFirstLevelThatDoesNotFit() {
        val values = (Progression.generate(Curve.Exponential(1.0, 0.1), 50) as Checked.Ok).value.values
        val expected = Checked.Bad("Level 47 does not fit in a C# float. Choose a larger type.")
        assertEquals(expected, CSharp.progression("X", values, type))
        assertEquals(expected, JsonExport.progression("X", "Exponential", values, type))
        assertTrue(CSharp.progression("X", values, NumberType.DOUBLE) is Checked.Ok)
    }
}

class PresetSafetyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val sample = Preset("Boss", mapOf("a" to "1"), emptyList())
    private val damaged = "{\"presets\":[{\"name\":\"precious\""

    private fun target() = File(folder.root, "presets.json")

    private fun aside(file: File) = File(file.parentFile, file.name + PresetFile.ASIDE_SUFFIX)

    @Test
    fun missingFileIsAnEmptyList() {
        assertEquals(PresetLoad.Loaded(emptyList()), PresetFile.inspect(target()))
    }

    @Test
    fun validFileLoads() {
        val file = target()
        assertTrue(PresetFile.save(file, listOf(sample)))
        assertEquals(PresetLoad.Loaded(listOf(sample)), PresetFile.inspect(file))
    }

    @Test
    fun validEmptyListIsNotDamage() {
        val file = target()
        assertTrue(PresetFile.save(file, emptyList()))
        assertEquals(PresetLoad.Loaded(emptyList()), PresetFile.inspect(file))
    }

    @Test
    fun brokenContentIsDamaged() {
        val file = target()
        listOf("", " ", "{", "[]", "12", "null", "{}", "{\"presets\":5}", damaged, "[".repeat(500_000)).forEach {
            file.writeText(it)
            assertEquals(it.take(20), PresetLoad.Damaged, PresetFile.inspect(file))
        }
    }

    @Test
    fun oversizedFileIsDamaged() {
        val file = target()
        file.writeBytes(ByteArray(PresetFile.MAX_BYTES + 1) { 'a'.code.toByte() })
        assertEquals(PresetLoad.Damaged, PresetFile.inspect(file))
    }

    @Test
    fun unreadablePathIsNotDamage() {
        val file = target()
        assertTrue(file.mkdirs())
        assertEquals(PresetLoad.Unreadable, PresetFile.inspect(file))
    }

    @Test
    fun saveAfterAFailedReadDoesNotTouchThePath() {
        val file = target()
        assertTrue(file.mkdirs())
        val kept = File(file, "inside.txt")
        kept.writeText("keep")
        assertEquals(SaveOutcome.FAILED, PresetFile.saveMerged(file, sample))
        assertEquals("keep", kept.readText())
        assertFalse(aside(file).exists())
    }

    @Test
    fun deleteAfterAFailedReadDoesNotTouchThePath() {
        val file = target()
        assertTrue(file.mkdirs())
        assertFalse(PresetFile.removeFrom(file, "Boss"))
        assertTrue(file.isDirectory)
    }

    @Test
    fun saveOverDamagedFileKeepsItsBytesAside() {
        val file = target()
        file.writeText(damaged)
        assertEquals(SaveOutcome.SAVED, PresetFile.saveMerged(file, sample))
        assertEquals(damaged, aside(file).readText())
        assertEquals(PresetLoad.Loaded(listOf(sample)), PresetFile.inspect(file))
    }

    @Test
    fun oversizedFileIsKeptAsideWhole() {
        val file = target()
        file.writeBytes(ByteArray(PresetFile.MAX_BYTES + 5) { 'a'.code.toByte() })
        assertEquals(SaveOutcome.SAVED, PresetFile.saveMerged(file, sample))
        assertEquals((PresetFile.MAX_BYTES + 5).toLong(), aside(file).length())
    }

    @Test
    fun deleteOnDamagedFileChangesNothing() {
        val file = target()
        file.writeText(damaged)
        assertFalse(PresetFile.removeFrom(file, "precious"))
        assertEquals(damaged, file.readText())
        assertFalse(aside(file).exists())
    }

    @Test
    fun saveMergesWithWhatIsThere() {
        val file = target()
        assertEquals(SaveOutcome.SAVED, PresetFile.saveMerged(file, sample))
        assertEquals(SaveOutcome.SAVED, PresetFile.saveMerged(file, sample.copy(name = "Two")))
        assertEquals(listOf("Boss", "Two"), PresetFile.load(file).map { it.name })
        assertFalse(aside(file).exists())
    }

    @Test
    fun saveWhenFullKeepsTheFile() {
        val file = target()
        val full = List(PresetCodec.MAX_PRESETS) { Preset("n$it", emptyMap(), emptyList()) }
        assertTrue(PresetFile.save(file, full))
        assertEquals(SaveOutcome.FULL, PresetFile.saveMerged(file, sample))
        assertEquals(PresetCodec.MAX_PRESETS, PresetFile.load(file).size)
    }

    @Test
    fun removeDeletesByName() {
        val file = target()
        PresetFile.saveMerged(file, sample)
        assertTrue(PresetFile.removeFrom(file, "Boss"))
        assertEquals(PresetLoad.Loaded(emptyList()), PresetFile.inspect(file))
    }

    @Test
    fun decodeOrNullSeparatesDamageFromEmpty() {
        assertEquals(emptyList<Preset>(), PresetCodec.decodeOrNull("{\"presets\":[]}"))
        assertEquals(null, PresetCodec.decodeOrNull("{\"presets\":5}"))
        assertEquals(null, PresetCodec.decodeOrNull(""))
        assertEquals(null, PresetCodec.decodeOrNull(null))
    }
}
