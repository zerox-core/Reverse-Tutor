package com.reversetutor.preview.shell

import java.io.StringReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceImportInputFactoryTest {
    @Test
    fun unsupportedBinarySelectionDoesNotReadTextBody() = runBlocking {
        var attemptedRead = false

        val input = buildSourceImportInput(
            requestId = 10L,
            fileName = "archive.bin",
            mimeType = "application/octet-stream",
            uri = "content://sources/archive.bin",
            readText = {
                attemptedRead = true
                error("Binary body should not be read")
            }
        )

        assertEquals("archive.bin", input.fileName)
        assertEquals("application/octet-stream", input.mimeType)
        assertEquals("content://sources/archive.bin", input.uri)
        assertNull(input.text)
        assertTrue(!attemptedRead)
    }

    @Test
    fun markdownSelectionReadsTextBodyForLocalParser() = runBlocking {
        val input = buildSourceImportInput(
            requestId = 11L,
            fileName = "notes.md",
            mimeType = "text/markdown",
            uri = "content://sources/notes.md",
            readText = { "# Heading" }
        )

        assertEquals("# Heading", input.text)
    }

    @Test
    fun pdfSelectionReadsExtractedTextForLocalParser() = runBlocking {
        val input = buildSourceImportInput(
            requestId = 14L,
            fileName = "chapter.pdf",
            mimeType = "application/pdf",
            uri = "content://sources/chapter.pdf",
            readText = { "Alpha content." }
        )

        assertEquals("chapter.pdf", input.fileName)
        assertEquals("application/pdf", input.mimeType)
        assertEquals("Alpha content.", input.text)
    }

    @Test
    fun imageSelectionReadsOcrTextForLocalParser() = runBlocking {
        val input = buildSourceImportInput(
            requestId = 13L,
            fileName = "question.png",
            mimeType = "image/png",
            uri = "content://sources/question.png",
            readText = { "扫描出来的题目文字" }
        )

        assertEquals("question.png", input.fileName)
        assertEquals("image/png", input.mimeType)
        assertEquals("扫描出来的题目文字", input.text)
    }

    @Test
    fun imageWithoutOcrTextStillCreatesImportInput() = runBlocking {
        val input = buildSourceImportInput(
            requestId = 15L,
            fileName = "diagram.png",
            mimeType = "image/png",
            uri = "content://sources/diagram.png",
            readText = { null }
        )

        assertEquals("diagram.png", input.fileName)
        assertNull(input.text)
    }

    @Test
    fun unreadableTextSelectionStillCreatesImportInput() = runBlocking {
        val input = buildSourceImportInput(
            requestId = 12L,
            fileName = "notes.txt",
            mimeType = "text/plain",
            uri = "content://sources/notes.txt",
            readText = { error("Cannot open input stream") }
        )

        assertEquals("notes.txt", input.fileName)
        assertEquals("text/plain", input.mimeType)
        assertNull(input.text)
    }

    @Test
    fun sourceTextReaderReturnsTextWithinLimit() {
        val text = readSourceTextWithinLimit(
            reader = StringReader("Alpha body"),
            maxChars = 20
        )

        assertEquals("Alpha body", text)
    }

    @Test
    fun sourceTextReaderReturnsNullWhenLimitExceeded() {
        val text = readSourceTextWithinLimit(
            reader = StringReader("123456"),
            maxChars = 5
        )

        assertNull(text)
    }
}
