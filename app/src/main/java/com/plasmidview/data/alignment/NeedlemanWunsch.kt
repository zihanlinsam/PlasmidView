package com.plasmidview.data.alignment

/**
 * Pure-Kotlin Smith-Waterman local alignment.
 *
 * Scoring: Match=+2, Mismatch=-1, Gap=-2.
 * Only returns the highest-scoring local alignment segment.
 */
data class AlignmentResult(
    val queryName: String,
    val queryLen: Int,
    val refName: String,
    val refLen: Int,
    val queryAligned: String,
    val refAligned: String,
    val matchLine: String,           // "*"=match, ":"=strong, "."=weak, " "=gap/none
    val score: Int,
    val maxScore: Int,
    val refStartPos: Int,            // 0-based start on reference
    val refEndPos: Int,              // 0-based exclusive end on reference
    val gaps: Int,
    val queryMatchStart: Int,    // 0-based index in original query where match starts
    val queryMatchEnd: Int,      // 0-based exclusive-end in original query
    val isRevComp: Boolean = false
)

object SmithWaterman {

    private const val MATCH = 2
    private const val MISMATCH = -1
    private const val GAP = -2

    /**
     * Local alignment: find the best matching segment of [query] in [ref].
     */
    fun align(query: String, ref: String, queryName: String = "Query", refName: String = "Reference", isRevComp: Boolean = false): AlignmentResult? {
        val q = query.uppercase().filter { it in "ACGTNRYSWKMBDHV" }
        val r = ref.uppercase().filter { it in "ACGTNRYSWKMBDHV" }
        val m = q.length
        val n = r.length
        if (m == 0 || n == 0) return null

        val dp = Array(m + 1) { IntArray(n + 1) }
        var maxScore = 0
        var maxI = 0
        var maxJ = 0

        for (i in 1..m) {
            for (j in 1..n) {
                val diag = dp[i - 1][j - 1] + if (q[i - 1] == r[j - 1]) MATCH else MISMATCH
                val up = dp[i - 1][j] + GAP
                val left = dp[i][j - 1] + GAP
                dp[i][j] = maxOf(0, diag, up, left)
                if (dp[i][j] > maxScore) {
                    maxScore = dp[i][j]
                    maxI = i
                    maxJ = j
                }
            }
        }

        if (maxScore == 0) return null  // no significant match

        // Traceback from maxI, maxJ until hitting 0
        val qChars = mutableListOf<Char>()
        val rChars = mutableListOf<Char>()
        val mChars = mutableListOf<Char>()
        var i = maxI
        var j = maxJ
        var gapCount = 0

        while (i > 0 && j > 0 && dp[i][j] > 0) {
            when {
                dp[i][j] == dp[i - 1][j - 1] + if (q[i - 1] == r[j - 1]) MATCH else MISMATCH -> {
                    qChars.add(q[i - 1])
                    rChars.add(r[j - 1])
                    mChars.add(clustalwSymbol(q[i - 1], r[j - 1]))
                    i--; j--
                }
                dp[i][j] == dp[i - 1][j] + GAP -> {
                    qChars.add(q[i - 1])
                    rChars.add('-')
                    mChars.add(' ')
                    gapCount++
                    i--
                }
                else -> {
                    qChars.add('-')
                    rChars.add(r[j - 1])
                    mChars.add(' ')
                    gapCount++
                    j--
                }
            }
        }

        val queryAligned = qChars.reversed().joinToString("")
        val refAligned = rChars.reversed().joinToString("")
        val matchLine = mChars.reversed().joinToString("")

        // j is the DP column where traceback ended → first ref base in alignment
        // maxJ is the DP column where max score was → exclusive end
        val refStartPos = j
        val refEndPos = maxJ
        val bestPossible = minOf(q.length, refEndPos - refStartPos) * MATCH

        val queryMatchEnd = maxI
        val queryMatchStart = maxI - qChars.count { it != '-' }

        return AlignmentResult(
            queryName = queryName,
            queryLen = q.length,
            refName = refName,
            refLen = r.length,
            queryAligned = queryAligned,
            refAligned = refAligned,
            matchLine = matchLine,
            score = maxScore,
            maxScore = bestPossible,
            refStartPos = refStartPos,
            refEndPos = refEndPos,
            gaps = gapCount,
            queryMatchStart = queryMatchStart,
            queryMatchEnd = queryMatchEnd,
            isRevComp = isRevComp
        )
    }

    private fun clustalwSymbol(a: Char, b: Char): Char {
        if (a == b) return '*'
        val pur = setOf('A', 'G')
        val pyr = setOf('C', 'T')
        return when {
            a in pur && b in pur -> ':'
            a in pyr && b in pyr -> ':'
            a != '-' && b != '-' -> '.'
            else -> ' '
        }
    }
}
