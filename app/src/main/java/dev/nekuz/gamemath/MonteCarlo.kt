package dev.nekuz.gamemath

class SplitMix64(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN
        var mixed = state
        mixed = (mixed xor (mixed ushr 30)) * MIX_A
        mixed = (mixed xor (mixed ushr 27)) * MIX_B
        return mixed xor (mixed ushr 31)
    }

    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * UNIT

    private companion object {
        val GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
        val MIX_A = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX_B = 0x94D049BB133111EBuL.toLong()
        const val UNIT = 1.0 / 9007199254740992.0
    }
}

data class MonteCarloPlan(
    val request: LootRequest,
    val requestedSessions: Long,
    val sessions: Long,
    val seed: Long,
) {
    val capped: Boolean get() = sessions < requestedSessions
}

class MonteCarloResult(
    val rolls: Long,
    val sessions: Long,
    val drops: LongArray,
    val sessionsWithDrop: LongArray,
    val cancelled: Boolean,
) {
    fun rate(index: Int): Double = if (rolls > 0) drops[index].toDouble() / rolls.toDouble() else 0.0

    fun chance(index: Int): Double =
        if (sessions > 0) sessionsWithDrop[index].toDouble() / sessions.toDouble() else 0.0
}

object MonteCarlo {
    const val MAX_SESSIONS = 1_000_000L
    const val MAX_ROLLS = 50_000_000L
    private const val CHECK_MASK = 0xFFFL

    fun plan(request: LootRequest, sessions: Long, seed: Long): Checked<MonteCarloPlan> {
        when (val valid = Loot.validate(request)) {
            is Checked.Bad -> return valid
            is Checked.Ok -> Unit
        }
        if (sessions < 1) return Checked.Bad("Runs must be at least 1")
        if (request.tries < 1) return Checked.Bad("Monte Carlo needs at least 1 try per run")
        if (request.tries > MAX_ROLLS) return Checked.Bad("Monte Carlo allows at most $MAX_ROLLS tries per run")
        val allowed = minOf(sessions, MAX_SESSIONS, MAX_ROLLS / request.tries)
        return Checked.Ok(MonteCarloPlan(request, sessions, allowed, seed))
    }

    fun run(
        plan: MonteCarloPlan,
        isCancelled: () -> Boolean = { false },
        onProgress: (Double) -> Unit = {},
    ): MonteCarloResult {
        val items = plan.request.items
        val cumulative = DoubleArray(items.size)
        var total = 0.0
        var lastPositive = 0
        items.forEachIndexed { index, item ->
            total += item.weight
            cumulative[index] = total
            if (item.weight > 0.0) lastPositive = index
        }
        val random = SplitMix64(plan.seed)
        val drops = LongArray(items.size)
        val withDrop = LongArray(items.size)
        val seen = LongArray(items.size) { -1L }
        val pity = plan.request.pity
        val planned = (plan.sessions * plan.request.tries).toDouble()
        var rolls = 0L
        var session = 0L
        while (session < plan.sessions) {
            var counter = 0
            var attempt = 0L
            while (attempt < plan.request.tries) {
                if (rolls and CHECK_MASK == 0L) {
                    if (isCancelled()) {
                        for (index in seen.indices) {
                            if (seen[index] == session) withDrop[index]--
                        }
                        return MonteCarloResult(rolls, session, drops, withDrop, true)
                    }
                    onProgress(rolls.toDouble() / planned)
                }
                val picked = if (pity != null && counter == pity.after - 1) {
                    pity.itemIndex
                } else {
                    pick(cumulative, random.nextDouble() * total, lastPositive)
                }
                if (pity != null) counter = if (picked == pity.itemIndex) 0 else counter + 1
                drops[picked]++
                if (seen[picked] != session) {
                    seen[picked] = session
                    withDrop[picked]++
                }
                rolls++
                attempt++
            }
            session++
        }
        onProgress(1.0)
        return MonteCarloResult(rolls, session, drops, withDrop, false)
    }

    internal fun pick(cumulative: DoubleArray, target: Double, fallback: Int): Int {
        var low = 0
        var high = cumulative.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (cumulative[middle] > target) {
                found = middle
                high = middle - 1
            } else {
                low = middle + 1
            }
        }
        return if (found < 0) fallback else found
    }
}
