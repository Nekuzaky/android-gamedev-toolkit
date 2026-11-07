package dev.nekuz.gamemath

import java.math.BigDecimal
import kotlin.math.roundToLong

data class AspectInfo(val ratioWidth: Long, val ratioHeight: Long, val decimal: Double, val megapixels: Double)

data class SafeArea(
    val safeWidth: Int,
    val safeHeight: Int,
    val anchorMinX: Double,
    val anchorMinY: Double,
    val anchorMaxX: Double,
    val anchorMaxY: Double,
)

data class CameraInfo(
    val orthographicSize: Double,
    val worldWidth: Double,
    val worldHeight: Double,
    val unitsPerPixel: Double,
)

data class TileGridInfo(val fullColumns: Long, val fullRows: Long, val visibleColumns: Long, val visibleRows: Long) {
    val fullTiles: Long get() = fullColumns * fullRows
    val visibleTiles: Long get() = visibleColumns * visibleRows
}

data class AtlasSize(
    val width: Long,
    val height: Long,
    val powerOfTwoWidth: Long,
    val powerOfTwoHeight: Long,
    val tiles: Long,
)

data class AtlasCapacity(val columns: Long, val rows: Long, val tiles: Long)

object ScreenMath {
    const val MAX_PIXELS = 1_000_000
    const val MIN_PPU = 0.001
    const val MAX_PPU = 100_000.0
    const val MIN_ZOOM = 0.01
    const val MAX_ZOOM = 1000.0
    const val MAX_TILES = 100_000
    const val MAX_PADDING = 4096
    const val MAX_TILE = 65536
    const val MAX_RATIO = 100_000
    private const val SIZE_RANGE = "A size must be between 1 and 1000000 pixels"

    fun gcd(a: Long, b: Long): Long {
        var x = if (a < 0) -a else a
        var y = if (b < 0) -b else b
        while (y != 0L) {
            val rest = x % y
            x = y
            y = rest
        }
        return x
    }

    fun aspect(width: Int, height: Int): Checked<AspectInfo> {
        if (!validSize(width) || !validSize(height)) return Checked.Bad(SIZE_RANGE)
        val divisor = gcd(width.toLong(), height.toLong())
        return Checked.Ok(
            AspectInfo(
                width / divisor,
                height / divisor,
                width.toDouble() / height.toDouble(),
                width.toDouble() * height.toDouble() / 1_000_000.0,
            ),
        )
    }

    fun heightFor(width: Int, ratioWidth: Int, ratioHeight: Int): Checked<Long> {
        if (!validSize(width)) return Checked.Bad(SIZE_RANGE)
        if (ratioWidth !in 1..MAX_RATIO || ratioHeight !in 1..MAX_RATIO) {
            return Checked.Bad("A ratio must be between 1 and $MAX_RATIO")
        }
        return Checked.Ok((width.toDouble() * ratioHeight.toDouble() / ratioWidth.toDouble()).roundToLong())
    }

    fun safeArea(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): Checked<SafeArea> {
        if (!validSize(width) || !validSize(height)) return Checked.Bad(SIZE_RANGE)
        if (left < 0 || top < 0 || right < 0 || bottom < 0) return Checked.Bad("Insets cannot be negative")
        val safeWidth = width.toLong() - left.toLong() - right.toLong()
        val safeHeight = height.toLong() - top.toLong() - bottom.toLong()
        if (safeWidth <= 0 || safeHeight <= 0) return Checked.Bad("The insets leave no safe area")
        return Checked.Ok(
            SafeArea(
                safeWidth.toInt(),
                safeHeight.toInt(),
                left.toDouble() / width,
                bottom.toDouble() / height,
                (width - right).toDouble() / width,
                (height - top).toDouble() / height,
            ),
        )
    }

    fun camera(width: Int, height: Int, pixelsPerUnit: Double, zoom: Double): Checked<CameraInfo> {
        if (!validSize(width) || !validSize(height)) return Checked.Bad(SIZE_RANGE)
        if (!pixelsPerUnit.isFinite() || pixelsPerUnit < MIN_PPU || pixelsPerUnit > MAX_PPU) {
            return Checked.Bad("Pixels per unit must be between ${Fmt.number(MIN_PPU)} and ${Fmt.number(MAX_PPU)}")
        }
        if (!validZoom(zoom)) return Checked.Bad(ZOOM_RANGE)
        val scale = pixelsPerUnit * zoom
        val worldHeight = height.toDouble() / scale
        val worldWidth = width.toDouble() / scale
        return Checked.Ok(CameraInfo(worldHeight / 2.0, worldWidth, worldHeight, 1.0 / scale))
    }

