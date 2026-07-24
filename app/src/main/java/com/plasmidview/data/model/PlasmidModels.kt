package com.plasmidview.data.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class Topology(val label: String) { CIRCULAR("Circular"), LINEAR("Linear") }
enum class Strand(val label: String) { FORWARD("Forward (+)"), REVERSE("Reverse (-)"), NONE("None") }
enum class FeatureType(val label: String) {
    CDS("CDS"), PROMOTER("Promoter"), ORIGIN("Origin"), PRIMER("Primer"),
    RESTRICTION_SITE("Restriction Site"), GENE("Gene"), MISC_FEATURE("Misc Feature"),
    TERMINATOR("Terminator"), PROTEIN_BIND("Protein Binding"), LTR("LTR"),
    REPEAT("Repeat Region"), SIGNAL("Signal"), RECOMB("Recombination"), OTHER("Other")
}

data class Feature(
    val name: String, val type: FeatureType, val start: Int, val end: Int,
    val strand: Strand = Strand.NONE, val color: String = "#757575", val description: String = ""
) { val length: Int get() = end - start }

data class PlasmidDocument(
    val name: String, val sequence: String,
    val topology: Topology = Topology.CIRCULAR,
    val features: List<Feature> = emptyList(), val sourceFile: String = ""
) { val totalLength: Int get() = sequence.length }

sealed class ParseResult {
    data class Success(val doc: PlasmidDocument) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

object DocumentRepository {
    val documents = mutableListOf<PlasmidDocument>()
    fun strandFromString(s: String): Strand = when (s.lowercase()) {
        "forward","f","plus","+" -> Strand.FORWARD
        "reverse","r","minus","-" -> Strand.REVERSE
        else -> Strand.NONE
    }
}

/** Reverse-complement a DNA string. */
fun String.reverseComplement(): String {
    val map = mapOf('A' to 'T', 'T' to 'A', 'G' to 'C', 'C' to 'G',
                     'a' to 't', 't' to 'a', 'g' to 'c', 'c' to 'g')
    return this.reversed().map { map[it] ?: it }.joinToString("")
}

/** Return the biologically meaningful sequence for this feature (rev-comp if REVERSE strand). */
fun Feature.displaySequence(doc: PlasmidDocument): String {
    val s = start.coerceAtMost(doc.totalLength)
    val e = end.coerceAtMost(doc.totalLength)
    if (s >= e) return ""
    val raw = doc.sequence.substring(s, e)
    return if (strand == Strand.REVERSE) raw.reverseComplement() else raw
}

// === File persistence ===
data class FileEntry(val name: String, val uri: String, val timestamp: Long = System.currentTimeMillis())

private val Context.fs: DataStore<Preferences> by preferencesDataStore(name = "files")
private val FILES_KEY = stringPreferencesKey("imported_files")

private fun List<FileEntry>.toJson(): String {
    val a = JSONArray()
    forEach { e -> a.put(JSONObject().apply { put("name",e.name); put("uri",e.uri); put("timestamp",e.timestamp) }) }
    return a.toString()
}
private fun String.toEntries(): List<FileEntry> = try {
    val a = JSONArray(this); (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        FileEntry(o.optString("name",""), o.optString("uri",""), o.optLong("timestamp",0))
    }
} catch (_: Exception) { emptyList() }

class FileRepository(private val ctx: Context) {
    val files: Flow<List<FileEntry>> = ctx.fs.data.map { it[FILES_KEY]?.toEntries()?.distinctBy { e -> e.uri } ?: emptyList() }
    suspend fun add(entry: FileEntry) { ctx.fs.edit { p -> val existing = p[FILES_KEY]?.toEntries() ?: emptyList(); if (existing.none { it.uri == entry.uri }) { p[FILES_KEY] = (existing + entry).toJson() } } }
    suspend fun remove(uris: Set<String>) {
        ctx.fs.edit { p ->
            val raw = p[FILES_KEY] ?: return@edit
            val arr = JSONArray(raw)
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("uri", "") !in uris) out.put(o)
            }
            p[FILES_KEY] = out.toString()
        }
    }
    suspend fun clear() { ctx.fs.edit { it.remove(FILES_KEY) } }
}

// === Theme mode ===
enum class ThemeMode { AUTO, LIGHT, DARK }

// === App preferences ===
private val Context.ps: DataStore<Preferences> by preferencesDataStore(name = "pv")
class AppPreferences(private val ctx: Context) {
    companion object {
        private val BC = booleanPreferencesKey("base")
        private val DK = intPreferencesKey("darkMode") // 0=AUTO 1=LIGHT 2=DARK
        private val CV = booleanPreferencesKey("circ")
        private val AI_URL = stringPreferencesKey("ai_url")
        private val AI_KEY = stringPreferencesKey("ai_key")
        private val AI_MODEL = stringPreferencesKey("ai_model")
        private val AI_LANG = stringPreferencesKey("ai_lang")
        private val AI_THINKING = booleanPreferencesKey("ai_thinking")
        private val EXAMPLE_SHOWN = booleanPreferencesKey("example_shown")
    }
    val baseCol: Flow<Boolean> = ctx.ps.data.map { it[BC] ?: true }
    val themeMode: Flow<ThemeMode> = ctx.ps.data.map { when (it[DK]) { 1 -> ThemeMode.LIGHT; 2 -> ThemeMode.DARK; else -> ThemeMode.AUTO } }
    val circ: Flow<Boolean> = ctx.ps.data.map { it[CV] ?: true }
    val aiBaseUrl: Flow<String> = ctx.ps.data.map { it[AI_URL] ?: "https://api.xiaomimimo.com/v1" }
    val aiApiKey: Flow<String> = ctx.ps.data.map { it[AI_KEY] ?: "" }
    val aiModel: Flow<String> = ctx.ps.data.map { it[AI_MODEL] ?: "mimo-v2.5" }
    val aiLang: Flow<String> = ctx.ps.data.map { it[AI_LANG] ?: "english" }
    val aiThinking: Flow<Boolean> = ctx.ps.data.map { it[AI_THINKING] ?: false }
    val exampleShown: Flow<Boolean> = ctx.ps.data.map { it[EXAMPLE_SHOWN] ?: false }
    suspend fun setBase(v: Boolean) { ctx.ps.edit { it[BC] = v } }
    suspend fun setThemeMode(m: ThemeMode) { ctx.ps.edit { it[DK] = m.ordinal } }
    suspend fun setCirc(v: Boolean) { ctx.ps.edit { it[CV] = v } }
    suspend fun setAiUrl(v: String) { ctx.ps.edit { it[AI_URL] = v } }
    suspend fun setAiKey(v: String) { ctx.ps.edit { it[AI_KEY] = v } }
    suspend fun setAiModel(v: String) { ctx.ps.edit { it[AI_MODEL] = v } }
    suspend fun setAiLang(v: String) { ctx.ps.edit { it[AI_LANG] = v } }
    suspend fun setAiThinking(v: Boolean) { ctx.ps.edit { it[AI_THINKING] = v } }
    suspend fun setExampleShown(v: Boolean) { ctx.ps.edit { it[EXAMPLE_SHOWN] = v } }
}
