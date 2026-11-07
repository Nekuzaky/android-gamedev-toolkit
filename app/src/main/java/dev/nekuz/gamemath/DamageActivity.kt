package dev.nekuz.gamemath

import android.widget.LinearLayout

class DamageActivity : ToolActivity() {
    override val tool = Tool.DAMAGE

    private lateinit var formula: ChoiceField
    private lateinit var attack: TextField
    private lateinit var defense: TextField
    private lateinit var critChance: TextField
    private lateinit var critMultiplier: TextField
    private lateinit var attacksPerSecond: TextField
    private lateinit var minimum: TextField
    private lateinit var results: LinearLayout

    override fun build() {
        formula = form.choice(content, "formula", R.string.damage_formula, DamageFormula.entries.map { it.label })
        attack = form.text(content, "attack", R.string.attack, "100", InputKind.DECIMAL)
        defense = form.text(content, "defense", R.string.defense, "40", InputKind.DECIMAL)
        critChance = form.text(content, "crit_chance", R.string.crit_chance, "25", InputKind.DECIMAL)
        critMultiplier = form.text(content, "crit_multiplier", R.string.crit_multiplier, "2", InputKind.DECIMAL)
        attacksPerSecond = form.text(content, "aps", R.string.attacks_per_second, "1.5", InputKind.DECIMAL)
        minimum = form.text(content, "minimum", R.string.minimum_damage, "1", InputKind.DECIMAL)
        results = addResults(content)
    }

    override fun render() {
        val a = attack.decimal(0.0, Damage.MAX_STAT)
        val d = defense.decimal(0.0, Damage.MAX_STAT)
        val c = critChance.decimal(0.0, 100.0)
        val m = critMultiplier.decimal(0.0, Damage.MAX_MULTIPLIER)
        val s = attacksPerSecond.decimal(0.0, Damage.MAX_ATTACKS_PER_SECOND)
        val low = minimum.decimal(0.0, Damage.MAX_STAT)
        val result = if (a != null && d != null && c != null && m != null && s != null && low != null) {
            Damage.compute(DamageInput(DamageFormula.fromLabel(formula.value), a, d, c, m, s, low))
        } else {
            null
        }
        val rows = ArrayList<Row>()
        when (result) {
            null -> setStatus(getString(R.string.fix_fields))
            is Checked.Bad -> {
                setStatus(result.message)
                rows.add(Row.Note(result.message))
            }
            is Checked.Ok -> {
                val value = result.value
                setStatus(Fmt.number(value.perSecond))
                rows.add(Row.Value(getString(R.string.damage_per_second), Fmt.number(value.perSecond), highlighted = true))
                rows.add(Row.Value(getString(R.string.damage_per_hit), Fmt.number(value.perHit)))
                rows.add(Row.Value(getString(R.string.damage_normal), Fmt.number(value.normalHit)))
                rows.add(Row.Value(getString(R.string.damage_crit), Fmt.number(value.critHit)))
            }
        }
        RowViews.fill(results, rows)
    }
}
