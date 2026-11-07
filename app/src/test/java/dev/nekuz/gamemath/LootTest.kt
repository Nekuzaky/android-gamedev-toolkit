package dev.nekuz.gamemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LootTest {
    private val delta = 1e-9

    private fun item(weight: Double, name: String = "x") = LootItem(name, weight)

    private fun odds(items: List<LootItem>, tries: Long = 10, pity: Pity? = null) =
        (Loot.analyze(LootRequest(items, tries, pity)) as Checked.Ok).value

    private fun bad(items: List<LootItem>, tries: Long = 10, pity: Pity? = null) =
        assertTrue(Loot.analyze(LootRequest(items, tries, pity)) is Checked.Bad)

    @Test
    fun probabilitiesHandComputed() {
        val result = odds(listOf(item(70.0), item(25.0), item(5.0)))
        assertEquals(100.0, result.totalWeight, delta)
        assertEquals(0.70, result.items[0].probability, delta)
        assertEquals(0.25, result.items[1].probability, delta)
        assertEquals(0.05, result.items[2].probability, delta)
    }

    @Test
    fun expectedTriesHandComputed() {
        val result = odds(listOf(item(3.0), item(1.0)))
        assertEquals(4.0 / 3.0, result.items[0].triesPerDrop!!, delta)
        assertEquals(4.0, result.items[1].triesPerDrop!!, delta)
    }

    @Test
    fun chanceInNHandComputed() {
        val result = odds(listOf(item(1.0), item(1.0)), tries = 3)
        assertEquals(0.875, result.items[0].chance!!, delta)
    }

    @Test
    fun oneInHundredOverHundredTries() {
        assertEquals(0.6339676587, Loot.atLeastOne(0.01, 100), 1e-9)
    }

    @Test
    fun zeroWeightItemNeverDrops() {
        val result = odds(listOf(item(0.0), item(1.0)))
        assertEquals(0.0, result.items[0].probability, 0.0)
        assertNull(result.items[0].triesPerDrop)
        assertEquals(0.0, result.items[0].chance!!, 0.0)
        assertEquals(1.0, result.items[1].probability, 0.0)
        assertEquals(1.0, result.items[1].triesPerDrop!!, 0.0)
        assertEquals(1.0, result.items[1].chance!!, 0.0)
    }

    @Test
    fun allZeroWeightsAreRefused() {
        bad(listOf(item(0.0), item(0.0)))
    }

    @Test
    fun emptyListIsRefused() {
        bad(emptyList())
    }

    @Test
    fun tooManyItemsAreRefused() {
        bad(List(101) { item(1.0) })
        assertEquals(100, odds(List(100) { item(1.0) }).items.size)
    }

    @Test
    fun invalidWeightsAreRefused() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e13, 1e-320, Double.MIN_VALUE, -0.5).forEach {
            bad(listOf(item(1.0), item(it)))
        }
    }

    @Test
    fun weightBoundaries() {
        assertTrue(Loot.validWeight(0.0))
        assertTrue(Loot.validWeight(Loot.MIN_WEIGHT))
        assertTrue(Loot.validWeight(Loot.MAX_WEIGHT))
        assertFalse(Loot.validWeight(Loot.MIN_WEIGHT / 2))
        assertFalse(Loot.validWeight(Loot.MAX_WEIGHT * 2))
    }

    @Test
    fun extremeWeightRatioStaysFinite() {
        val result = odds(List(99) { item(Loot.MAX_WEIGHT) } + item(Loot.MIN_WEIGHT), tries = Loot.MAX_TRIES)
        result.items.forEach {
            assertTrue(it.probability.isFinite())
            assertTrue(it.triesPerDrop!!.isFinite())
            assertTrue(it.chance!!.isFinite())
        }
        assertTrue(result.items.last().probability > 0.0)
    }

    @Test
    fun triesOutOfRangeAreRefused() {
        bad(listOf(item(1.0)), tries = -1)
        bad(listOf(item(1.0)), tries = Loot.MAX_TRIES + 1)
        odds(listOf(item(1.0)), tries = Loot.MAX_TRIES)
    }

    @Test
    fun zeroTriesMeansZeroChance() {
        assertEquals(0.0, odds(listOf(item(1.0)), tries = 0).items[0].chance!!, 0.0)
    }

    @Test
    fun atLeastOneEdgeCases() {
        assertEquals(0.0, Loot.atLeastOne(0.5, 0), 0.0)
        assertEquals(0.0, Loot.atLeastOne(0.5, -3), 0.0)
        assertEquals(0.0, Loot.atLeastOne(0.0, 100), 0.0)
        assertEquals(0.0, Loot.atLeastOne(-1.0, 100), 0.0)
        assertEquals(1.0, Loot.atLeastOne(1.0, 1), 0.0)
        assertEquals(1.0, Loot.atLeastOne(5.0, 1), 0.0)
        assertEquals(0.0, Loot.atLeastOne(Double.NaN, 5), 0.0)
        assertEquals(1.0, Loot.atLeastOne(Double.POSITIVE_INFINITY, 5), 0.0)
        assertEquals(1.0, Loot.atLeastOne(0.5, Long.MAX_VALUE), 0.0)
    }

    @Test
    fun atLeastOneIsAccurateForTinyProbabilities() {
        assertEquals(1e-12, Loot.atLeastOne(1e-12, 1), 1e-24)
    }

    @Test
    fun expectedTriesWithPityHandComputed() {
        assertEquals(1.5, Loot.expectedTriesWithPity(0.5, 2), delta)
        assertEquals(1.75, Loot.expectedTriesWithPity(0.5, 3), delta)
        assertEquals(1.0, Loot.expectedTriesWithPity(0.5, 1), delta)
        assertEquals(1.0, Loot.expectedTriesWithPity(1.0, 50), delta)
        assertEquals(50.0, Loot.expectedTriesWithPity(0.0, 50), delta)
        assertEquals(50.0, Loot.expectedTriesWithPity(Double.NaN, 50), delta)
    }

    @Test
    fun pityTargetIsGuaranteedByK() {
        val result = odds(listOf(item(1.0), item(9.0)), tries = 5, pity = Pity(0, 5))
        assertEquals(1.0, result.items[0].chance!!, 0.0)
        val short = odds(listOf(item(1.0), item(9.0)), tries = 4, pity = Pity(0, 5))
        assertEquals(1.0 - 0.9 * 0.9 * 0.9 * 0.9, short.items[0].chance!!, delta)
    }

    @Test
    fun pityTargetExpectedTriesAndRate() {
        val result = odds(listOf(item(1.0), item(1.0)), tries = 1, pity = Pity(0, 3))
        assertEquals(1.75, result.items[0].triesPerDrop!!, delta)
        assertEquals(1.0 / 1.75, result.items[0].rate, delta)
    }

    @Test
    fun pityRatesSumToOne() {
        val result = odds(listOf(item(1.0), item(4.0), item(15.0)), tries = 10, pity = Pity(0, 7))
        assertEquals(1.0, result.items.sumOf { it.rate }, 1e-9)
    }

    @Test
    fun pityOnZeroWeightItem() {
        val result = odds(listOf(item(0.0), item(1.0)), tries = 4, pity = Pity(0, 4))
        assertEquals(0.25, result.items[0].rate, delta)
        assertEquals(4.0, result.items[0].triesPerDrop!!, delta)
        assertEquals(1.0, result.items[0].chance!!, 0.0)
        assertEquals(0.75, result.items[1].rate, delta)
    }

    @Test
    fun pityOfOneAlwaysGivesTheTarget() {
        val result = odds(listOf(item(1.0), item(1.0)), tries = 9, pity = Pity(0, 1))
        assertEquals(1.0, result.items[0].rate, delta)
        assertEquals(0.0, result.items[1].rate, delta)
        assertNull(result.items[1].triesPerDrop)
        assertEquals(0.0, result.items[1].chance!!, 0.0)
    }

    @Test
    fun otherItemWithPityHandComputed() {
        assertEquals(0.3125, Loot.chanceWithPity(0.25, 0.25, 2, 2)!!, delta)
    }

    @Test
    fun otherItemWithPityIsPlainWhenPityCannotTrigger() {
        assertEquals(Loot.atLeastOne(0.25, 5), Loot.chanceWithPity(0.25, 0.25, 5, 6)!!, delta)
    }

    @Test
    fun otherItemWithPityTooBigGivesNull() {
        assertNull(Loot.chanceWithPity(0.25, 0.25, 1_000_000_000L, 1_000_000))
    }

    @Test
    fun otherItemWithPityEdgeCases() {
        assertEquals(0.0, Loot.chanceWithPity(0.25, 0.25, 0, 5)!!, 0.0)
        assertEquals(0.0, Loot.chanceWithPity(0.0, 0.25, 5, 5)!!, 0.0)
        assertEquals(0.0, Loot.chanceWithPity(Double.NaN, 0.25, 5, 5)!!, 0.0)
        assertEquals(0.0, Loot.chanceWithPity(0.25, 0.25, 5, 1)!!, 0.0)
    }

    @Test
    fun analyzeReportsNullWhenWorkIsTooBig() {
        val result = odds(listOf(item(1.0), item(1.0), item(1.0)), tries = Loot.MAX_TRIES, pity = Pity(0, Loot.MAX_PITY))
        assertNull(result.items[1].chance)
        assertEquals(1.0, result.items[0].chance!!, 0.0)
    }

    @Test
    fun invalidPityIsRefused() {
        bad(listOf(item(1.0)), pity = Pity(1, 5))
        bad(listOf(item(1.0)), pity = Pity(-1, 5))
        bad(listOf(item(1.0)), pity = Pity(0, 0))
        bad(listOf(item(1.0)), pity = Pity(0, -1))
        bad(listOf(item(1.0)), pity = Pity(0, Loot.MAX_PITY + 1))
    }

    @Test
    fun cleanNameStripsHostileText() {
        assertEquals("Item 3", Loot.cleanName("‮​", 2))
        assertEquals("Item 1", Loot.cleanName(null, 0))
        assertEquals("abc", Loot.cleanName("a‮b\u0000c", 0))
        assertTrue(Loot.cleanName("x".repeat(10_000), 0).length <= Loot.NAME_LENGTH + 1)
    }
}

