package dev.nekuz.gamemath

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

enum class Tool(val id: String, val title: Int, val summary: Int, val activity: Class<out Activity>) {
    PROGRESSION("progression", R.string.tool_progression, R.string.tool_progression_summary, ProgressionActivity::class.java),
    LOOT("loot", R.string.tool_loot, R.string.tool_loot_summary, LootActivity::class.java),
    EASING("easing", R.string.tool_easing, R.string.tool_easing_summary, EasingActivity::class.java),
    TEXTURE("texture", R.string.tool_texture, R.string.tool_texture_summary, TextureActivity::class.java),
    FRAME("frame", R.string.tool_frame, R.string.tool_frame_summary, FrameBudgetActivity::class.java),
    SCREEN("screen", R.string.tool_screen, R.string.tool_screen_summary, ScreenActivity::class.java),
    DAMAGE("damage", R.string.tool_damage, R.string.tool_damage_summary, DamageActivity::class.java),
}

abstract class ToolActivity : Activity() {
    protected abstract val tool: Tool

    protected lateinit var form: Form
    protected lateinit var content: LinearLayout
    protected lateinit var table: ListView
    private lateinit var status: TextView
    private lateinit var scroll: ScrollView
    private lateinit var tabBar: LinearLayout
    private val tabButtons = ArrayList<Button>()
    private val tabPanels = ArrayList<LinearLayout>()
    private var dialog: AlertDialog? = null
    private var ready = false

    protected var tab = 0
        private set

    protected abstract fun build()

