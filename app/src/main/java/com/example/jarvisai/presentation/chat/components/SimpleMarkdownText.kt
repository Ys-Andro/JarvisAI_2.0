package com.example.jarvisai.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.ui.theme.JarvisCodeBackground
import com.example.jarvisai.ui.theme.JarvisPrimary
import com.example.jarvisai.ui.theme.JarvisTextPrimary

/**
 * Clean, lightweight Markdown parser for Compose.
 * Handles:
 * - Code blocks: ```code```
 * - Inline code: `code`
 * - Bold: **text**
 * - Italic: *text*
 * - Headers: ### Header
 * - Bullet lists: - item or * item
 */
@Composable
fun SimpleMarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = JarvisTextPrimary,
    fontSize: Int = 15
) {
    SelectionContainer {
        Column(modifier = modifier) {
            val parts = remember(content) { splitIntoBlocks(content) }
            for (block in parts) {
                when (block) {
                    is MarkdownBlock.CodeBlock -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = JarvisCodeBackground,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = block.code,
                                color = JarvisPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSize - 2).sp,
                                lineHeight = (fontSize + 2).sp
                            )
                        }
                    }
                    is MarkdownBlock.Paragraph -> {
                        val annotated = remember(block.text, textColor) {
                            parseInlineMarkdown(block.text, textColor)
                        }
                        Text(
                            text = annotated,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 7).sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock
}

private fun splitIntoBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var inCodeBlock = false
    val codeAccumulator = StringBuilder()
    var codeLang = ""
    val paragraphAccumulator = StringBuilder()

    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (inCodeBlock) {
                // End code block
                blocks.add(MarkdownBlock.CodeBlock(codeAccumulator.toString().trimEnd(), codeLang))
                codeAccumulator.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                // Start code block: flush previous paragraph
                if (paragraphAccumulator.isNotBlank()) {
                    blocks.add(MarkdownBlock.Paragraph(paragraphAccumulator.toString().trimEnd()))
                    paragraphAccumulator.clear()
                }
                codeLang = line.trimStart().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else if (inCodeBlock) {
            codeAccumulator.append(line).append("\n")
        } else {
            paragraphAccumulator.append(line).append("\n")
        }
    }

    if (inCodeBlock && codeAccumulator.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeAccumulator.toString().trimEnd(), codeLang))
    } else if (paragraphAccumulator.isNotBlank()) {
        blocks.add(MarkdownBlock.Paragraph(paragraphAccumulator.toString().trimEnd()))
    }

    return if (blocks.isEmpty()) listOf(MarkdownBlock.Paragraph(content)) else blocks
}

private fun parseInlineMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // Bold: **text**
                i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline Code: `code`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = JarvisPrimary,
                                background = JarvisCodeBackground
                            )
                        )
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: *text*
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Header at line start: ###
                text.startsWith("### ", i) -> {
                    val endOfLine = text.indexOf('\n', i)
                    val headerText = if (endOfLine != -1) text.substring(i + 4, endOfLine) else text.substring(i + 4)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = JarvisPrimary))
                    append(headerText)
                    pop()
                    i = if (endOfLine != -1) endOfLine else len
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