class MonteCarloTest {
    private fun plan(items: List<LootItem>, tries: Long = 10, sessions: Long = 1000, seed: Long = 42, pity: Pity? = null) =
        (MonteCarlo.plan(LootRequest(items, tries, pity), sessions, seed) as Checked.Ok).value

    private val sample = listOf(LootItem("a", 70.0), LootItem("b", 25.0), LootItem("c", 5.0))

    @Test
    fun splitMixKnownVector() {
        val random = SplitMix64(1234567)
        assertEquals("6457827717110365317", java.lang.Long.toUnsignedString(random.nextLong()))
        assertEquals("3203168211198807973", java.lang.Long.toUnsignedString(random.nextLong()))
        assertEquals("9817491932198370423", java.lang.Long.toUnsignedString(random.nextLong()))
        assertEquals("4593380528125082431", java.lang.Long.toUnsignedString(random.nextLong()))
        assertEquals("16408922859458223821", java.lang.Long.toUnsignedString(random.nextLong()))
    }

    @Test
    fun nextDoubleStaysInUnitInterval() {
        val random = SplitMix64(-1)
        repeat(100_000) {
            val value = random.nextDouble()
            assertTrue(value >= 0.0 && value < 1.0)
        }
    }

    @Test
    fun sameSeedGivesSameResult() {
        val first = MonteCarlo.run(plan(sample))
        val second = MonteCarlo.run(plan(sample))
        assertTrue(first.drops.contentEquals(second.drops))
        assertTrue(first.sessionsWithDrop.contentEquals(second.sessionsWithDrop))
    }

