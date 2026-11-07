package dev.nekuz.gamemath

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object JsonExport {
    private const val INDENT = 2
    private const val NAME_LENGTH = 64

    fun progression(name: String?, curveLabel: String, values: DoubleArray, type: NumberType): Checked<String> {
        if (values.isEmpty()) return Checked.Bad("There are no values to export")
        return guarded {
            val array = JSONArray()
            for (index in values.indices) {
                val value = values[index]
                when (val literal = CSharp.numberLiteral(value, type)) {
                    is Checked.Bad -> return Checked.Bad("Level ${index + 1} ${literal.message}. Choose a larger type.")
                    is Checked.Ok -> when (type) {
                        NumberType.INT, NumberType.LONG -> array.put(literal.value.toLong())
                        NumberType.FLOAT, NumberType.DOUBLE -> array.put(value)
                    }
                }
            }
            JSONObject()
                .put("name", ForeignText.clean(name, NAME_LENGTH))
                .put("curve", curveLabel)
                .put("firstLevel", 1)
                .put("values", array)
                .toString(INDENT)
        }
    }

    fun loot(name: String?, items: List<LootItem>, pity: Pity?): Checked<String> {
        if (items.isEmpty()) return Checked.Bad("There are no items to export")
        if (items.any { !it.weight.isFinite() }) return Checked.Bad("A weight is not a finite number")
        if (pity != null && pity.itemIndex !in items.indices) return Checked.Bad("The pity item does not exist")
        return guarded {
            val total = items.sumOf { it.weight }
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject()
                        .put("name", ForeignText.clean(item.name, NAME_LENGTH))
                        .put("weight", item.weight)
                        .put("probability", if (total > 0.0) item.weight / total else 0.0),
                )
            }
            val root = JSONObject()
                .put("name", ForeignText.clean(name, NAME_LENGTH))
                .put("totalWeight", total)
                .put("items", array)
            if (pity != null) {
                root.put("pity", JSONObject().put("itemIndex", pity.itemIndex).put("after", pity.after))
            }
            root.toString(INDENT)
        }
    }

    private inline fun guarded(build: () -> String): Checked<String> =
        try {
            Checked.Ok(build())
        } catch (e: JSONException) {
            Checked.Bad("Could not build the JSON")
        }
}
