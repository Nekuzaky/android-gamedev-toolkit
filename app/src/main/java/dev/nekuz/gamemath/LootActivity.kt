package dev.nekuz.gamemath

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar

class LootActivity : ToolActivity() {
    override val tool = Tool.LOOT

    private lateinit var editor: RowsEditor
    private lateinit var tries: TextField
    private lateinit var pityAfter: TextField
    private lateinit var pityItem: TextField
    private lateinit var odds: LinearLayout
    private lateinit var runs: TextField
    private lateinit var seed: TextField
    private lateinit var runButton: Button
    private lateinit var bar: ProgressBar
    private lateinit var mcNotes: LinearLayout
    private lateinit var exportName: TextField
    private lateinit var exportFormat: ChoiceField
    private lateinit var export: ExportPanel
    private var currentPlan: MonteCarloPlan? = null
    private var wasRunning = false
    private val listener: () -> Unit = { onStoreChanged() }

    override fun build() {
        val panels = addTabs(R.string.tab_items, R.string.tab_odds, R.string.tab_export)
        val items = panels[0]
        addHeader(items, R.string.loot_items)
        val list = addResults(items)
        editor = RowsEditor(list, Loot.MAX_ITEMS, R.string.item_name, R.string.item_weight, R.string.item_add) { form.changed() }
        editor.set(listOf(listOf("Common", "70"), listOf("Rare", "25"), listOf("Legendary", "5")))
        form.rows = editor
        tries = form.text(items, "tries", R.string.tries, "20", InputKind.WHOLE)
        pityAfter = form.text(items, "pity_after", R.string.pity_after, "", InputKind.WHOLE)
        pityItem = form.text(items, "pity_item", R.string.pity_item, "", InputKind.WHOLE)
        addNote(items, R.string.pity_help)

        val oddsPanel = panels[1]
        addHeader(oddsPanel, R.string.mc_title)
        runs = form.text(oddsPanel, "mc_runs", R.string.mc_runs, "10000", InputKind.WHOLE)
        seed = form.text(oddsPanel, "mc_seed", R.string.mc_seed, "42", InputKind.SIGNED_WHOLE)
        runButton = LayoutInflater.from(this).inflate(R.layout.view_button, oddsPanel, false) as Button
        runButton.setOnClickListener { onRun() }
        oddsPanel.addView(runButton)
        bar = LayoutInflater.from(this).inflate(R.layout.view_progress, oddsPanel, false) as ProgressBar
        oddsPanel.addView(bar)
        mcNotes = addResults(oddsPanel)
        addHeader(oddsPanel, R.string.odds_exact)
        odds = addResults(oddsPanel)

        val out = panels[2]
        exportName = form.text(out, "export_name", R.string.export_name, "LootTable", InputKind.TEXT, PresetCodec.MAX_VALUE)
        exportFormat = form.choice(
            out,
            "export_format",
            R.string.export_format,
            listOf(getString(R.string.export_csharp), getString(R.string.export_json)),
        )
        export = ExportPanel(this, out, R.string.tool_loot)
    }

    override fun onStart() {
        super.onStart()
        MonteCarloStore.addListener(listener)
        wasRunning = MonteCarloStore.state.running
        refresh()
    }

    override fun onStop() {
        super.onStop()
        MonteCarloStore.removeListener(listener)
        if (!isChangingConfigurations) MonteCarloStore.cancel()
    }

    override fun render() {
        val request = readRequest()
        val analysis = request?.let { Loot.analyze(it) }
        val result = analysis?.valueOrNull()
        setStatus(
            when {
                analysis is Checked.Bad -> analysis.message
                result == null -> getString(R.string.fix_fields)
                else -> resources.getQuantityString(
                    R.plurals.loot_summary,
                    result.items.size,
                    result.items.size,
                    Fmt.number(result.totalWeight),
                )
            },
        )
        val sessions = runs.check(Inputs.long(runs.text, 1, MonteCarlo.MAX_SESSIONS))
        val seedValue = seed.check(Inputs.long(seed.text, Long.MIN_VALUE, Long.MAX_VALUE))
        val planned = if (request != null && sessions != null && seedValue != null && result != null) {
            MonteCarlo.plan(request, sessions, seedValue)
        } else {
            null
        }
        currentPlan = planned?.valueOrNull()
        if (tab == 1) renderOdds(request, result, analysis as? Checked.Bad, planned)
        if (tab == 2) export.show(exportResult(request, analysis))
        renderControls()
    }

    private fun exportResult(request: LootRequest?, analysis: Checked<LootOdds>?): Checked<String>? {
        if (request == null) return null
        if (analysis is Checked.Bad) return analysis
        return if (exportFormat.index == 0) {
            CSharp.loot(exportName.text, request.items, request.pity)
        } else {
            JsonExport.loot(exportName.text, request.items, request.pity)
        }
    }

