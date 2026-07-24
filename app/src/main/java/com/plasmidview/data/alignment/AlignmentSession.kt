package com.plasmidview.data.alignment

import com.plasmidview.data.model.PlasmidDocument

/** Temporary session to pass alignment data across screens. */
object AlignmentSession {
    var result: AlignmentResult? = null
    var document: PlasmidDocument? = null
    var originalQuerySeq: String = ""
    fun clear() { result = null; document = null; originalQuerySeq = "" }
}
