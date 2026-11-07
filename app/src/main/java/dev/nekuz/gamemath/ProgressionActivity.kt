package dev.nekuz.gamemath

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

class ProgressionActivity : ToolActivity() {
    override val tool = Tool.PROGRESSION

    private lateinit var curve: ChoiceField
    private lateinit var levels: TextField
    private lateinit var linear: List<TextField>
    private lateinit var polynomial: List<TextField>
    private lateinit var exponential: List<TextField>
    private lateinit var logistic: List<TextField>
    private lateinit var formula: TextView
    private lateinit var chart: ChartView
    private lateinit var notes: LinearLayout
    private lateinit var exportName: TextField
    private lateinit var exportFormat: ChoiceField
    private lateinit var exportType: ChoiceField
    private lateinit var export: ExportPanel
    private val adapter = LevelAdapter()

    override fun build() {
        val panels = addTabs(R.string.tab_curve, R.string.tab_table, R.string.tab_export)
        val setup = panels[0]
        curve = form.choice(setup, "curve", R.string.curve_type, CURVES)
        levels = form.text(setup, "levels", R.string.levels, "50", InputKind.WHOLE)
        linear = listOf(
            number(setup, "linear_base", R.string.field_base, "100"),
            number(setup, "linear_slope", R.string.field_slope, "50"),
        )
        polynomial = listOf(
            number(setup, "poly_base", R.string.field_base, "100"),
            number(setup, "poly_scale", R.string.field_scale, "50"),
            number(setup, "poly_power", R.string.field_power, "2"),
        )
        exponential = listOf(
            number(setup, "exp_base", R.string.field_base, "100"),
            number(setup, "exp_growth", R.string.field_growth, "1.15"),
        )
        logistic = listOf(
            number(setup, "log_max", R.string.field_max, "10000"),
            number(setup, "log_steep", R.string.field_steepness, "0.2"),
            number(setup, "log_mid", R.string.field_midpoint, "25"),
        )
        formula = LayoutInflater.from(this).inflate(R.layout.row_note, setup, false) as TextView
        setup.addView(formula)
        chart = LayoutInflater.from(this).inflate(R.layout.view_chart, setup, false) as ChartView
        setup.addView(chart)
        notes = addResults(setup)

        val header = LayoutInflater.from(this).inflate(R.layout.row_table, table, false)
        header.findViewById<TextView>(R.id.level).setText(R.string.table_level)
        header.findViewById<TextView>(R.id.value).setText(R.string.table_value)
        header.findViewById<TextView>(R.id.total).setText(R.string.table_total)
        table.addHeaderView(header, null, false)
        table.adapter = adapter

        val out = panels[2]
        exportName = form.text(out, "export_name", R.string.export_name, "XpCurve", InputKind.TEXT, PresetCodec.MAX_VALUE)
        exportFormat = form.choice(
            out,
            "export_format",
            R.string.export_format,
            listOf(getString(R.string.export_csharp), getString(R.string.export_json)),
        )
        exportType = form.choice(out, "export_type", R.string.export_type, NumberType.entries.map { it.label })
        exportType.select(NumberType.LONG.label)
        export = ExportPanel(this, out, R.string.tool_progression)
    }

