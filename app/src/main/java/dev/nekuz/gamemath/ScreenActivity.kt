package dev.nekuz.gamemath

import android.view.ViewGroup
import android.widget.LinearLayout

class ScreenActivity : ToolActivity() {
    override val tool = Tool.SCREEN

    private lateinit var width: TextField
    private lateinit var height: TextField
    private lateinit var targetWidth: TextField
    private lateinit var ratioW: TextField
    private lateinit var ratioH: TextField
    private lateinit var resolutionResults: LinearLayout
    private lateinit var left: TextField
    private lateinit var top: TextField
    private lateinit var right: TextField
    private lateinit var bottom: TextField
    private lateinit var safeResults: LinearLayout
    private lateinit var ppu: TextField
    private lateinit var zoom: TextField
    private lateinit var cameraResults: LinearLayout
    private lateinit var tileSize: TextField
    private lateinit var tileResults: LinearLayout
    private lateinit var columns: TextField
    private lateinit var rowsCount: TextField
    private lateinit var tileW: TextField
    private lateinit var tileH: TextField
    private lateinit var padding: TextField
    private lateinit var margin: TextField
    private lateinit var atlasW: TextField
    private lateinit var atlasH: TextField
    private lateinit var atlasResults: LinearLayout

    override fun build() {
        addHeader(content, R.string.screen_resolution)
        width = whole(content, "width", R.string.width, "1920")
        height = whole(content, "height", R.string.height, "1080")
        targetWidth = whole(content, "target_width", R.string.screen_target_width, "1280")
        ratioW = whole(content, "ratio_w", R.string.screen_ratio_w, "16")
        ratioH = whole(content, "ratio_h", R.string.screen_ratio_h, "9")
        resolutionResults = addResults(content)
        addHeader(content, R.string.screen_safe)
        left = whole(content, "left", R.string.inset_left, "0")
        top = whole(content, "top", R.string.inset_top, "0")
        right = whole(content, "right", R.string.inset_right, "0")
        bottom = whole(content, "bottom", R.string.inset_bottom, "0")
        safeResults = addResults(content)
        addHeader(content, R.string.screen_camera)
        ppu = form.text(content, "ppu", R.string.camera_ppu, "100", InputKind.DECIMAL)
        zoom = form.text(content, "zoom", R.string.camera_zoom, "1", InputKind.DECIMAL)
        cameraResults = addResults(content)
        addHeader(content, R.string.screen_tiles)
        tileSize = whole(content, "tile_size", R.string.tile_size, "32")
        tileResults = addResults(content)
        addHeader(content, R.string.screen_atlas)
        columns = whole(content, "columns", R.string.atlas_columns, "16")
        rowsCount = whole(content, "rows", R.string.atlas_rows, "16")
        tileW = whole(content, "tile_w", R.string.atlas_tile_w, "32")
        tileH = whole(content, "tile_h", R.string.atlas_tile_h, "32")
        padding = whole(content, "padding", R.string.atlas_padding, "2")
        margin = whole(content, "margin", R.string.atlas_margin, "1")
        atlasW = whole(content, "atlas_w", R.string.atlas_width, "1024")
        atlasH = whole(content, "atlas_h", R.string.atlas_height, "1024")
        atlasResults = addResults(content)
    }

    override fun render() {
        val w = width.whole(1, ScreenMath.MAX_PIXELS)
        val h = height.whole(1, ScreenMath.MAX_PIXELS)
        val failed = renderResolution(w, h) or renderSafe(w, h)
        val zoomValue = zoom.decimal(ScreenMath.MIN_ZOOM, ScreenMath.MAX_ZOOM)
        val failedCamera = renderCamera(w, h, zoomValue) or renderTiles(w, h, zoomValue)
        val failedAtlas = renderAtlas()
        setStatus(
            if (w == null || h == null || failed || failedCamera || failedAtlas) {
                getString(R.string.fix_fields)
            } else {
                getString(R.string.pair_x, w.toString(), h.toString())
            },
        )
    }

