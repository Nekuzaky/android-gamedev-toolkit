package dev.nekuz.gamemath

import android.widget.LinearLayout

class TextureActivity : ToolActivity() {
    override val tool = Tool.TEXTURE

    private lateinit var width: TextField
    private lateinit var height: TextField
    private lateinit var format: ChoiceField
    private lateinit var mipmaps: ToggleField
    private lateinit var count: TextField
    private lateinit var results: LinearLayout

    override fun build() {
        width = form.text(content, "width", R.string.width, "1024", InputKind.WHOLE)
        height = form.text(content, "height", R.string.height, "1024", InputKind.WHOLE)
        format = form.choice(content, "format", R.string.format, TextureFormat.entries.map { it.label })
        mipmaps = form.toggle(content, "mipmaps", R.string.mipmaps, true)
        count = form.text(content, "count", R.string.texture_count, "1", InputKind.WHOLE)
        results = addResults(content)
    }

    override fun render() {
        val w = width.whole(1, TextureMemory.MAX_SIZE)
        val h = height.whole(1, TextureMemory.MAX_SIZE)
        val n = count.whole(1, TextureMemory.MAX_COUNT)
        val result = if (w != null && h != null && n != null) {
            TextureMemory.compute(w, h, TextureFormat.fromLabel(format.value), mipmaps.checked, n)
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
                setStatus(Fmt.bytes(value.totalBytes))
                rows.add(Row.Value(getString(R.string.texture_total), exact(value.totalBytes), highlighted = true))
                rows.add(Row.Value(getString(R.string.texture_each), exact(value.bytesPerTexture)))
                rows.add(Row.Value(getString(R.string.texture_top), exact(value.topLevelBytes)))
                rows.add(Row.Value(getString(R.string.texture_levels), value.mipLevels.toString()))
                value.notes.forEach { rows.add(Row.Note(it)) }
            }
        }
        RowViews.fill(results, rows)
    }

    private fun exact(bytes: Long) = getString(R.string.exact_bytes, Fmt.bytes(bytes), bytes.toString())
}
