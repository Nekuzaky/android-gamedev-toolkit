package dev.nekuz.gamemath

import kotlin.math.expm1
import kotlin.math.ln1p

data class LootItem(val name: String, val weight: Double)

data class Pity(val itemIndex: Int, val after: Int)

data class LootRequest(val items: List<LootItem>, val tries: Long, val pity: Pity?)

data class ItemOdds(
    val name: String,
    val weight: Double,
    val probability: Double,
    val rate: Double,
    val triesPerDrop: Double?,
    val chance: Double?,
)

data class LootOdds(val totalWeight: Double, val items: List<ItemOdds>)

object Loot {
    const val MAX_ITEMS = 100
    const val MAX_WEIGHT = 1e12
    const val MIN_WEIGHT = 1e-9
    const val MAX_TRIES = 1_000_000_000L
    const val MAX_PITY = 1_000_000
    const val NAME_LENGTH = 64
    const val WORK_BUDGET = 5_000_000L
    const val WEIGHT_RANGE = "A weight must be 0, or between 0.000000001 and 1000000000000"

    fun cleanName(raw: String?, index: Int): String =
        ForeignText.clean(raw, NAME_LENGTH).ifEmpty { "Item ${index + 1}" }

    fun validate(request: LootRequest): Checked<LootRequest> {
        if (request.items.isEmpty()) return Checked.Bad("Add at least one item")
        if (request.items.size > MAX_ITEMS) return Checked.Bad("At most $MAX_ITEMS items")
        if (request.items.any { !validWeight(it.weight) }) return Checked.Bad(WEIGHT_RANGE)
        if (request.items.all { it.weight == 0.0 }) return Checked.Bad("At least one weight must be above 0")
        if (request.tries < 0 || request.tries > MAX_TRIES) return Checked.Bad("Tries must be between 0 and $MAX_TRIES")
        val pity = request.pity
        if (pity != null) {
            if (pity.itemIndex !in request.items.indices) return Checked.Bad("The pity item does not exist")
            if (pity.after < 1 || pity.after > MAX_PITY) return Checked.Bad("Pity must be between 1 and $MAX_PITY")
        }
        return Checked.Ok(request)
    }

    fun validWeight(weight: Double): Boolean =
        weight.isFinite() && (weight == 0.0 || (weight >= MIN_WEIGHT && weight <= MAX_WEIGHT))

    fun analyze(request: LootRequest): Checked<LootOdds> {
        when (val valid = validate(request)) {
            is Checked.Bad -> return valid
            is Checked.Ok -> Unit
        }
        val total = request.items.sumOf { it.weight }
        val probabilities = request.items.map { it.weight / total }
        val pity = request.pity
        val budget = WORK_BUDGET / request.items.size
        val odds = request.items.mapIndexed { index, item ->
            val p = probabilities[index]
            val rate: Double
            val chance: Double?
            if (pity == null) {
                rate = p
                chance = atLeastOne(p, request.tries)
            } else if (index == pity.itemIndex) {
                rate = 1.0 / expectedTriesWithPity(p, pity.after)
                chance = if (request.tries >= pity.after) 1.0 else atLeastOne(p, request.tries)
            } else {
                val target = probabilities[pity.itemIndex]
                rate = p * freeRolls(target, pity.after) / expectedTriesWithPity(target, pity.after)
                chance = chanceWithPity(p, target, request.tries, pity.after, budget)
            }
            ItemOdds(
                name = item.name,
                weight = item.weight,
                probability = p,
                rate = rate,
                triesPerDrop = if (rate > 0.0) 1.0 / rate else null,
                chance = chance,
            )
        }
        return Checked.Ok(LootOdds(total, odds))
    }

    fun atLeastOne(probability: Double, tries: Long): Double {
        if (probability.isNaN() || tries <= 0 || probability <= 0.0) return 0.0
        if (probability >= 1.0) return 1.0
        return (-expm1(tries.toDouble() * ln1p(-probability))).coerceIn(0.0, 1.0)
    }

    fun expectedTriesWithPity(probability: Double, after: Int): Double {
        if (after <= 1) return 1.0
        if (probability.isNaN() || probability <= 0.0) return after.toDouble()
        return atLeastOne(probability, after.toLong()) / probability
    }

    fun chanceWithPity(item: Double, target: Double, tries: Long, after: Int, budget: Long = WORK_BUDGET): Double? {
        if (tries <= 0 || item.isNaN() || item <= 0.0 || after <= 1) return 0.0
        if (tries < after) return atLeastOne(item, tries)
        if (tries.toDouble() * after.toDouble() > budget.toDouble()) return null
        val other = (1.0 - target - item).coerceAtLeast(0.0)
        var alive = DoubleArray(after)
        var next = DoubleArray(after)
        alive[0] = 1.0
        var step = 0L
        while (step < tries) {
            next.fill(0.0)
            for (count in 0 until after) {
                val mass = alive[count]
                if (mass == 0.0) continue
                if (count == after - 1) {
                    next[0] += mass
                } else {
                    next[0] += mass * target
                    next[count + 1] += mass * other
                }
            }
            val swap = alive
            alive = next
            next = swap
            step++
        }
        return (1.0 - alive.sum()).coerceIn(0.0, 1.0)
    }

    private fun freeRolls(target: Double, after: Int): Double {
        if (after <= 1) return 0.0
        if (target <= 0.0) return (after - 1).toDouble()
        return atLeastOne(target, (after - 1).toLong()) / target
    }
}
