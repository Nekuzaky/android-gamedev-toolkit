package dev.nekuz.gamemath

import kotlin.math.abs
import kotlin.math.max

data class CostEntry(val name: String, val ms: Double)

data class CostShare(val name: String, val ms: Double, val fraction: Double)

data class FrameBudgetResult(
    val budgetMs: Double,
    val usedMs: Double,
    val remainingMs: Double,
    val usedFraction: Double,
    val overBudget: Boolean,
    val entries: List<CostShare>,
)

object FrameBudget {
    const val MIN_FPS = 1.0
    const val MAX_FPS = 1000.0
    const val MAX_COST_MS = 1e6
    const val MAX_ENTRIES = 100
    const val NAME_LENGTH = 64
    private const val RELATIVE_TOLERANCE = 1e-9

    fun cleanName(raw: String?, index: Int): String =
        ForeignText.clean(raw, NAME_LENGTH).ifEmpty { "System ${index + 1}" }

    fun budgetMs(fps: Double): Checked<Double> {
        if (!fps.isFinite() || fps < MIN_FPS || fps > MAX_FPS) {
            return Checked.Bad("Target FPS must be between ${Fmt.number(MIN_FPS)} and ${Fmt.number(MAX_FPS)}")
        }
        return Checked.Ok(1000.0 / fps)
    }

    fun compute(fps: Double, entries: List<CostEntry>): Checked<FrameBudgetResult> {
        val budget = when (val checked = budgetMs(fps)) {
            is Checked.Ok -> checked.value
            is Checked.Bad -> return checked
        }
        if (entries.size > MAX_ENTRIES) return Checked.Bad("At most $MAX_ENTRIES entries")
        if (entries.any { !validCost(it.ms) }) {
            return Checked.Bad("A cost must be between 0 and ${Fmt.number(MAX_COST_MS)} ms")
        }
        val used = entries.sumOf { it.ms }
        val shares = entries.map { CostShare(it.name, it.ms, it.ms / budget) }
        val difference = budget - used
        val remaining = if (abs(difference) <= budget * RELATIVE_TOLERANCE) 0.0 else difference
        return Checked.Ok(FrameBudgetResult(budget, used, remaining, used / budget, remaining < 0.0, shares))
    }

    fun validCost(ms: Double): Boolean = ms.isFinite() && ms >= 0.0 && ms <= MAX_COST_MS
}

enum class DamageFormula(val label: String) {
    SUBTRACTIVE("Attack minus defense"),
    ARMOR("Armor: attack x 100 / (100 + defense)"),
    RATIO("Ratio: attack x attack / (attack + defense)"),
    ;

    companion object {
        fun fromLabel(label: String?): DamageFormula = entries.firstOrNull { it.label == label } ?: SUBTRACTIVE
    }
}

data class DamageInput(
    val formula: DamageFormula,
    val attack: Double,
    val defense: Double,
    val critChancePercent: Double,
    val critMultiplier: Double,
    val attacksPerSecond: Double,
    val minimumDamage: Double,
)

data class DamageResult(val normalHit: Double, val critHit: Double, val perHit: Double, val perSecond: Double)

object Damage {
    const val MAX_STAT = 1e12
    const val MAX_MULTIPLIER = 1e6
    const val MAX_ATTACKS_PER_SECOND = 1000.0

    fun compute(input: DamageInput): Checked<DamageResult> {
        val problem = when {
            !inRange(input.attack, MAX_STAT) -> "Attack must be between 0 and ${Fmt.number(MAX_STAT)}"
            !inRange(input.defense, MAX_STAT) -> "Defense must be between 0 and ${Fmt.number(MAX_STAT)}"
            !inRange(input.critChancePercent, 100.0) -> "Crit chance must be between 0 and 100"
            !inRange(input.critMultiplier, MAX_MULTIPLIER) ->
                "Crit multiplier must be between 0 and ${Fmt.number(MAX_MULTIPLIER)}"
            !inRange(input.attacksPerSecond, MAX_ATTACKS_PER_SECOND) ->
                "Attacks per second must be between 0 and ${Fmt.number(MAX_ATTACKS_PER_SECOND)}"
            !inRange(input.minimumDamage, MAX_STAT) -> "Minimum damage must be between 0 and ${Fmt.number(MAX_STAT)}"
            else -> null
        }
        if (problem != null) return Checked.Bad(problem)
        val raw = when (input.formula) {
            DamageFormula.SUBTRACTIVE -> input.attack - input.defense
            DamageFormula.ARMOR -> input.attack * 100.0 / (100.0 + input.defense)
            DamageFormula.RATIO -> {
                val sum = input.attack + input.defense
                if (sum == 0.0) 0.0 else input.attack * (input.attack / sum)
            }
        }
        val normal = max(raw, input.minimumDamage)
        val crit = normal * input.critMultiplier
        val chance = input.critChancePercent / 100.0
        val perHit = normal * (1.0 - chance) + crit * chance
        return Checked.Ok(DamageResult(normal, crit, perHit, perHit * input.attacksPerSecond))
    }

    private fun inRange(value: Double, max: Double) = value.isFinite() && value >= 0.0 && value <= max
}
