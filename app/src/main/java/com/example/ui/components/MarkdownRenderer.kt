package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Header(val level: Int, val text: String) : ContentBlock
    data class Code(val language: String, val code: String) : ContentBlock
    data class BulletItem(val text: String) : ContentBlock
    data class NumberedItem(val number: String, val text: String) : ContentBlock
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = parseMarkdownBlocks(content)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 17.sp
                        else -> 15.sp
                    }
                    Text(
                        text = parseFormattedText(block.text, textColor),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is ContentBlock.Code -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language
                    )
                }

                is ContentBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = parseFormattedText(block.text, textColor),
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ContentBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.number,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            text = parseFormattedText(block.text, textColor),
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ContentBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        Text(
                            text = parseFormattedText(block.text, textColor),
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Check for code blocks ```
        if (line.trim().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeBuilder.append(lines[i]).append("\n")
                i++
            }
            if (i < lines.size) {
                // Skip the closing ```
                i++
            }
            blocks.add(ContentBlock.Code(language = language, code = codeBuilder.toString()))
            continue
        }

        val trimmed = line.trim()

        when {
            trimmed.startsWith("### ") -> {
                blocks.add(ContentBlock.Header(level = 3, text = trimmed.removePrefix("### ")))
                i++
            }
            trimmed.startsWith("## ") -> {
                blocks.add(ContentBlock.Header(level = 2, text = trimmed.removePrefix("## ")))
                i++
            }
            trimmed.startsWith("# ") -> {
                blocks.add(ContentBlock.Header(level = 1, text = trimmed.removePrefix("# ")))
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val bulletText = if (trimmed.startsWith("- ")) trimmed.removePrefix("- ") else trimmed.removePrefix("* ")
                blocks.add(ContentBlock.BulletItem(text = bulletText))
                i++
            }
            trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                val dotIndex = trimmed.indexOf('.')
                val number = trimmed.substring(0, dotIndex + 1)
                val itemText = trimmed.substring(dotIndex + 1).trim()
                blocks.add(ContentBlock.NumberedItem(number = number, text = itemText))
                i++
            }
            trimmed.isBlank() -> {
                i++
            }
            else -> {
                // Collect paragraph
                val pBuilder = StringBuilder(line)
                i++
                while (i < lines.size &&
                    !lines[i].trim().startsWith("```") &&
                    !lines[i].trim().startsWith("#") &&
                    !lines[i].trim().startsWith("- ") &&
                    !lines[i].trim().startsWith("* ") &&
                    !lines[i].trim().matches(Regex("^\\d+\\.\\s.*")) &&
                    lines[i].isNotBlank()
                ) {
                    pBuilder.append("\n").append(lines[i])
                    i++
                }
                blocks.add(ContentBlock.Paragraph(text = pBuilder.toString()))
            }
        }
    }

    return blocks
}

@Composable
fun parseFormattedText(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            val boldStart = text.indexOf("**", cursor)
            val inlineCodeStart = text.indexOf("`", cursor)

            // Find closest token
            val nextSpecial = when {
                boldStart != -1 && inlineCodeStart != -1 -> minOf(boldStart, inlineCodeStart)
                boldStart != -1 -> boldStart
                inlineCodeStart != -1 -> inlineCodeStart
                else -> -1
            }

            if (nextSpecial == -1) {
                append(text.substring(cursor))
                break
            }

            if (nextSpecial > cursor) {
                append(text.substring(cursor, nextSpecial))
                cursor = nextSpecial
            }

            if (nextSpecial == boldStart) {
                val boldEnd = text.indexOf("**", boldStart + 2)
                if (boldEnd != -1) {
                    val boldContent = text.substring(boldStart + 2, boldEnd)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldContent)
                    pop()
                    cursor = boldEnd + 2
                } else {
                    append("**")
                    cursor += 2
                }
            } else if (nextSpecial == inlineCodeStart) {
                val codeEnd = text.indexOf("`", inlineCodeStart + 1)
                if (codeEnd != -1) {
                    val codeContent = text.substring(inlineCodeStart + 1, codeEnd)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF282B33),
                            color = Color(0xFF6EE7B7)
                        )
                    )
                    append(" $codeContent ")
                    pop()
                    cursor = codeEnd + 1
                } else {
                    append("`")
                    cursor += 1
                }
            }
        }
    }
}
