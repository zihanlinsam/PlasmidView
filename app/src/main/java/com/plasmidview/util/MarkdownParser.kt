package com.plasmidview.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object MarkdownParser {
    fun parse(text: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        val lines = text.split("\n")
        lines.forEachIndexed { li, line ->
            if (li > 0) append("\n")
            when {
                line.startsWith("### ") -> { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.removePrefix("### ")) } }
                line.startsWith("## ") -> { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.removePrefix("## ")) } }
                line.startsWith("# ") -> { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.removePrefix("# ")) } }
                line.startsWith("```") -> { withStyle(SpanStyle(color = Color(0xFF388E3C), background = Color(0xFFE8F5E9))) { append(line) } }
                line.startsWith("|") && line.contains("|") -> {
                    // Render table row: replace | with spacing, remove separator rows
                    if (!line.matches(Regex("\\|\\s*[-:]\\s*\\|.*"))) {
                        val cells = line.split("\\|").filter { it.isNotBlank() }
                        withStyle(SpanStyle(color = Color(0xFF2196F3))) {
                            append("  " + cells.joinToString("  │  "))
                        }
                    }
                }
                line.startsWith("---") || line.startsWith("***") -> { append("────────────────") }
                else -> {
                    // Inline formatting
                    var ci = 0
                    while (ci < line.length) {
                        val boldStart = line.indexOf("**", ci)
                        val italicStart = line.indexOf("*", ci)
                        val codeStart = line.indexOf("`", ci)

                        val nextSpecial = listOfNotNull(
                            if (boldStart >= 0 && boldStart >= ci) boldStart to "**" else null,
                            if (italicStart >= 0 && italicStart >= ci && (boldStart < 0 || italicStart != boldStart)) italicStart to "*" else null,
                            if (codeStart >= 0 && codeStart >= ci) codeStart to "`" else null
                        ).minByOrNull { it.first }

                        if (nextSpecial != null) {
                            val (pos, fmt) = nextSpecial
                            if (pos > ci) append(line.substring(ci, pos))
                            ci = pos + fmt.length
                            val end = line.indexOf(fmt, ci)
                            val content = if (end >= 0) line.substring(ci, end) else line.substring(ci)
                            when (fmt) {
                                "**" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(content) }
                                "*" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(content) }
                                "`" -> withStyle(SpanStyle(color = Color(0xFF388E3C), background = Color(0xFFE8F5E9))) { append(content) }
                            }
                            if (end >= 0) ci = end + fmt.length else ci = line.length
                        } else {
                            append(line.substring(ci))
                            ci = line.length
                        }
                    }
                }
            }
        }
    }
}