    fun tileGrid(width: Int, height: Int, tileSize: Int, zoom: Double): Checked<TileGridInfo> {
        if (!validSize(width) || !validSize(height)) return Checked.Bad(SIZE_RANGE)
        if (tileSize !in 1..MAX_TILE) return Checked.Bad("Tile size must be between 1 and $MAX_TILE")
        if (!validZoom(zoom)) return Checked.Bad(ZOOM_RANGE)
        val pixels = BigDecimal.valueOf(zoom).multiply(BigDecimal(tileSize))
        return Checked.Ok(
            TileGridInfo(
                wholeTiles(width, pixels),
                wholeTiles(height, pixels),
                tilesToDraw(width, pixels),
                tilesToDraw(height, pixels),
            ),
        )
    }

    fun atlasSize(
        columns: Int,
        rows: Int,
        tileWidth: Int,
        tileHeight: Int,
        padding: Int,
        margin: Int,
    ): Checked<AtlasSize> {
        if (columns !in 1..MAX_TILES || rows !in 1..MAX_TILES) {
            return Checked.Bad("Columns and rows must be between 1 and $MAX_TILES")
        }
        atlasProblem(tileWidth, tileHeight, padding, margin)?.let { return Checked.Bad(it) }
        val width = axis(columns, tileWidth, padding, margin)
        val height = axis(rows, tileHeight, padding, margin)
        return Checked.Ok(
            AtlasSize(width, height, nextPowerOfTwo(width), nextPowerOfTwo(height), columns.toLong() * rows.toLong()),
        )
    }

    fun atlasCapacity(
        width: Int,
        height: Int,
        tileWidth: Int,
        tileHeight: Int,
        padding: Int,
        margin: Int,
    ): Checked<AtlasCapacity> {
        if (!validSize(width) || !validSize(height)) return Checked.Bad(SIZE_RANGE)
        atlasProblem(tileWidth, tileHeight, padding, margin)?.let { return Checked.Bad(it) }
        val columns = fit(width, tileWidth, padding, margin)
        val rows = fit(height, tileHeight, padding, margin)
        return Checked.Ok(AtlasCapacity(columns, rows, columns * rows))
    }

    fun nextPowerOfTwo(value: Long): Long {
        if (value <= 1) return 1
        val high = java.lang.Long.highestOneBit(value)
        return if (high == value) value else high * 2
    }

    private fun wholeTiles(size: Int, pixels: BigDecimal): Long =
        BigDecimal(size).divideToIntegralValue(pixels).toLong()

    private fun tilesToDraw(size: Int, pixels: BigDecimal): Long {
        val whole = wholeTiles(size, pixels)
        return if (BigDecimal(size).remainder(pixels).signum() == 0) whole else whole + 1
    }

    private fun axis(count: Int, tile: Int, padding: Int, margin: Int): Long =
        count.toLong() * tile + (count.toLong() - 1) * padding + 2L * margin

    private fun fit(size: Int, tile: Int, padding: Int, margin: Int): Long {
        val room = size.toLong() - 2L * margin
        if (room < tile) return 0
        return (room + padding) / (tile.toLong() + padding)
    }

    private fun atlasProblem(tileWidth: Int, tileHeight: Int, padding: Int, margin: Int): String? = when {
        tileWidth !in 1..MAX_TILE || tileHeight !in 1..MAX_TILE -> "Tile size must be between 1 and $MAX_TILE"
        padding !in 0..MAX_PADDING || margin !in 0..MAX_PADDING ->
            "Padding and margin must be between 0 and $MAX_PADDING"
        else -> null
    }

    private fun validSize(value: Int) = value in 1..MAX_PIXELS

    private fun validZoom(zoom: Double) = zoom.isFinite() && zoom >= MIN_ZOOM && zoom <= MAX_ZOOM

    private val ZOOM_RANGE = "Zoom must be between ${Fmt.number(MIN_ZOOM)} and ${Fmt.number(MAX_ZOOM)}"
}
