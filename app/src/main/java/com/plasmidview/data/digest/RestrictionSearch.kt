package com.plasmidview.data.digest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

val COMMON_ENZYMES = listOf(
    "BamHI", "BglII", "DraI", "EcoRI", "Eco32I", "EcoRV",
    "HincII", "HindIII", "KpnI", "NcoI", "NdeI", "NheI",
    "NotI", "PstI", "PvuII", "SacI", "SalI", "SmaI",
    "SpeI", "SphI", "XbaI", "XhoI"
)

/** Pure-Kotlin restriction enzyme search engine. */
object RestrictionSearch {

    data class EnzymeEntry(
        val name: String, val site: String,
        val elucidate: String, val overhang: String
    )

    // IUPAC ambiguity codes
    private val IUPAC = mapOf(
        'A' to setOf('A'), 'C' to setOf('C'), 'G' to setOf('G'), 'T' to setOf('T'),
        'R' to setOf('A', 'G'), 'Y' to setOf('C', 'T'), 'S' to setOf('G', 'C'),
        'W' to setOf('A', 'T'), 'K' to setOf('G', 'T'), 'M' to setOf('A', 'C'),
        'B' to setOf('C', 'G', 'T'), 'D' to setOf('A', 'G', 'T'),
        'H' to setOf('A', 'C', 'T'), 'V' to setOf('A', 'C', 'G'),
        'N' to setOf('A', 'C', 'G', 'T'),
    )

