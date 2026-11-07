package dev.nekuz.gamemath

import android.widget.LinearLayout

class FrameBudgetActivity : ToolActivity() {
    override val tool = Tool.FRAME

    private lateinit var fps: TextField
    private lateinit var editor: RowsEditor
    private lateinit var results: LinearLayout

    override fun build() {
        fps = form.text(content, "fps", R.string.fps, "60", InputKind.DECIMAL)
        addHeader(content, R.string.frame_systems)
        val list = addResults(content)
        editor = RowsEditor(
            list,
            FrameBudget.MAX_ENTRIES,
            R.string.system_name,
            R.string.system_cost,
            R.string.system_add,
        ) { form.changed() }
        editor.set(
            listOf(
                listOf("Rendering", "6"),
                listOf("Scripts", "4"),
                listOf("Physics", "2"),
                listOf("UI", "1"),
            ),
        )
        form.rows = editor
        results = addResults(content)
    }

    override fun render() {
        val target = fps.decimal(FrameBudget.MIN_FPS, FrameBudget.MAX_FPS)
        val entries = ArrayList<CostEntry>()
        var valid = true
        editor.rows.forEachIndexed { index, row ->
            val cost = row.check(Inputs.double(row.value, 0.0, FrameBudget.MAX_COST_MS))
            if (cost == null) valid = false else entries.add(CostEntry(FrameBudget.cleanName(row.name, index), cost))
        }
        val result = if (target != null && valid) FrameBudget.compute(target, entries) else null
        val rows = ArrayList<Row>()
        when (result) {
            null -> setStatus(getString(R.string.fix_fields))
            is Checked.Bad -> {
                setStatus(result.message)
                rows.add(Row.Note(result.message))
            }
            is Checked.Ok -> {
                val value = result.value
                setStatus(getString(R.string.frame_value_ms, Fmt.number(value.budgetMs)))
                rows.add(Row.Value(getString(R.string.frame_budget), ms(value.budgetMs)))
                rows.add(
                    Row.Value(
                        getString(R.string.frame_used),
                        getString(R.string.frame_used_value, Fmt.number(value.usedMs), Fmt.percent(value.usedFraction)),
                    ),
                )
                rows.add(
                    Row.Value(
                        label = getString(R.string.frame_remaining),
                        value = ms(value.remainingMs),
                        tag = getString(if (value.overBudget) R.string.frame_over else R.string.frame_ok),
                        tagColor = if (value.overBudget) R.color.level_risk else R.color.level_ok,
                        highlighted = value.overBudget,
                    ),
                )
                value.entries.forEach {
                    rows.add(
                        Row.Value(
                            it.name,
                            getString(R.string.frame_used_value, Fmt.number(it.ms), Fmt.percent(it.fraction)),
                        ),
                    )
                }
            }
        }
        RowViews.fill(results, rows)
    }

    private fun ms(value: Double) = getString(R.string.frame_value_ms, Fmt.number(value))
}
