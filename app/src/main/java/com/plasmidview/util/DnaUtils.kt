package com.plasmidview.util

object DnaUtils {

    fun complement(seq: String): String = seq.uppercase().map { base ->
        when (base) {
            'A' -> 'T'; 'T' -> 'A'
            'G' -> 'C'; 'C' -> 'G'
            else -> 'N'
        }
    }.joinToString("")

    fun reverseComplement(seq: String) = complement(seq).reversed()

    fun gcContent(seq: String): Double {
        if (seq.isEmpty()) return 0.0
        val gc = seq.uppercase().count { it in "GC" }
        return gc.toDouble() / seq.length
    }

    fun translate(seq: String): String {
        val codonMap = mapOf(
            "TTT" to "F", "TTC" to "F", "TTA" to "L", "TTG" to "L",
            "CTT" to "L", "CTC" to "L", "CTA" to "L", "CTG" to "L",
            "ATT" to "I", "ATC" to "I", "ATA" to "I", "ATG" to "M",
            "GTT" to "V", "GTC" to "V", "GTA" to "V", "GTG" to "V",
            "TCT" to "S", "TCC" to "S", "TCA" to "S", "TCG" to "S",
            "CCT" to "P", "CCC" to "P", "CCA" to "P", "CCG" to "P",
            "ACT" to "T", "ACC" to "T", "ACA" to "T", "ACG" to "T",
            "GCT" to "A", "GCC" to "A", "GCA" to "A", "GCG" to "A",
            "TAT" to "Y", "TAC" to "Y", "TAA" to "*", "TAG" to "*",
            "CAT" to "H", "CAC" to "H", "CAA" to "Q", "CAG" to "Q",
            "AAT" to "N", "AAC" to "N", "AAA" to "K", "AAG" to "K",
            "GAT" to "D", "GAC" to "D", "GAA" to "E", "GAG" to "E",
            "TGT" to "C", "TGC" to "C", "TGA" to "*", "TGG" to "W",
            "CGT" to "R", "CGC" to "R", "CGA" to "R", "CGG" to "R",
            "AGT" to "S", "AGC" to "S", "AGA" to "R", "AGG" to "R",
            "GGT" to "G", "GGC" to "G", "GGA" to "G", "GGG" to "G"
        )
        return seq.uppercase().chunked(3).mapNotNull { codonMap[it] }.joinToString("")
    }

    fun tm(seq: String, salt: Double = 0.05, oligoConc: Double = 0.00025): Double {
        val gcCount = seq.uppercase().count { it in "GC" }
        val atCount = seq.length - gcCount
        // Basic Tm calculation (for short oligos < 20bp)
        return if (seq.length < 14) {
            2.0 * atCount + 4.0 * gcCount
        } else {
            64.9 + 41.0 * (gcCount - 16.4) / seq.length
        }
    }

    fun findRestrictionSites(seq: String, enzyme: String): List<Int> {
        val sites = mapOf(
            "EcoRI" to "GAATTC",
            "HindIII" to "AAGCTT",
            "BamHI" to "GGATCC",
            "NotI" to "GCGGCCGC",
            "XhoI" to "CTCGAG",
            "NheI" to "GCTAGC",
            "KpnI" to "GGTACC",
            "SpeI" to "ACTAGT",
            "SnaBI" to "TACGTA",
            "PmeI" to "GTTTAAAC"
        )
        val pattern = sites[enzyme] ?: return emptyList()
        val results = mutableListOf<Int>()
        var idx = 0
        while (idx <= seq.length - pattern.length) {
            val found = seq.indexOf(pattern, idx, ignoreCase = true)
            if (found == -1) break
            results.add(found)
            idx = found + 1
        }
        return results
    }
}
