package dev.nekuz.gamemath

import android.app.Activity
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView

enum class InputKind { WHOLE, SIGNED_WHOLE, DECIMAL, SIGNED_DECIMAL, TEXT }

object FieldSetup {
    private const val DECIMAL_CHARS = "0123456789.,+-eE"
    private const val WHOLE_CHARS = "0123456789+-"

    fun configure(input: EditText, kind: InputKind, maxLength: Int) {
        input.isSaveEnabled = false
        when (kind) {
            InputKind.TEXT -> input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            InputKind.WHOLE -> numeric(input, WHOLE_CHARS, InputType.TYPE_CLASS_NUMBER)
            InputKind.SIGNED_WHOLE ->
                numeric(input, WHOLE_CHARS, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
            InputKind.DECIMAL ->
                numeric(input, DECIMAL_CHARS, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            InputKind.SIGNED_DECIMAL -> numeric(
                input,
                DECIMAL_CHARS,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED,
            )
        }
        input.filters = arrayOf(InputFilter.LengthFilter(maxLength))
    }

    fun showError(view: TextView, message: String?) {
        if (view.text.toString() != message.orEmpty()) view.text = message.orEmpty()
        view.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun numeric(input: EditText, accepted: String, raw: Int) {
        input.keyListener = DigitsKeyListener.getInstance(accepted)
        input.setRawInputType(raw)
    }
}

class TextField(val root: View, private val input: EditText, private val error: TextView) {
    var text: String
        get() = input.text.toString()
        set(value) {
            if (input.text.toString() != value) input.setText(value)
        }

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

    fun decimal(min: Double, max: Double): Double? = check(Inputs.double(text, min, max))

    fun whole(min: Int, max: Int): Int? = check(Inputs.int(text, min, max))

    fun show(visible: Boolean) {
        root.visibility = if (visible) View.VISIBLE else View.GONE
    }
}

class ChoiceField(val root: View, private val spinner: Spinner, private val options: List<String>) {
    var index: Int = 0
        private set

    val value: String get() = options[index]

    fun select(label: String?) {
        val position = options.indexOf(label)
        if (position < 0) return
        index = position
        spinner.setSelection(position, false)
    }

    internal fun changed(position: Int): Boolean {
        if (position == index || position !in options.indices) return false
        index = position
        return true
    }
}

class ToggleField(val root: CheckBox) {
    var checked: Boolean
        get() = root.isChecked
        set(value) {
            root.isChecked = value
        }
}

class Form(private val activity: Activity, private val onChange: () -> Unit) {
    private val inflater = LayoutInflater.from(activity)
    private val texts = LinkedHashMap<String, TextField>()
    private val choices = LinkedHashMap<String, ChoiceField>()
    private val toggles = LinkedHashMap<String, ToggleField>()
    private var loading = false

    var rows: RowsEditor? = null

    fun changed() {
        if (!loading) onChange()
    }

    fun text(
        parent: ViewGroup,
        key: String,
        label: Int,
        default: String,
        kind: InputKind,
        maxLength: Int = Inputs.MAX_LENGTH,
    ): TextField {
        val root = inflater.inflate(R.layout.field_text, parent, false)
        val input = root.findViewById<EditText>(R.id.input)
        root.findViewById<TextView>(R.id.label).setText(label)
        input.setHint(label)
        FieldSetup.configure(input, kind, maxLength)
        input.setText(default)
        input.addTextChangedListener(watcher { changed() })
        parent.addView(root)
        val field = TextField(root, input, root.findViewById(R.id.error))
        texts[key] = field
        return field
    }

    fun choice(parent: ViewGroup, key: String, label: Int, options: List<String>): ChoiceField {
        val root = inflater.inflate(R.layout.field_choice, parent, false)
        val spinner = root.findViewById<Spinner>(R.id.spinner)
        root.findViewById<TextView>(R.id.label).setText(label)
        spinner.isSaveEnabled = false
        spinner.contentDescription = activity.getString(label)
        spinner.adapter = ArrayAdapter(activity, R.layout.spinner_item, options).also {
            it.setDropDownViewResource(R.layout.spinner_item)
        }
        val field = ChoiceField(root, spinner, options)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(view: AdapterView<*>?, item: View?, position: Int, id: Long) {
                if (field.changed(position)) changed()
            }

            override fun onNothingSelected(view: AdapterView<*>?) = Unit
        }
        parent.addView(root)
        choices[key] = field
        return field
    }

    fun toggle(parent: ViewGroup, key: String, label: Int, default: Boolean): ToggleField {
        val box = inflater.inflate(R.layout.field_toggle, parent, false) as CheckBox
        box.setText(label)
        box.isSaveEnabled = false
        box.isChecked = default
        box.setOnCheckedChangeListener { _, _ -> changed() }
        parent.addView(box)
        val field = ToggleField(box)
        toggles[key] = field
        return field
    }

    fun capture(name: String): Preset {
        val fields = LinkedHashMap<String, String>()
        texts.forEach { (key, field) -> fields[key] = field.text }
        choices.forEach { (key, field) -> fields[key] = field.value }
        toggles.forEach { (key, field) -> fields[key] = field.checked.toString() }
        return Preset(name, fields, rows?.capture() ?: emptyList())
    }

    fun restore(preset: Preset) {
        loading = true
        try {
            texts.forEach { (key, field) -> preset.fields[key]?.let { field.text = it } }
            choices.forEach { (key, field) -> field.select(preset.fields[key]) }
            toggles.forEach { (key, field) ->
                when (preset.fields[key]) {
                    "true" -> field.checked = true
                    "false" -> field.checked = false
                }
            }
            rows?.set(preset.rows)
        } finally {
            loading = false
        }
    }
}

fun watcher(after: () -> Unit) = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, afterCount: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) = after()
}
