package com.plasmidview.data.digest

data class EnzymeInfo(
    val name: String,
    val site: String,
    val overhang: String,  // "5'", "3'", or "blunt"
    val cutPositions: List<Int>  // 0-based positions
)

data class FragmentInfo(
    val start: Int,    // 0-based start
    val end: Int,      // 0-based exclusive end (i.e., first base AFTER fragment)
    val length: Int
)

data class DigestResult(
    val enzymes: List<String>,
    val cutsByEnzyme: Map<String, List<Int>>,  // enzyme name -> 0-based cut positions
    val fragments: List<FragmentInfo>,
    val fragmentCount: Int
)

data class AutoPickCandidate(
    val enzymes: List<String>,
    val cutPositions: List<Int>,    // sorted 0-based positions
    val fragmentLengths: List<Int>, // sorted descending
    val fragmentCount: Int
)