    private fun readRequest(): LootRequest? {
        val items = ArrayList<LootItem>()
        var valid = true
        editor.rows.forEachIndexed { index, row ->
            val weight = row.check(readWeight(row.value))
            if (weight == null) valid = false else items.add(LootItem(Loot.cleanName(row.name, index), weight))
        }
        val triesValue = tries.check(Inputs.long(tries.text, 0, Loot.MAX_TRIES))
        val after = pityAfter.check(Inputs.optionalInt(pityAfter.text, 1, Loot.MAX_PITY))
        val itemCount = editor.rows.size.coerceAtLeast(1)
        val itemNumber = if (pityAfter.text.isBlank()) {
            pityItem.check(Inputs.optionalInt(pityItem.text, 1, itemCount))
        } else {
            pityItem.whole(1, itemCount)
        }
        if (!valid || triesValue == null) return null
        if (pityAfter.text.isNotBlank() && (after == null || itemNumber == null)) return null
        if (pityAfter.text.isBlank() && pityItem.text.isNotBlank() && itemNumber == null) return null
        val pity = if (after != null && itemNumber != null) Pity(itemNumber - 1, after) else null
        return LootRequest(items, triesValue, pity)
    }

    private fun readWeight(raw: String): Checked<Double> {
        val parsed = Inputs.double(raw, 0.0, Loot.MAX_WEIGHT)
        if (parsed is Checked.Ok && !Loot.validWeight(parsed.value)) return Checked.Bad(Loot.WEIGHT_RANGE)
        return parsed
    }

    private fun renderOdds(
        request: LootRequest?,
        result: LootOdds?,
        problem: Checked.Bad?,
        planned: Checked<MonteCarloPlan>?,
    ) {
        val state = MonteCarloStore.state
        val notes = ArrayList<Row>()
        val plan = planned?.valueOrNull()
        if (planned is Checked.Bad) notes.add(Row.Note(planned.message))
        if (plan != null && plan.capped) {
            notes.add(Row.Note(getString(R.string.mc_capped, plan.sessions.toString(), MonteCarlo.MAX_ROLLS.toString())))
        }
        val finished = state.result.takeIf { !state.running && state.plan != null && state.plan == plan }
        if (state.failed && state.plan == plan) notes.add(Row.Note(getString(R.string.mc_failed)))
        if (finished != null) {
            val format = if (finished.cancelled) R.string.mc_cancelled else R.string.mc_summary
            notes.add(
                Row.Note(
                    getString(format, finished.sessions.toString(), finished.rolls.toString(), plan?.seed?.toString().orEmpty()),
                ),
            )
        } else if (!state.running && state.result != null) {
            notes.add(Row.Note(getString(R.string.mc_stale)))
        }
        if (finished != null && request?.pity != null) notes.add(Row.Note(getString(R.string.odds_mc_pity)))
        RowViews.fill(mcNotes, notes)
        val rows = ArrayList<Row>()
        if (problem != null) rows.add(Row.Note(problem.message))
        if (request == null && problem == null) rows.add(Row.Note(getString(R.string.fix_fields)))
        result?.items?.forEachIndexed { index, item ->
            rows.add(Row.Value(item.name, itemText(item, request?.tries ?: 0, finished, index)))
        }
        RowViews.fill(odds, rows)
    }

    private fun itemText(item: ItemOdds, triesCount: Long, finished: MonteCarloResult?, index: Int): String {
        val lines = ArrayList<String>()
        lines.add(getString(R.string.odds_chance, Fmt.percent(item.probability)))
        if (item.rate != item.probability) lines.add(getString(R.string.odds_rate, Fmt.percent(item.rate)))
        lines.add(
            item.triesPerDrop?.let { getString(R.string.odds_expected, Fmt.number(it)) }
                ?: getString(R.string.odds_never),
        )
        lines.add(
            item.chance?.let { getString(R.string.odds_in_tries, triesCount.toString(), Fmt.percent(it)) }
                ?: getString(R.string.odds_too_big, triesCount.toString()),
        )
        if (finished != null) {
            lines.add(
                getString(R.string.odds_mc, Fmt.percent(finished.rate(index)), Fmt.percent(finished.chance(index)), triesCount.toString()),
            )
        }
        return lines.joinToString("\n")
    }

    private fun onRun() {
        if (MonteCarloStore.state.running) {
            MonteCarloStore.cancel()
            return
        }
        hideKeyboard()
        currentPlan?.let { MonteCarloStore.start(it) }
    }

    private fun onStoreChanged() {
        val running = MonteCarloStore.state.running
        if (running && wasRunning) {
            renderControls()
        } else {
            wasRunning = running
            refresh()
        }
    }

    private fun renderControls() {
        val state = MonteCarloStore.state
        runButton.setText(if (state.running) R.string.mc_cancel else R.string.mc_start)
        val enabled = state.running || currentPlan != null
        runButton.isEnabled = enabled
        runButton.alpha = if (enabled) 1f else 0.4f
        bar.visibility = if (state.running) View.VISIBLE else View.GONE
        bar.progress = state.percent
        if (state.running) setStatus(getString(R.string.mc_running, state.percent))
    }
}