    override fun render() {
        val kind = curve.index
        linear.forEach { it.show(kind == 0) }
        polynomial.forEach { it.show(kind == 1) }
        exponential.forEach { it.show(kind == 2) }
        logistic.forEach { it.show(kind == 3) }
        formula.setText(FORMULAS[kind])
        val requested = levels.check(Inputs.long(levels.text, 1, Int.MAX_VALUE.toLong()))
        val parsed = readCurve(kind)
        val result = if (requested != null && parsed != null) Progression.generate(parsed, requested) else null
        val curveTable = result?.valueOrNull()
        showList(tab == 1)
        adapter.update(curveTable)
        chart.setValues(curveTable?.values ?: EMPTY)
        val rows = ArrayList<Row>()
        when {
            curveTable == null -> {
                setStatus(getString(R.string.fix_fields))
                if (result is Checked.Bad) rows.add(Row.Note(result.message))
            }
            else -> {
                setStatus(
                    resources.getQuantityString(
                        R.plurals.curve_summary,
                        curveTable.size,
                        curveTable.size,
                        Fmt.number(curveTable.values.first()),
                        Fmt.number(curveTable.values.last()),
                    ),
                )
                if (curveTable.capped) {
                    rows.add(
                        Row.Note(
                            getString(R.string.curve_capped, curveTable.requestedLevels.toString(), Progression.MAX_LEVELS),
                        ),
                    )
                }
                curveTable.stoppedAtLevel?.let { rows.add(Row.Note(getString(R.string.curve_stopped, it))) }
            }
        }
        RowViews.fill(notes, rows)
        if (tab == 2) export.show(exportResult(curveTable))
    }

    private fun number(parent: ViewGroup, key: String, label: Int, default: String) =
        form.text(parent, key, label, default, InputKind.SIGNED_DECIMAL)

    private fun exportResult(curveTable: CurveTable?): Checked<String>? {
        if (curveTable == null) return null
        val type = NumberType.fromLabel(exportType.value)
        return if (exportFormat.index == 0) {
            CSharp.progression(exportName.text, curveTable.values, type)
        } else {
            JsonExport.progression(exportName.text, curve.value, curveTable.values, type)
        }
    }

    private fun readCurve(kind: Int): Curve? {
        val m = Progression.MAX_MAGNITUDE
        return when (kind) {
            0 -> {
                val base = linear[0].decimal(-m, m)
                val slope = linear[1].decimal(-m, m)
                if (base == null || slope == null) null else Curve.Linear(base, slope)
            }
            1 -> {
                val base = polynomial[0].decimal(-m, m)
                val scale = polynomial[1].decimal(-m, m)
                val power = polynomial[2].decimal(0.0, Progression.MAX_POWER)
                if (base == null || scale == null || power == null) null else Curve.Polynomial(base, scale, power)
            }
            2 -> {
                val base = exponential[0].decimal(-m, m)
                val growth = exponential[1].decimal(MIN_GROWTH, Progression.MAX_GROWTH)
                if (base == null || growth == null) null else Curve.Exponential(base, growth)
            }
            else -> {
                val max = logistic[0].decimal(-m, m)
                val steep = logistic[1].decimal(-Progression.MAX_STEEPNESS, Progression.MAX_STEEPNESS)
                val mid = logistic[2].decimal(-Progression.MAX_MIDPOINT, Progression.MAX_MIDPOINT)
                if (max == null || steep == null || mid == null) null else Curve.Logistic(max, steep, mid)
            }
        }
    }

    private class LevelAdapter : BaseAdapter() {
        private var data: CurveTable? = null

        fun update(next: CurveTable?) {
            data = next
            notifyDataSetChanged()
        }

        override fun getCount() = data?.size ?: 0

        override fun getItem(position: Int): Any = position

        override fun getItemId(position: Int) = position.toLong()

        override fun areAllItemsEnabled() = false

        override fun isEnabled(position: Int) = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.row_table, parent, false)
            val value = data?.values?.getOrNull(position) ?: Double.NaN
            val total = data?.totals?.getOrNull(position) ?: Double.NaN
            view.findViewById<TextView>(R.id.level).text = parent.context.getString(R.string.plain_number, position + 1)
            view.findViewById<TextView>(R.id.value).text = Fmt.number(value)
            view.findViewById<TextView>(R.id.total).text = Fmt.number(total)
            return view
        }
    }

    private companion object {
        const val MIN_GROWTH = 0.000001
        val EMPTY = DoubleArray(0)
        val CURVES = listOf("Linear", "Polynomial", "Exponential", "Logistic")
        val FORMULAS = intArrayOf(
            R.string.formula_linear,
            R.string.formula_polynomial,
            R.string.formula_exponential,
            R.string.formula_logistic,
        )
    }
}