    /** Load full enzyme data from assets JSON. */
    fun loadEnzymeData(ctx: Context): List<EnzymeEntry> {
        val json = ctx.assets.open("enzyme_data.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EnzymeEntry(
                name = o.getString("name"),
                site = o.getString("site"),
                elucidate = o.optString("elucidate", ""),
                overhang = o.optString("overhang", "")
            )
        }
    }

    /** Parse elucidate string → (sense_cut_offset, antisense_cut_offset) 0-based. */
    private fun parseElucidate(el: String): Pair<Int, Int> {
        val parts = el.split("^")
        if (parts.size != 2) return Pair(0, el.replace("^", "").replace("_", "").length)
        val beforeSense = parts[0].count { it != '_' }
        val after = parts[1]
        val beforeAnti = if ('_' in after) beforeSense + after.indexOf('_') else beforeSense + after.count { it != '_' }
        return Pair(beforeSense, beforeAnti)
    }

    /** Check if site matches seq at pos (IUPAC-aware). */
    private fun matchPos(seq: String, site: String, pos: Int): Boolean {
        if (pos + site.length > seq.length) return false
        for (i in site.indices) {
            val ch = site[i].uppercaseChar()
            val want = IUPAC[ch] ?: setOf(ch)
            if (seq[pos + i].uppercaseChar() !in want) return false
        }
        return true
    }

    /** Find all match positions of site in seq. Returns 0-based start indices. */
    private fun findSites(seq: String, site: String, linear: Boolean): List<Int> {
        val ext = if (!linear) seq + site.substring(0, site.length - 1) else seq
        val hits = mutableListOf<Int>()
        for (i in seq.indices) {
            if (matchPos(ext, site, i)) hits.add(i)
        }
        return hits.distinct()
    }

    /** Search cut positions (1-based, Biopython convention). */
    private fun search(seq: String, site: String, elucidate: String, linear: Boolean): List<Int> {
        val (senseCut, _) = parseElucidate(elucidate)
        return findSites(seq, site, linear).map { it + senseCut + 1 }.distinct().sorted()
    }

    /** Calculate fragments for a circular digest given 0-based cut positions. */
    private fun calcFragments(cuts: List<Int>, total: Int): List<FragmentInfo> {
        if (cuts.isEmpty()) return listOf(FragmentInfo(0, total, total))
        val sorted = cuts.sorted()
        val frags = mutableListOf<FragmentInfo>()
        for (i in sorted.indices) {
            val s = sorted[i]
            var e = sorted[(i + 1) % sorted.size]
            if (e <= s) e += total
            frags.add(FragmentInfo(s, e % total, e - s))
        }
        return frags
    }

    // ── Public API ──

    /** Digest with given enzyme names. */
    fun calculateDigest(
        seq: String, enzymeNames: List<String>, enzymeData: List<EnzymeEntry>
    ): DigestResult {
        val s = seq.uppercase()
        val total = s.length
        val cutsByEnzyme = mutableMapOf<String, List<Int>>()
        val allCuts = mutableSetOf<Int>()

        for (name in enzymeNames) {
            val entry = enzymeData.find { it.name == name } ?: continue
            val pos1 = search(s, entry.site, entry.elucidate, linear = false)
            if (pos1.isNotEmpty()) {
                val pos0 = pos1.map { it - 1 }.sorted().distinct()
                cutsByEnzyme[name] = pos0
                allCuts.addAll(pos0)
            }
        }

        val fragments = calcFragments(allCuts.toList(), total)

        return DigestResult(
            enzymes = enzymeNames,
            cutsByEnzyme = cutsByEnzyme,
            fragments = fragments,
            fragmentCount = fragments.size
        )
    }

    /** Auto-pick enzymes matching criteria. */
    fun autoPick(
        seq: String, mode: String, targetFragments: Int,
        minBp: Int, maxBp: Int, enzymeData: List<EnzymeEntry>
    ): List<AutoPickCandidate> {
        val s = seq.uppercase()
        val total = s.length
        val candidates = mutableListOf<AutoPickCandidate>()

        if (mode == "single") {
            val commonData = enzymeData.filter { it.name in COMMON_ENZYMES }
            for (entry in commonData) {
                val pos1 = search(s, entry.site, entry.elucidate, linear = false)
                if (pos1.isEmpty()) continue
                val pos0 = pos1.map { it - 1 }.sorted().distinct()
                if (pos0.size != targetFragments) continue
                val lengths = fragLengths(pos0, total)
                if (minBp > 0 && lengths.min() < minBp) continue
                if (maxBp > 0 && lengths.max() > maxBp) continue
                candidates.add(AutoPickCandidate(
                    enzymes = listOf(entry.name),
                    cutPositions = pos0,
                    fragmentLengths = lengths.sortedDescending(),
                    fragmentCount = pos0.size
                ))
            }
        } else if (mode == "double") {
            // Only search common enzymes, cache individual results
            val cache = mutableMapOf<String, Set<Int>>()
            val commonData = enzymeData.filter { it.name in COMMON_ENZYMES }
            for (entry in commonData) {
                val pos1 = search(s, entry.site, entry.elucidate, linear = false)
                if (pos1.isNotEmpty()) {
                    cache[entry.name] = pos1.map { it - 1 }.toSet()
                }
            }
            val cutting = commonData.filter { it.name in cache }
            for (i in cutting.indices) {
                for (j in i + 1 until cutting.size) {
                    val p1 = cache[cutting[i].name] ?: continue
                    val p2 = cache[cutting[j].name] ?: continue
                    val all = (p1 + p2).sorted()
                    if (all.size != targetFragments) continue
                    val lengths = fragLengths(all, total)
                    if (minBp > 0 && lengths.min() < minBp) continue
                    if (maxBp > 0 && lengths.max() > maxBp) continue
                    candidates.add(AutoPickCandidate(
                        enzymes = listOf(cutting[i].name, cutting[j].name),
                        cutPositions = all,
                        fragmentLengths = lengths.sortedDescending(),
                        fragmentCount = all.size
                    ))
                    if (candidates.size >= 200) break
                }
                if (candidates.size >= 200) break
            }
        }

        candidates.sortBy { it.fragmentLengths.toSet().size }
        return candidates
    }

    private fun fragLengths(cuts: List<Int>, total: Int): List<Int> {
        if (cuts.isEmpty()) return listOf(total)
        val lens = mutableListOf<Int>()
        for (i in cuts.indices) {
            val s = cuts[i]
            var e = cuts[(i + 1) % cuts.size]
            if (e <= s) e += total
            lens.add(e - s)
        }
        return lens
    }
}
