package dev.nekuz.gamemath

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class Preset(val name: String, val fields: Map<String, String>, val rows: List<List<String>>)

sealed class PresetLoad {
    data class Loaded(val presets: List<Preset>) : PresetLoad()

    data object Damaged : PresetLoad()

    data object Unreadable : PresetLoad()
}

enum class SaveOutcome { SAVED, FULL, FAILED }

object PresetCodec {
    const val MAX_PRESETS = 100
    const val MAX_FIELDS = 48
    const val MAX_ROWS = 100
    const val MAX_COLUMNS = 4
    const val MAX_VALUE = 64
    const val MAX_KEY = 32
    const val MAX_NAME = 64
    private const val VERSION = 1

    fun cleanName(raw: String?): String = ForeignText.clean(raw, MAX_NAME)

    fun encode(presets: List<Preset>): String? = try {
        val array = JSONArray()
        presets.take(MAX_PRESETS).forEach { array.put(toJson(it)) }
        JSONObject().put("version", VERSION).put("presets", array).toString()
    } catch (e: JSONException) {
        null
    }

    fun decode(text: String?): List<Preset> = decodeOrNull(text).orEmpty()

    fun decodeOrNull(text: String?): List<Preset>? {
        if (text.isNullOrEmpty()) return null
        return try {
            val array = JSONObject(text).opt("presets") as? JSONArray ?: return null
            val presets = ArrayList<Preset>()
            for (index in 0 until minOf(array.length(), MAX_PRESETS)) {
                val preset = (array.opt(index) as? JSONObject)?.let(::fromJson) ?: continue
                if (presets.none { it.name == preset.name }) presets.add(preset)
            }
            presets
        } catch (e: JSONException) {
            null
        } catch (e: StackOverflowError) {
            null
        }
    }

    fun encodeOne(preset: Preset): String? = try {
        toJson(preset).toString()
    } catch (e: JSONException) {
        null
    }

    fun decodeOne(text: String?): Preset? {
        if (text.isNullOrEmpty()) return null
        return try {
            fromJson(JSONObject(text))
        } catch (e: JSONException) {
            null
        } catch (e: StackOverflowError) {
            null
        }
    }

    fun upsert(presets: List<Preset>, preset: Preset): List<Preset>? {
        val replaced = presets.indexOfFirst { it.name == preset.name }
        if (replaced >= 0) return presets.toMutableList().also { it[replaced] = preset }
        if (presets.size >= MAX_PRESETS) return null
        return presets + preset
    }

    fun remove(presets: List<Preset>, name: String): List<Preset> = presets.filter { it.name != name }

    private fun toJson(preset: Preset): JSONObject {
        val fields = JSONObject()
        preset.fields.entries.take(MAX_FIELDS).forEach { (key, value) -> fields.put(key, value) }
        val rows = JSONArray()
        preset.rows.take(MAX_ROWS).forEach { row ->
            val columns = JSONArray()
            row.take(MAX_COLUMNS).forEach { columns.put(it) }
            rows.put(columns)
        }
        return JSONObject().put("name", preset.name).put("fields", fields).put("rows", rows)
    }

    private fun fromJson(json: JSONObject): Preset? {
        val name = cleanName(json.opt("name") as? String)
        if (name.isEmpty()) return null
        val fields = LinkedHashMap<String, String>()
        val fieldJson = json.opt("fields") as? JSONObject
        if (fieldJson != null) {
            val keys = fieldJson.keys()
            var scanned = 0
            while (keys.hasNext() && scanned < MAX_FIELDS * 4 && fields.size < MAX_FIELDS) {
                scanned++
                val key = keys.next()
                val value = fieldJson.opt(key) as? String ?: continue
                if (key.length <= MAX_KEY) fields[key] = ForeignText.clean(value, MAX_VALUE)
            }
        }
        val rows = ArrayList<List<String>>()
        val rowJson = json.opt("rows") as? JSONArray
        if (rowJson != null) {
            for (index in 0 until minOf(rowJson.length(), MAX_ROWS)) {
                val row = rowJson.opt(index) as? JSONArray ?: continue
                val columns = ArrayList<String>()
                for (column in 0 until minOf(row.length(), MAX_COLUMNS)) {
                    columns.add(ForeignText.clean(row.opt(column) as? String, MAX_VALUE))
                }
                rows.add(columns)
            }
        }
        return Preset(name, fields, rows)
    }
}

object PresetFile {
    const val MAX_BYTES = 1_048_576
    const val ASIDE_SUFFIX = ".damaged"
    private const val CHUNK = 8192

    fun read(file: File): String? {
        return try {
            if (!file.isFile) return null
            readBounded(file)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    fun inspect(file: File): PresetLoad {
        return try {
            if (!file.exists()) return PresetLoad.Loaded(emptyList())
            if (!file.isFile) return PresetLoad.Unreadable
            val presets = readBounded(file)?.let(PresetCodec::decodeOrNull) ?: return PresetLoad.Damaged
            PresetLoad.Loaded(presets)
        } catch (e: IOException) {
            PresetLoad.Unreadable
        } catch (e: SecurityException) {
            PresetLoad.Unreadable
        }
    }

    fun saveMerged(file: File, preset: Preset): SaveOutcome {
        val load = inspect(file)
        if (load == PresetLoad.Unreadable) return SaveOutcome.FAILED
        val current = (load as? PresetLoad.Loaded)?.presets.orEmpty()
        val merged = PresetCodec.upsert(current, preset) ?: return SaveOutcome.FULL
        if (load == PresetLoad.Damaged && !moveAside(file)) return SaveOutcome.FAILED
        return if (save(file, merged)) SaveOutcome.SAVED else SaveOutcome.FAILED
    }

    fun removeFrom(file: File, name: String): Boolean {
        val load = inspect(file) as? PresetLoad.Loaded ?: return false
        return save(file, PresetCodec.remove(load.presets, name))
    }

    fun write(file: File, text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return false
        val temp = File(file.parentFile, file.name + ".tmp")
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            moveOver(temp, file)
            true
        } catch (e: IOException) {
            temp.delete()
            false
        } catch (e: SecurityException) {
            temp.delete()
            false
        }
    }

    fun load(file: File): List<Preset> = PresetCodec.decode(read(file))

    fun save(file: File, presets: List<Preset>): Boolean {
        val text = PresetCodec.encode(presets) ?: return false
        return write(file, text)
    }

    private fun readBounded(file: File): String? {
        if (file.length() > MAX_BYTES) return null
        return file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(CHUNK)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) return null
                output.write(chunk, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun moveAside(file: File): Boolean = try {
        moveOver(file, File(file.parentFile, file.name + ASIDE_SUFFIX))
        true
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun moveOver(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