    private fun whole(parent: ViewGroup, key: String, label: Int, default: String) =
        form.text(parent, key, label, default, InputKind.WHOLE)

    private fun renderResolution(w: Int?, h: Int?): Boolean {
        val rows = ArrayList<Row>()
        val aspect = if (w != null && h != null) ScreenMath.aspect(w, h) else null
        var failed = aspect == null
        when (aspect) {
            null -> Unit
            is Checked.Bad -> {
                failed = true
                rows.add(Row.Note(aspect.message))
            }
            is Checked.Ok -> {
                val value = aspect.value
                rows.add(
                    Row.Value(
                        getString(R.string.screen_aspect),
                        getString(R.string.pair_ratio, value.ratioWidth, value.ratioHeight),
                    ),
                )
                rows.add(Row.Value(getString(R.string.screen_decimal), Fmt.number(value.decimal)))
                rows.add(Row.Value(getString(R.string.screen_megapixels), Fmt.number(value.megapixels)))
            }
        }
        val target = targetWidth.whole(1, ScreenMath.MAX_PIXELS)
        val rw = ratioW.whole(1, ScreenMath.MAX_RATIO)
        val rh = ratioH.whole(1, ScreenMath.MAX_RATIO)
        if (target != null && rw != null && rh != null) {
            when (val computed = ScreenMath.heightFor(target, rw, rh)) {
                is Checked.Ok -> rows.add(Row.Value(getString(R.string.screen_height_for), computed.value.toString()))
                is Checked.Bad -> {
                    failed = true
                    rows.add(Row.Note(computed.message))
                }
            }
        } else {
            failed = true
        }
        RowViews.fill(resolutionResults, rows)
        return failed
    }

    private fun renderSafe(w: Int?, h: Int?): Boolean {
        val l = left.whole(0, ScreenMath.MAX_PIXELS)
        val t = top.whole(0, ScreenMath.MAX_PIXELS)
        val r = right.whole(0, ScreenMath.MAX_PIXELS)
        val b = bottom.whole(0, ScreenMath.MAX_PIXELS)
        val rows = ArrayList<Row>()
        var failed = true
        if (w != null && h != null && l != null && t != null && r != null && b != null) {
            when (val safe = ScreenMath.safeArea(w, h, l, t, r, b)) {
                is Checked.Bad -> rows.add(Row.Note(safe.message))
                is Checked.Ok -> {
                    failed = false
                    val value = safe.value
                    rows.add(
                        Row.Value(
                            getString(R.string.safe_size),
                            getString(R.string.pair_x, value.safeWidth.toString(), value.safeHeight.toString()),
                        ),
                    )
                    rows.add(
                        Row.Value(
                            getString(R.string.safe_anchor_min),
                            getString(R.string.pair_vector, Fmt.number(value.anchorMinX), Fmt.number(value.anchorMinY)),
                        ),
                    )
                    rows.add(
                        Row.Value(
                            getString(R.string.safe_anchor_max),
                            getString(R.string.pair_vector, Fmt.number(value.anchorMaxX), Fmt.number(value.anchorMaxY)),
                        ),
                    )
                }
            }
        }
        RowViews.fill(safeResults, rows)
        return failed
    }

    private fun renderCamera(w: Int?, h: Int?, zoomValue: Double?): Boolean {
        val ppuValue = ppu.decimal(ScreenMath.MIN_PPU, ScreenMath.MAX_PPU)
        val rows = ArrayList<Row>()
        var failed = true
        if (w != null && h != null && ppuValue != null && zoomValue != null) {
            when (val camera = ScreenMath.camera(w, h, ppuValue, zoomValue)) {
                is Checked.Bad -> rows.add(Row.Note(camera.message))
                is Checked.Ok -> {
                    failed = false
                    val value = camera.value
                    rows.add(Row.Value(getString(R.string.camera_size), Fmt.number(value.orthographicSize)))
                    rows.add(
                        Row.Value(
                            getString(R.string.camera_world),
                            getString(R.string.pair_x, Fmt.number(value.worldWidth), Fmt.number(value.worldHeight)),
                        ),
                    )
                    rows.add(Row.Value(getString(R.string.camera_unit), Fmt.number(value.unitsPerPixel, 6)))
                }
            }
        }
        RowViews.fill(cameraResults, rows)
        return failed
    }

