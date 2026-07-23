package com.plasmidview.data.parser

import com.plasmidview.data.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plasmid file parser. Delegates .dna to SnapGeneBridge (Chaquopy),
 * handles JSON and FASTA directly.
 */
object PlasmidParser {

    fun fromJson(jsonString: String): ParseResult {
        return try {
            val obj = JSONObject(jsonString)
            val name = obj.optString("name", "Untitled")
            val sequence = obj.optString("sequence", "")
            val topoStr = obj.optString("topology", "CIRCULAR")
            val topology = if (topoStr.uppercase().contains("LINEAR")) Topology.LINEAR else Topology.CIRCULAR

            val features = mutableListOf<Feature>()
            val featuresArr = obj.optJSONArray("features")
            if (featuresArr != null) {
                for (i in 0 until featuresArr.length()) {
                    val f = featuresArr.getJSONObject(i)
                    val start = f.optInt("start", 0)
                    val end = f.optInt("end", 0)
                    val typeStr = f.optString("type", "other")
                    if (end > start) {
                        features.add(Feature(
                            name = f.optString("name", ""),
                            type = SnapGeneBridge.featureType(typeStr),
                            start = start,
                            end = end,
                            strand = DocumentRepository.strandFromString(f.optString("strand", "")),
                            color = f.optString("color", "#757575"),
                            description = f.optString("description", "")
                        ))
                    }
                }
            }
            features.sortBy { it.start }

            ParseResult.Success(PlasmidDocument(
                name = name.ifBlank { "Untitled" },
                sequence = sequence,
                topology = topology,
                features = features
            ))
        } catch (e: Exception) {
            ParseResult.Error("JSON parse error: ${e.message}")
        }
    }

    fun fromFasta(content: String): PlasmidDocument {
        var name = "Imported FASTA"
        val sequence = StringBuilder()
        content.lines().forEach { line ->
            when {
                line.startsWith(">") -> {
                    val parts = line.substringAfter(">").trim().split("\\s+".toRegex(), limit = 2)
                    name = parts[0]
                }
                line.isNotBlank() -> {
                    val clean = line.replace(Regex("[^a-zA-Z]"), "").uppercase()
                    sequence.append(clean)
                }
            }
        }
        return PlasmidDocument(
            name = name,
            sequence = sequence.toString()
        )
    }
}
