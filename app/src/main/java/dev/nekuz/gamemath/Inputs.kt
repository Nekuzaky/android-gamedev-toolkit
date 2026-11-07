package dev.nekuz.gamemath

sealed class Checked<out T> {
    data class Ok<out T>(val value: T) : Checked<T>()

    data class Bad(val message: String) : Checked<Nothing>()

    fun valueOrNull(): T? = (this as? Ok)?.value
}

object Inputs {
    const val MAX_LENGTH = 40
    const val REQUIRED = "Enter a value"
    const val NOT_A_NUMBER = "Enter a plain number"
    const val NOT_A_WHOLE_NUMBER = "Enter a whole number"
    const val TOO_LONG = "This value is too long"
    const val AMBIGUOUS_COMMA = "Ambiguous comma: use a dot for decimals and no thousands separator"

    private val DECIMAL = Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")
    private val INTEGER = Regex("[+-]?\\d+")
    private val THOUSANDS = Regex("[+-]?\\d+,\\d{3}([eE].*)?")

    fun double(raw: String?, min: Double, max: Double): Checked<Double> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Checked.Bad(REQUIRED)
        if (text.length > MAX_LENGTH) return Checked.Bad(TOO_LONG)
        val normal = decimalPoint(text)
            ?: return Checked.Bad(if (DECIMAL.matches(text.replace(",", ""))) AMBIGUOUS_COMMA else NOT_A_NUMBER)
        if (!DECIMAL.matches(normal)) return Checked.Bad(NOT_A_NUMBER)
        val value = normal.toDoubleOrNull() ?: return Checked.Bad(NOT_A_NUMBER)
        if (!value.isFinite()) return Checked.Bad(range(min, max))
        val clean = value + 0.0
        if (clean < min || clean > max) return Checked.Bad(range(min, max))
        return Checked.Ok(clean)
    }

    fun long(raw: String?, min: Long, max: Long): Checked<Long> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Checked.Bad(REQUIRED)
        if (text.length > MAX_LENGTH) return Checked.Bad(TOO_LONG)
        if (!INTEGER.matches(text)) return Checked.Bad(NOT_A_WHOLE_NUMBER)
        val value = text.toLongOrNull() ?: return Checked.Bad(rangeWhole(min, max))
        if (value < min || value > max) return Checked.Bad(rangeWhole(min, max))
        return Checked.Ok(value)
    }

    fun int(raw: String?, min: Int, max: Int): Checked<Int> =
        when (val parsed = long(raw, min.toLong(), max.toLong())) {
            is Checked.Ok -> Checked.Ok(parsed.value.toInt())
            is Checked.Bad -> parsed
        }

    fun optionalInt(raw: String?, min: Int, max: Int): Checked<Int?> {
        if (raw.isNullOrBlank()) return Checked.Ok(null)
        return when (val parsed = int(raw, min, max)) {
            is Checked.Ok -> Checked.Ok(parsed.value)
            is Checked.Bad -> parsed
        }
    }

    private fun decimalPoint(text: String): String? {
        val commas = text.count { it == ',' }
        if (commas == 0) return text
        if (commas > 1 || text.contains('.') || THOUSANDS.matches(text)) return null
        return text.replace(',', '.')
    }

    private fun range(min: Double, max: Double) = "Must be between ${Fmt.number(min)} and ${Fmt.number(max)}"

    private fun rangeWhole(min: Long, max: Long) = "Must be a whole number between $min and $max"
}