    private fun renderTiles(w: Int?, h: Int?, zoomValue: Double?): Boolean {
        val size = tileSize.whole(1, ScreenMath.MAX_TILE)
        val rows = ArrayList<Row>()
        var failed = true
        if (w != null && h != null && size != null && zoomValue != null) {
            when (val grid = ScreenMath.tileGrid(w, h, size, zoomValue)) {
                is Checked.Bad -> rows.add(Row.Note(grid.message))
                is Checked.Ok -> {
                    failed = false
                    val value = grid.value
                    rows.add(
                        Row.Value(
                            getString(R.string.tile_full),
                            getString(R.string.tiles_grid, value.fullColumns, value.fullRows, value.fullTiles),
                        ),
                    )
                    rows.add(
                        Row.Value(
                            getString(R.string.tile_visible),
                            getString(R.string.tiles_grid, value.visibleColumns, value.visibleRows, value.visibleTiles),
                        ),
                    )
                }
            }
        }
        RowViews.fill(tileResults, rows)
        return failed
    }

    private fun renderAtlas(): Boolean {
        val c = columns.whole(1, ScreenMath.MAX_TILES)
        val r = rowsCount.whole(1, ScreenMath.MAX_TILES)
        val tw = tileW.whole(1, ScreenMath.MAX_TILE)
        val th = tileH.whole(1, ScreenMath.MAX_TILE)
        val p = padding.whole(0, ScreenMath.MAX_PADDING)
        val m = margin.whole(0, ScreenMath.MAX_PADDING)
        val aw = atlasW.whole(1, ScreenMath.MAX_PIXELS)
        val ah = atlasH.whole(1, ScreenMath.MAX_PIXELS)
        val rows = ArrayList<Row>()
        var failed = true
        if (tw != null && th != null && p != null && m != null) {
            failed = false
            if (c != null && r != null) {
                when (val size = ScreenMath.atlasSize(c, r, tw, th, p, m)) {
                    is Checked.Bad -> {
                        failed = true
                        rows.add(Row.Note(size.message))
                    }
                    is Checked.Ok -> {
                        val value = size.value
                        rows.add(
                            Row.Value(
                                getString(R.string.atlas_size),
                                getString(R.string.pair_x, value.width.toString(), value.height.toString()),
                            ),
                        )
                        rows.add(
                            Row.Value(
                                getString(R.string.atlas_pot),
                                getString(R.string.pair_x, value.powerOfTwoWidth.toString(), value.powerOfTwoHeight.toString()),
                            ),
                        )
                        rows.add(Row.Value(getString(R.string.atlas_tiles), value.tiles.toString()))
                    }
                }
            } else {
                failed = true
            }
            if (aw != null && ah != null) {
                when (val capacity = ScreenMath.atlasCapacity(aw, ah, tw, th, p, m)) {
                    is Checked.Bad -> {
                        failed = true
                        rows.add(Row.Note(capacity.message))
                    }
                    is Checked.Ok -> {
                        val value = capacity.value
                        rows.add(
                            Row.Value(
                                getString(R.string.atlas_capacity),
                                getString(R.string.tiles_grid, value.columns, value.rows, value.tiles),
                            ),
                        )
                    }
                }
            } else {
                failed = true
            }
        }
        RowViews.fill(atlasResults, rows)
        return failed
    }
}
