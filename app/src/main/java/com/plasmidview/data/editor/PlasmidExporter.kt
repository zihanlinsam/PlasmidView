package com.plasmidview.data.editor

import com.plasmidview.data.model.*
import java.text.SimpleDateFormat
import java.util.*

object PlasmidExporter {

    fun toFasta(doc: PlasmidDocument): String {
        return ">${doc.name}\n${doc.sequence}"
    }

    fun toGenBank(doc: PlasmidDocument): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(Date())
        sb.appendLine("LOCUS       ${doc.name.padEnd(30)} ${doc.sequence.length} bp   ${doc.topology.label.lowercase()}   DNA")
        sb.appendLine("DEFINITION  ${doc.name}.")
        sb.appendLine("ACCESSION   ${doc.name}")
        sb.appendLine("VERSION     ${doc.name}")
        sb.appendLine("KEYWORDS    .")
        sb.appendLine("SOURCE      .")
        sb.appendLine("  ORGANISM  .")
        sb.appendLine("FEATURES             Location/Qualifiers")
        if (doc.features.isEmpty()) {
            sb.appendLine("                     /note=\"No features\"")
        }
        doc.features.sortedBy { it.start }.forEach { f ->
            val loc = when (f.strand) {
                Strand.FORWARD -> "${f.start + 1}..${f.end}"
                Strand.REVERSE -> "complement(${f.start + 1}..${f.end})"
                Strand.NONE -> "${f.start + 1}..${f.end}"
            }
            val label = f.name.ifBlank { f.type.label }
            sb.appendLine("     ${f.type.label.padEnd(15)} $loc")
            sb.appendLine("                     /label=\"$label\"")
        }
        sb.appendLine("ORIGIN")
        val seq = doc.sequence.uppercase()
        seq.chunked(60).forEachIndexed { i, chunk ->
            val pos = String.format("%09d", i * 60 + 1)
            val formatted = chunk.chunked(10).joinToString(" ")
            sb.appendLine("$pos $formatted")
        }
        sb.appendLine("//")
        return sb.toString()
    }
}