    protected abstract fun render()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool)
        status = findViewById(R.id.status)
        content = findViewById(R.id.content)
        scroll = findViewById(R.id.scroll)
        table = findViewById(R.id.table)
        tabBar = findViewById(R.id.tabs)
        findViewById<TextView>(R.id.title).setText(tool.title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.presetsButton).setOnClickListener { openPresets() }
        form = Form(this) { refresh() }
        build()
        PresetCodec.decodeOne(savedInstanceState?.getString(STATE_FORM))?.let { form.restore(it) }
        tab = (savedInstanceState?.getInt(STATE_TAB) ?: 0).coerceIn(0, (tabPanels.size - 1).coerceAtLeast(0))
        ready = true
        applyTab()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tab)
        PresetCodec.encodeOne(form.capture(BUNDLE_NAME))?.let { outState.putString(STATE_FORM, it) }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    protected fun refresh() {
        if (ready) render()
    }

    protected fun setStatus(text: String) {
        status.text = text
    }

    protected fun addTabs(vararg labels: Int): List<LinearLayout> {
        tabBar.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        labels.forEachIndexed { index, label ->
            val button = inflater.inflate(R.layout.tab_button, tabBar, false) as Button
            button.setText(label)
            button.setOnClickListener { selectTab(index) }
            tabBar.addView(button)
            tabButtons.add(button)
            val panel = LinearLayout(this)
            panel.orientation = LinearLayout.VERTICAL
            content.addView(
                panel,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            tabPanels.add(panel)
        }
        return tabPanels.toList()
    }

    protected fun showList(visible: Boolean) {
        table.visibility = if (visible) View.VISIBLE else View.GONE
        scroll.visibility = if (visible) View.GONE else View.VISIBLE
    }

    protected fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    protected fun addHeader(parent: ViewGroup, text: Int) {
        val header = LayoutInflater.from(this).inflate(R.layout.row_header, parent, false) as TextView
        header.setText(text)
        parent.addView(header)
    }

    protected fun addNote(parent: ViewGroup, text: Int) {
        val note = LayoutInflater.from(this).inflate(R.layout.row_note, parent, false) as TextView
        note.setText(text)
        parent.addView(note)
    }

    protected fun addResults(parent: ViewGroup): LinearLayout {
        val results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        parent.addView(
            results,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return results
    }

    private fun selectTab(index: Int) {
        if (index == tab) return
        tab = index
        hideKeyboard()
        applyTab()
        refresh()
    }

    private fun applyTab() {
        tabButtons.forEachIndexed { index, button ->
            val selected = index == tab
            button.setBackgroundResource(if (selected) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
            button.setTextColor(getColor(if (selected) R.color.on_brand else R.color.text_primary))
        }
        tabPanels.forEachIndexed { index, panel ->
            panel.visibility = if (index == tab) View.VISIBLE else View.GONE
        }
        (tabButtons.lastOrNull()?.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = 0
    }

    private fun presetFile() = File(File(filesDir, PRESET_DIR), "presets_${tool.id}.json")

    private fun alive() = !isFinishing && !isDestroyed

    private fun openPresets() {
        val file = presetFile()
        PresetRepository.load(file) { load ->
            if (alive()) showPresets(load)
        }
    }

    private fun showPresets(load: PresetLoad) {
        val presets = (load as? PresetLoad.Loaded)?.presets.orEmpty()
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.presets)
            .setPositiveButton(R.string.preset_save) { _, _ -> askName() }
            .setNegativeButton(R.string.close, null)
        if (load == PresetLoad.Damaged) {
            builder.setMessage(R.string.preset_damaged)
        } else if (load == PresetLoad.Unreadable) {
            builder.setMessage(R.string.preset_unreadable)
        } else if (presets.isEmpty()) {
            builder.setMessage(R.string.preset_none)
        } else {
            builder.setItems(presets.map { it.name }.toTypedArray()) { _, which ->
                presets.getOrNull(which)?.let { showPreset(it) }
            }
        }
        present(builder)
    }

    private fun showPreset(preset: Preset) {
        present(
            AlertDialog.Builder(this)
                .setTitle(preset.name)
                .setPositiveButton(R.string.preset_load) { _, _ ->
                    form.restore(preset)
                    refresh()
                }
                .setNeutralButton(R.string.preset_delete) { _, _ -> deletePreset(preset) }
                .setNegativeButton(R.string.cancel, null),
        )
    }

    private fun askName() {
        val root = LayoutInflater.from(this).inflate(R.layout.dialog_name, FrameLayout(this), false)
        val input = root.findViewById<EditText>(R.id.nameInput)
        present(
            AlertDialog.Builder(this)
                .setTitle(R.string.preset_save)
                .setView(root)
                .setPositiveButton(R.string.preset_save) { _, _ -> savePreset(input.text.toString()) }
                .setNegativeButton(R.string.cancel, null),
        )
    }

    private fun savePreset(raw: String) {
        val name = PresetCodec.cleanName(raw)
        if (name.isEmpty()) {
            Actions.toast(this, R.string.preset_name_needed)
            return
        }
        PresetRepository.save(presetFile(), form.capture(name)) { outcome ->
            if (alive()) {
                Actions.toast(
                    this,
                    when (outcome) {
                        SaveOutcome.SAVED -> R.string.preset_saved
                        SaveOutcome.FULL -> R.string.preset_full
                        SaveOutcome.FAILED -> R.string.preset_save_failed
                    },
                )
            }
        }
    }

    private fun deletePreset(preset: Preset) {
        PresetRepository.delete(presetFile(), preset.name) { saved ->
            if (alive()) Actions.toast(this, if (saved) R.string.preset_deleted else R.string.preset_save_failed)
        }
    }

    private fun present(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        dialog = builder.show()
        dialog?.window?.decorView?.filterTouchesWhenObscured = true
    }

    private companion object {
        const val STATE_FORM = "form"
        const val STATE_TAB = "tab"
        const val BUNDLE_NAME = "state"
        const val PRESET_DIR = "presets"
    }
}

class ExportPanel(private val activity: Activity, parent: ViewGroup, private val subject: Int) {
    private val root = LayoutInflater.from(activity).inflate(R.layout.panel_export, parent, false)
    private val copy = root.findViewById<Button>(R.id.copyButton)
    private val share = root.findViewById<Button>(R.id.shareButton)
    private val note = root.findViewById<TextView>(R.id.exportNote)
    private val preview = root.findViewById<TextView>(R.id.exportPreview)
    private var full: String? = null

    init {
        parent.addView(root)
        copy.setOnClickListener { full?.let { Actions.copy(activity, it) } }
        share.setOnClickListener { full?.let { Actions.shareText(activity, activity.getString(subject), it) } }
    }

    fun show(result: Checked<String>?) {
        val text = result?.valueOrNull()
        full = text
        val enabled = text != null
        copy.isEnabled = enabled
        share.isEnabled = enabled
        copy.alpha = if (enabled) 1f else 0.4f
        share.alpha = if (enabled) 1f else 0.4f
        val shortened = Fmt.preview(text.orEmpty())
        preview.text = shortened
        preview.visibility = if (enabled) View.VISIBLE else View.GONE
        val message = when {
            result is Checked.Bad -> result.message
            result == null -> activity.getString(R.string.fix_fields)
            shortened.length < text.orEmpty().length -> activity.getString(R.string.preview_cut)
            else -> null
        }
        note.text = message.orEmpty()
        note.visibility = if (message == null) View.GONE else View.VISIBLE
    }
}
