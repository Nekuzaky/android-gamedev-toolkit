package dev.nekuz.gamemath

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class EditorRow(val root: View, private val nameInput: EditText, private val valueInput: EditText, private val error: TextView) {
    val name: String get() = nameInput.text.toString()
    val value: String get() = valueInput.text.toString()

    fun <T> check(result: Checked<T>): T? = when (result) {
        is Checked.Ok -> {
            FieldSetup.showError(error, null)
            result.value
        }
        is Checked.Bad -> {
            FieldSetup.showError(error, result.message)
            null
        }
    }
}

class RowsEditor(
    private val parent: ViewGroup,
    private val maxRows: Int,
    private val nameLabel: Int,
    private val valueLabel: Int,
    addLabel: Int,
    private val onChange: () -> Unit,
) {
    private val inflater = LayoutInflater.from(parent.context)
    private val list = ArrayList<EditorRow>()
    private val addButton: Button
    private var loading = false

    val rows: List<EditorRow> get() = list

    init {
        addButton = inflater.inflate(R.layout.view_button, parent, false) as Button
        addButton.setText(addLabel)
        addButton.setOnClickListener {
            if (list.size < maxRows) {
                addRow("", "1")
                changed()
            }
        }
        parent.addView(addButton)
    }

    fun set(values: List<List<String>>) {
        loading = true
        try {
            list.forEach { parent.removeView(it.root) }
            list.clear()
            values.take(maxRows).forEach { addRow(it.getOrElse(0) { "" }, it.getOrElse(1) { "" }) }
        } finally {
            loading = false
        }
        refresh()
    }

    fun capture(): List<List<String>> = list.map { listOf(it.name, it.value) }

    private fun addRow(name: String, value: String) {
        val root = inflater.inflate(R.layout.row_editor, parent, false)
        val nameInput = root.findViewById<EditText>(R.id.name)
        val valueInput = root.findViewById<EditText>(R.id.value)
        FieldSetup.configure(nameInput, InputKind.TEXT, PresetCodec.MAX_VALUE)
        FieldSetup.configure(valueInput, InputKind.DECIMAL, Inputs.MAX_LENGTH)
        nameInput.setHint(nameLabel)
        valueInput.setHint(valueLabel)
        nameInput.setText(name)
        valueInput.setText(value)
        nameInput.addTextChangedListener(watcher { changed() })
        valueInput.addTextChangedListener(watcher { changed() })
        val row = EditorRow(root, nameInput, valueInput, root.findViewById(R.id.error))
        root.findViewById<View>(R.id.delete).setOnClickListener {
            parent.removeView(root)
            list.remove(row)
            refresh()
            changed()
        }
        parent.addView(root, parent.indexOfChild(addButton))
        list.add(row)
        refresh()
    }

    private fun refresh() {
        list.forEachIndexed { index, row ->
            row.root.findViewById<TextView>(R.id.number).text = parent.context.getString(R.string.plain_number, index + 1)
        }
        addButton.isEnabled = list.size < maxRows
        addButton.alpha = if (addButton.isEnabled) 1f else 0.4f
    }

    private fun changed() {
        if (!loading) onChange()
    }
}
