package com.ml.shubham0204.docqa.domain.readers

import java.io.InputStream

class MarkdownReader : Reader() {
    override fun readFromInputStream(inputStream: InputStream): String {
        val markdownText = inputStream.bufferedReader().use { it.readText() }
        return markdownText
            // Remove code blocks
            .replace(Regex("""```[\s\S]*?```"""), "")
            // Remove inline code
            .replace(Regex("""`([^`]+)`"""), "$1")
            // Remove images
            .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
            // Remove links, keeping the link text
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            // Remove bold/italics/strikethrough
            .replace(Regex("""\*\*|__|~~"""), "")
            // Remove headers
            .replace(Regex("""^#+\s*""", RegexOption.MULTILINE), "")
            // Remove list items (-, *, +)
            .replace(Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE), "")
            // Remove numbered lists (1. ...)
            .replace(Regex("""^\s*\d+\.\s+""", RegexOption.MULTILINE), "")
            // Remove blockquotes
            .replace(Regex("""^>\s*""", RegexOption.MULTILINE), "")
            // Remove horizontal rules
            .replace(Regex("""^-{3,}\s*$""", RegexOption.MULTILINE), "")
    }
}
