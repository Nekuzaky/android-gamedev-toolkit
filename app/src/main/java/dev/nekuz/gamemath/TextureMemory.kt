package dev.nekuz.gamemath

enum class TextureFormat(val label: String, val blockWidth: Int, val blockHeight: Int, val blockBytes: Int) {
    RGBA32("RGBA32", 1, 1, 4),
    RGB565("RGB565", 1, 1, 2),
    BC1("DXT1 / BC1", 4, 4, 8),
    BC3("DXT5 / BC3", 4, 4, 16),
    BC7("BC7", 4, 4, 16),
    ASTC_4("ASTC 4x4", 4, 4, 16),
    ASTC_6("ASTC 6x6", 6, 6, 16),
    ASTC_8("ASTC 8x8", 8, 8, 16),
    ETC2_RGB("ETC2 RGB", 4, 4, 8),
    ETC2_RGBA("ETC2 RGBA8", 4, 4, 16),
    ;

    val compressed: Boolean get() = blockWidth > 1

    companion object {
        fun fromLabel(label: String?): TextureFormat = entries.firstOrNull { it.label == label } ?: RGBA32
    }
}

data class TextureResult(
    val topLevelBytes: Long,
    val bytesPerTexture: Long,
    val totalBytes: Long,
    val mipLevels: Int,
    val notes: List<String>,
)

object TextureMemory {
    const val MAX_SIZE = 65536
    const val MAX_COUNT = 100_000

    fun levelBytes(width: Int, height: Int, format: TextureFormat): Long {
        val columns = (width.toLong() + format.blockWidth - 1) / format.blockWidth
        val rows = (height.toLong() + format.blockHeight - 1) / format.blockHeight
        return Math.multiplyExact(Math.multiplyExact(columns, rows), format.blockBytes.toLong())
    }

    fun mipLevels(width: Int, height: Int): Int {
        val largest = maxOf(width, height, 1)
        return 32 - Integer.numberOfLeadingZeros(largest)
    }

    fun compute(width: Int, height: Int, format: TextureFormat, mipmaps: Boolean, count: Int): Checked<TextureResult> {
        if (width < 1 || width > MAX_SIZE || height < 1 || height > MAX_SIZE) {
            return Checked.Bad("Width and height must be between 1 and $MAX_SIZE")
        }
        if (count < 1 || count > MAX_COUNT) return Checked.Bad("Count must be between 1 and $MAX_COUNT")
        return try {
            val levels = if (mipmaps) mipLevels(width, height) else 1
            var perTexture = 0L
            var levelWidth = width
            var levelHeight = height
            repeat(levels) {
                perTexture = Math.addExact(perTexture, levelBytes(levelWidth, levelHeight, format))
                levelWidth = (levelWidth / 2).coerceAtLeast(1)
                levelHeight = (levelHeight / 2).coerceAtLeast(1)
            }
            Checked.Ok(
                TextureResult(
                    topLevelBytes = levelBytes(width, height, format),
                    bytesPerTexture = perTexture,
                    totalBytes = Math.multiplyExact(perTexture, count.toLong()),
                    mipLevels = levels,
                    notes = notes(width, height, format, mipmaps),
                ),
            )
        } catch (e: ArithmeticException) {
            Checked.Bad("The total is too large to compute")
        }
    }

    private fun notes(width: Int, height: Int, format: TextureFormat, mipmaps: Boolean): List<String> {
        val notes = ArrayList<String>()
        val powerOfTwo = Integer.bitCount(width) == 1 && Integer.bitCount(height) == 1
        if (!powerOfTwo && mipmaps) {
            notes.add("Not a power of two: some GPUs and import settings handle mipmaps badly at this size.")
        }
        if (format.compressed && (width % format.blockWidth != 0 || height % format.blockHeight != 0)) {
            notes.add(
                "Not a multiple of the ${format.blockWidth}x${format.blockHeight} block: " +
                    "the last row and column are padded, and Unity may refuse to compress it.",
            )
        }
        return notes
    }
}
