package com.plasmidview.data.parser

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.plasmidview.data.model.*
import org.json.JSONObject

object SnapGeneBridge {

    private var initialized = false

    private fun ensureInit(context: Context) {
        if (!initialized) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
            initialized = true
        }
    }

    fun parseBytes(context: Context, bytes: ByteArray, fileName: String): ParseResult {
        return try {
            ensureInit(context)
            val tempFile = java.io.File(context.cacheDir, fileName.ifBlank { "temp.dna" })
            tempFile.writeBytes(bytes)
            val result = parseFile(context, tempFile.absolutePath)
            tempFile.delete()
            result
        } catch (e: Exception) {
            ParseResult.Error("Parse error: ${e.message}")
        }
    }

    private fun parseFile(context: Context, filePath: String): ParseResult {
        return try {
            ensureInit(context)
            val py = Python.getInstance()
            val module = py.getModule("sgffp_android")
            val jsonStr = module.callAttr("parse", filePath).toString()
            val obj = JSONObject(jsonStr)
            if (obj.has("error")) return ParseResult.Error("sgffp: ${obj.optString("detail","?")}")

            val seq = obj.optString("sequence","")
            if (seq.length < 10) return ParseResult.Error("No valid sequence")
            val topo = if (obj.optString("topology","CIRCULAR").uppercase().contains("LINEAR"))
                Topology.LINEAR else Topology.CIRCULAR

            val features = mutableListOf<Feature>()
            val fa = obj.optJSONArray("features")
            if (fa != null) {
                for (i in 0 until fa.length()) {
                    val f = fa.getJSONObject(i)
                    val s = f.optInt("start",0).coerceAtLeast(0)
                    val e = f.optInt("end",0).coerceAtLeast(0)
                    if (e > s && s < seq.length) {
                        features.add(Feature(
                            name = f.optString("name",""),
                            type = featureType(f.optString("type","other")),
                            start = s, end = e.coerceAtMost(seq.length),
                            strand = DocumentRepository.strandFromString(f.optString("strand","")),
                            color = f.optString("color","#757575"),
                        ))
                    }
                }
            }
            features.sortBy { it.start }
            ParseResult.Success(PlasmidDocument(
                name = obj.optString("name","Imported").ifBlank { "Imported" },
                sequence = seq, topology = topo, features = features
            ))
        } catch (e: Exception) { ParseResult.Error("Bridge: ${e.message}") }
    }

    fun featureType(t: String): FeatureType = when (t.lowercase()) {
        "cds" -> FeatureType.CDS; "promoter" -> FeatureType.PROMOTER
        "rep_origin","ori" -> FeatureType.ORIGIN; "primer" -> FeatureType.PRIMER
        "gene" -> FeatureType.GENE; "misc_feature" -> FeatureType.MISC_FEATURE
        "terminator" -> FeatureType.TERMINATOR; "protein_bind" -> FeatureType.PROTEIN_BIND
        "ltr" -> FeatureType.LTR; "repeat_region" -> FeatureType.REPEAT
        "misc_signal" -> FeatureType.SIGNAL; "misc_recomb" -> FeatureType.RECOMB
        "restriction_site" -> FeatureType.RESTRICTION_SITE
        else -> FeatureType.OTHER
    }

    /** Write feature list + seq back to a .dna byte array. */
    fun writeBytes(context: Context, bytes: ByteArray, features: List<Feature>, sequence: String, fileName: String): ByteArray? {
        return try {
            ensureInit(context)
            val tempFile = java.io.File(context.cacheDir, fileName.ifBlank { "temp_edit.dna" })
            tempFile.writeBytes(bytes)
            val py = Python.getInstance()
            val module = py.getModule("sgffp_android")
            val featuresJson = org.json.JSONArray()
            features.forEach { f ->
                featuresJson.put(org.json.JSONObject().apply {
                    put("name", f.name)
                    put("type", f.type.label.lowercase().replace(" ", "_"))
                    put("start", f.start)
                    put("end", f.end)
                    put("strand", when (f.strand) {
                        Strand.FORWARD -> "+"
                        Strand.REVERSE -> "-"
                        Strand.NONE -> "."
                    })
                    put("color", f.color)
                })
            }
            val result = module.callAttr("write_features", tempFile.absolutePath, featuresJson.toString(), sequence)
            val outBytes = tempFile.readBytes()
            tempFile.delete()
            outBytes
        } catch (e: Exception) { null }
    }
}