    @Test
    fun differentSeedGivesDifferentResult() {
        val first = MonteCarlo.run(plan(sample, seed = 1))
        val second = MonteCarlo.run(plan(sample, seed = 2))
        assertFalse(first.drops.contentEquals(second.drops))
    }

    @Test
    fun extremeSeedsWork() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L, -1L).forEach {
            assertEquals(10_000L, MonteCarlo.run(plan(sample, seed = it)).rolls)
        }
    }

    @Test
    fun allRollsAreCounted() {
        val result = MonteCarlo.run(plan(sample, tries = 10, sessions = 1000))
        assertEquals(10_000L, result.rolls)
        assertEquals(1000L, result.sessions)
        assertEquals(10_000L, result.drops.sum())
        assertFalse(result.cancelled)
    }

    @Test
    fun convergesToExactProbabilities() {
        val result = MonteCarlo.run(plan(sample, tries = 1, sessions = 1_000_000))
        assertEquals(0.70, result.rate(0), 0.005)
        assertEquals(0.25, result.rate(1), 0.005)
        assertEquals(0.05, result.rate(2), 0.005)
    }

    @Test
    fun convergesToExactChanceInTries() {
        val result = MonteCarlo.run(plan(sample, tries = 20, sessions = 200_000))
        assertEquals(Loot.atLeastOne(0.05, 20), result.chance(2), 0.01)
        assertEquals(Loot.atLeastOne(0.25, 20), result.chance(1), 0.01)
    }

    @Test
    fun convergesToExactWithPity() {
        val request = LootRequest(sample, 12, Pity(2, 8))
        val exact = (Loot.analyze(request) as Checked.Ok).value
        val result = MonteCarlo.run((MonteCarlo.plan(request, 200_000, 7) as Checked.Ok).value)
        sample.indices.forEach { index ->
            assertEquals(exact.items[index].chance!!, result.chance(index), 0.01)
        }
    }

    @Test
    fun longRunRateWithPityMatches() {
        val request = LootRequest(sample, 10_000, Pity(2, 8))
        val exact = (Loot.analyze(request) as Checked.Ok).value
        val result = MonteCarlo.run((MonteCarlo.plan(request, 500, 9) as Checked.Ok).value)
        sample.indices.forEach { index ->
            assertEquals(exact.items[index].rate, result.rate(index), 0.01)
        }
    }

    @Test
    fun zeroWeightItemNeverDrops() {
        val result = MonteCarlo.run(plan(listOf(LootItem("z", 0.0), LootItem("a", 1.0), LootItem("y", 0.0)), sessions = 10_000))
        assertEquals(0L, result.drops[0])
        assertEquals(0L, result.drops[2])
        assertEquals(100_000L, result.drops[1])
    }

    @Test
    fun pityGuaranteesTheTargetEverySession() {
        val items = listOf(LootItem("rare", 1e-9), LootItem("common", 1e12))
        val result = MonteCarlo.run(plan(items, tries = 5, sessions = 1000, pity = Pity(0, 5)))
        assertEquals(1000L, result.sessionsWithDrop[0])
    }

    @Test
    fun pityOfOneForcesTargetOnly() {
        val result = MonteCarlo.run(plan(sample, tries = 3, sessions = 10, pity = Pity(1, 1)))
        assertEquals(30L, result.drops[1])
        assertEquals(0L, result.drops[0])
    }

    @Test
    fun cancelledImmediately() {
        val result = MonteCarlo.run(plan(sample), isCancelled = { true })
        assertTrue(result.cancelled)
        assertEquals(0L, result.rolls)
        assertEquals(0L, result.sessions)
        assertEquals(0.0, result.rate(0), 0.0)
        assertEquals(0.0, result.chance(0), 0.0)
    }

    @Test
    fun cancelledMidwayKeepsConsistentCounts() {
        var calls = 0
        val result = MonteCarlo.run(plan(sample, tries = 1000, sessions = 1000), isCancelled = { ++calls > 3 })
        assertTrue(result.cancelled)
        assertTrue(result.rolls in 1 until 1_000_000)
        assertEquals(result.rolls, result.drops.sum())
        sample.indices.forEach {
            assertTrue(result.sessionsWithDrop[it] <= result.sessions)
            assertTrue(result.chance(it) in 0.0..1.0)
        }
    }

    @Test
    fun progressIsBoundedAndEndsAtOne() {
        val seen = ArrayList<Double>()
        MonteCarlo.run(plan(sample, tries = 100, sessions = 1000), onProgress = { seen.add(it) })
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it in 0.0..1.0 })
        assertEquals(1.0, seen.last(), 0.0)
    }

    @Test
    fun sessionsAreCappedToTheRollBudget() {
        val capped = plan(sample, tries = 1_000_000, sessions = 1_000_000)
        assertEquals(50L, capped.sessions)
        assertTrue(capped.capped)
        assertEquals(MonteCarlo.MAX_SESSIONS, plan(sample, tries = 1, sessions = Long.MAX_VALUE).sessions)
        assertFalse(plan(sample, tries = 10, sessions = 100).capped)
    }

    @Test
    fun planRefusals() {
        assertTrue(MonteCarlo.plan(LootRequest(sample, 0, null), 10, 1) is Checked.Bad)
        assertTrue(MonteCarlo.plan(LootRequest(sample, 1, null), 0, 1) is Checked.Bad)
        assertTrue(MonteCarlo.plan(LootRequest(sample, 1, null), -5, 1) is Checked.Bad)
        assertTrue(MonteCarlo.plan(LootRequest(sample, MonteCarlo.MAX_ROLLS + 1, null), 1, 1) is Checked.Bad)
        assertTrue(MonteCarlo.plan(LootRequest(emptyList(), 1, null), 1, 1) is Checked.Bad)
        assertTrue(MonteCarlo.plan(LootRequest(listOf(LootItem("x", Double.NaN)), 1, null), 1, 1) is Checked.Bad)
    }

    @Test
    fun plansCompareByValue() {
        assertEquals(plan(sample), plan(sample))
        assertNotEquals(plan(sample), plan(sample, seed = 43))
        assertNotEquals(plan(sample), plan(sample + LootItem("d", 1.0)))
    }

    @Test
    fun pickHandComputed() {
        val cumulative = doubleArrayOf(0.0, 1.0, 1.0, 3.0)
        assertEquals(1, MonteCarlo.pick(cumulative, 0.0, 3))
        assertEquals(1, MonteCarlo.pick(cumulative, 0.999, 3))
        assertEquals(3, MonteCarlo.pick(cumulative, 1.0, 3))
        assertEquals(3, MonteCarlo.pick(cumulative, 2.9, 3))
        assertEquals(3, MonteCarlo.pick(cumulative, 3.0, 3))
    }
}
