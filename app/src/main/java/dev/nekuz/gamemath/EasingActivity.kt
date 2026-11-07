package dev.nekuz.gamemath

import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout

class EasingActivity : ToolActivity() {
    override val tool = Tool.EASING

    private lateinit var choice: ChoiceField
    private lateinit var chart: EasingChartView
    private lateinit var code: LinearLayout
    private var shown: Easing? = null

    override fun build() {
        choice = form.choice(content, "easing", R.string.easing_choice, Easing.entries.map { it.label })
        chart = LayoutInflater.from(this).inflate(R.layout.view_easing_chart, content, false) as EasingChartView
        content.addView(chart)
        val copy = LayoutInflater.from(this).inflate(R.layout.view_button, content, false) as Button
        copy.setText(R.string.easing_copy)
        copy.setOnClickListener { Actions.copy(this, Easing.fromLabel(choice.value).csharp()) }
        content.addView(copy)
        code = addResults(content)
    }

    override fun onStart() {
        super.onStart()
        chart.setRunning(true)
    }

    override fun onStop() {
        chart.setRunning(false)
        super.onStop()
    }

    override fun render() {
        val easing = Easing.fromLabel(choice.value)
        setStatus(easing.label)
        if (easing == shown) return
        shown = easing
        chart.setEasing(easing)
        RowViews.fill(code, listOf(Row.Value(getString(R.string.easing_code), easing.csharp())))
    }
}
