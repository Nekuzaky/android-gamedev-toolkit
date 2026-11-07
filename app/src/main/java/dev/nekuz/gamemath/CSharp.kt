package dev.nekuz.gamemath

enum class NumberType(val label: String, val keyword: String) {
    INT("int (rounded)", "int"),
    LONG("long (rounded)", "long"),
    FLOAT("float", "float"),
    DOUBLE("double", "double"),
    ;

    companion object {
        fun fromLabel(label: String?): NumberType = entries.firstOrNull { it.label == label } ?: LONG
    }
}

object CSharp {
    const val MAX_IDENTIFIER = 64
    private const val SCAN_LIMIT = 512
    private const val VALUES_PER_LINE = 8
    private const val TWO_POW_63 = 9.223372036854775808E18
    private val CLASS_RESERVED = setOf("Values", "Item", "Entry", "Entries", "TotalWeight", "PityAfter", "PityItem")
    private val KEYWORDS = setOf(
        "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked", "class", "const",
        "continue", "decimal", "default", "delegate", "do", "double", "else", "enum", "event", "explicit", "extern",
        "false", "finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in", "int", "interface",
        "internal", "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out", "override",
        "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte", "sealed", "short", "sizeof",
        "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true", "try", "typeof", "uint",
        "ulong", "unchecked", "unsafe", "ushort", "using", "virtual", "void", "volatile", "while",
    )

    fun identifier(raw: String?, fallback: String): String {
        val text = sanitize(raw)
        if (text.isNotEmpty()) return text
        return sanitize(fallback).ifEmpty { "Generated" }
    }

    fun uniqueIdentifiers(names: List<String>, fallback: String, reserved: Set<String> = emptySet()): List<String> {
        val taken = HashSet(reserved)
        return names.mapIndexed { index, name ->
            val first = identifier(name, "$fallback${index + 1}")
            var candidate = first
            var suffix = 2
            while (!taken.add(candidate)) {
                candidate = "${first}_$suffix"
                suffix++
            }
            candidate
        }
    }

    fun stringLiteral(raw: String): String {
        val text = StringBuilder(raw.length + 2)
        text.append('"')
        for (char in raw) {
            when {
                char == '"' -> text.append("\\\"")
                char == '\\' -> text.append("\\\\")
                char == '\n' -> text.append("\\n")
                char == '\r' -> text.append("\\r")
                char == '\t' -> text.append("\\t")
                char.code in 0x20..0x7E -> text.append(char)
                else -> text.append("\\u").append(char.code.toString(16).uppercase().padStart(4, '0'))
            }
        }
        text.append('"')
        return text.toString()
    }

    fun numberLiteral(value: Double, type: NumberType): Checked<String> {
        if (!value.isFinite()) return Checked.Bad("is not a finite number")
        return when (type) {
            NumberType.INT -> {
                val whole = Math.round(value)
                if (whole < Int.MIN_VALUE || whole > Int.MAX_VALUE) {
                    Checked.Bad("does not fit in a C# int")
                } else {
                    Checked.Ok(whole.toString())
                }
            }
            NumberType.LONG -> {
                if (value < -TWO_POW_63 || value >= TWO_POW_63) {
                    Checked.Bad("does not fit in a C# long")
                } else {
                    Checked.Ok(Math.round(value).toString())
                }
            }
            NumberType.FLOAT -> {
                val single = value.toFloat()
                if (single.isInfinite() || (single == 0f && value != 0.0)) {
                    Checked.Bad("does not fit in a C# float")
                } else {
                    Checked.Ok("${single}f")
                }
            }
            NumberType.DOUBLE -> Checked.Ok(value.toString())
        }
    }

    fun progression(name: String?, values: DoubleArray, type: NumberType): Checked<String> {
        if (values.isEmpty()) return Checked.Bad("There are no values to export")
        val text = StringBuilder()
        text.append("public static class ").append(className(name, "ProgressionCurve")).append("\n{\n")
        text.append("    public static readonly ").append(type.keyword).append("[] Values =\n    {\n")
        for (index in values.indices) {
            val literal = when (val checked = numberLiteral(values[index], type)) {
                is Checked.Ok -> checked.value
                is Checked.Bad -> return Checked.Bad("Level ${index + 1} ${checked.message}. Choose a larger type.")
            }
            if (index % VALUES_PER_LINE == 0) text.append("        ")
            text.append(literal).append(',')
            text.append(if (index % VALUES_PER_LINE == VALUES_PER_LINE - 1 || index == values.lastIndex) '\n' else ' ')
        }
        text.append("    };\n}\n")
        return Checked.Ok(text.toString())
    }

    fun loot(name: String?, items: List<LootItem>, pity: Pity?): Checked<String> {
        if (items.isEmpty()) return Checked.Bad("There are no items to export")
        if (items.any { !it.weight.isFinite() }) return Checked.Bad("A weight is not a finite number")
        if (pity != null && pity.itemIndex !in items.indices) return Checked.Bad("The pity item does not exist")
        val members = uniqueIdentifiers(items.map { it.name }, "Item", setOf("Item"))
        val text = StringBuilder()
        text.append("public static class ").append(className(name, "LootTable")).append("\n{\n")
        text.append("    public enum Item\n    {\n")
        members.forEach { text.append("        ").append(it).append(",\n") }
        text.append("    }\n\n")
        text.append("    public readonly struct Entry\n    {\n")
        text.append("        public readonly Item Id;\n")
        text.append("        public readonly string Name;\n")
        text.append("        public readonly double Weight;\n\n")
        text.append("        public Entry(Item id, string name, double weight)\n        {\n")
        text.append("            Id = id;\n            Name = name;\n            Weight = weight;\n")
        text.append("        }\n    }\n\n")
        text.append("    public const double TotalWeight = ").append(items.sumOf { it.weight }).append(";\n")
        if (pity != null) {
            text.append("    public const int PityAfter = ").append(pity.after).append(";\n")
            text.append("    public const Item PityItem = Item.").append(members[pity.itemIndex]).append(";\n")
        }
        text.append("\n    public static readonly Entry[] Entries =\n    {\n")
        items.forEachIndexed { index, item ->
            text.append("        new Entry(Item.").append(members[index]).append(", ")
                .append(stringLiteral(item.name)).append(", ").append(item.weight).append("),\n")
        }
        text.append("    };\n}\n")
        return Checked.Ok(text.toString())
    }

    private fun className(name: String?, fallback: String): String {
        val candidate = identifier(name, fallback)
        return if (candidate in CLASS_RESERVED) "${candidate}Data" else candidate
    }

    private fun sanitize(raw: String?): String {
        if (raw == null) return ""
        val text = StringBuilder()
        val end = minOf(raw.length, SCAN_LIMIT)
        for (index in 0 until end) {
            val char = raw[index]
            val ascii = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'
            when {
                ascii -> text.append(char)
                text.isNotEmpty() && text.last() != '_' -> text.append('_')
            }
            if (text.length >= MAX_IDENTIFIER) break
        }
        val trimmed = text.toString().trimEnd('_')
        if (trimmed.isEmpty()) return ""
        if (trimmed.first() in '0'..'9' || trimmed in KEYWORDS) return "_$trimmed"
        return trimmed
    }
}
