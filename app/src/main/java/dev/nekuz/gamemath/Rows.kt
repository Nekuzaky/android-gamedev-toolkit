package dev.nekuz.gamemath

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

sealed class Row {
    data class Header(val text: String) : Row()

    data class Note(val text: String) : Row()

    data class Value(
        val label: String,
        val value: String,
        val tag: String? = null,
        val tagColor: Int = R.color.text_secondary,
        val highlighted: Boolean = false,
    ) : Row()
}

object RowViews {
    fun fill(container: ViewGroup, rows: List<Row>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        for (row in rows) {
            container.addView(
                when (row) {
                    is Row.Header -> text(inflater, container, R.layout.row_header, row.text)
                    is Row.Note -> text(inflater, container, R.layout.row_note, row.text)
                    is Row.Value -> value(inflater, container, row)
                },
            )
        }
    }

    private fun text(inflater: LayoutInflater, parent: ViewGroup, layout: Int, text: String): View {
        val view = inflater.inflate(layout, parent, false)
        (view as TextView).text = text
        return view
    }

    private fun value(inflater: LayoutInflater, parent: ViewGroup, row: Row.Value): View {
        val view = inflater.inflate(R.layout.row_value, parent, false)
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
            (MARGIN_DP * parent.resources.displayMetrics.density).toInt()
        view.setBackgroundResource(if (row.highlighted) R.drawable.bg_row_highlight else R.drawable.bg_row)
        view.findViewById<TextView>(R.id.label).text = row.label
        val tag = view.findViewById<TextView>(R.id.tag)
        tag.visibility = if (row.tag == null) View.GONE else View.VISIBLE
        tag.text = row.tag
        tag.setTextColor(parent.context.getColor(row.tagColor))
        view.findViewById<View>(R.id.labelRow).visibility =
            if (row.label.isEmpty() && row.tag == null) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.value).text = row.value
        view.setOnLongClickListener {
            Actions.copy(parent.context, row.value)
            true
        }
        return view
    }

    private const val MARGIN_DP = 6
}
