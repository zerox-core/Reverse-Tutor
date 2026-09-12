package com.reversetutor.preview.shell

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxSourceTextTest {

    @Test
    fun paragraphsAndEmbeddedImagesKeepDocumentOrder() = runBlocking {
        val docx = buildDocx(
            bodyXml = """
                <w:p><w:r><w:t>第一章 需求背景</w:t></w:r></w:p>
                <w:p><w:r><w:t>下图是流程：</w:t></w:r><w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r></w:p>
                <w:p><w:r><w:t>第二章 范围</w:t></w:r></w:p>
            """,
            rels = mapOf("rId1" to "media/image1.png"),
            media = mapOf("word/media/image1.png" to ByteArray(8192) { 1 })
        )

        val text = extractDocxSourceText(ByteArrayInputStream(docx)) { image ->
            assertEquals("image/png", image.mimeType)
            "【流程图转写】打开 App 后进入首页"
        }

        assertEquals(
            listOf("第一章 需求背景", "下图是流程：", "【流程图转写】打开 App 后进入首页", "第二章 范围"),
            text!!.split("\n\n")
        )
    }

    @Test
    fun tableRowsFlattenToPipeSeparatedText() = runBlocking {
        val docx = buildDocx(
            bodyXml = """
                <w:p><w:r><w:t>指标表</w:t></w:r></w:p>
                <w:tbl>
                  <w:tr><w:tc><w:p><w:r><w:t>名称</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>目标</w:t></w:r></w:p></w:tc></w:tr>
                  <w:tr><w:tc><w:p><w:r><w:t>留存率</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>80%</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
            """
        )

        val text = extractDocxSourceText(ByteArrayInputStream(docx)) { null }

        assertEquals(listOf("指标表", "名称 | 目标", "留存率 | 80%"), text!!.split("\n\n"))
    }

    @Test
    fun tinyDecorativeImagesAreSkipped() = runBlocking {
        val docx = buildDocx(
            bodyXml = """
                <w:p><w:r><w:t>正文</w:t></w:r><w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r></w:p>
            """,
            rels = mapOf("rId1" to "media/bullet.png"),
            media = mapOf("word/media/bullet.png" to ByteArray(200) { 2 })
        )
        var calls = 0

        val text = extractDocxSourceText(ByteArrayInputStream(docx)) {
            calls += 1
            "不应出现"
        }

        assertEquals(0, calls)
        assertEquals("正文", text)
    }

    @Test
    fun embeddedImageCountIsCapped() = runBlocking {
        val rels = (1..40).associate { "rId$it" to "media/image$it.png" }
        val media = (1..40).associate { "word/media/image$it.png" to ByteArray(8192) { it.toByte() } }
        val body = (1..40).joinToString("") {
            "<w:p><w:r><w:drawing><a:blip r:embed=\"rId$it\"/></w:drawing></w:r></w:p>"
        }
        var calls = 0

        val text = extractDocxSourceText(ByteArrayInputStream(buildDocx(body, rels, media))) {
            calls += 1
            "图转写"
        }

        assertEquals(MaxEmbeddedImages, calls)
        assertEquals(MaxEmbeddedImages, text!!.split("\n\n").size)
    }

    @Test
    fun imageWithoutTranscriptionIsDropped() = runBlocking {
        val docx = buildDocx(
            bodyXml = """
                <w:p><w:r><w:t>上文</w:t></w:r></w:p>
                <w:p><w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r></w:p>
                <w:p><w:r><w:t>下文</w:t></w:r></w:p>
            """,
            rels = mapOf("rId1" to "media/image1.png"),
            media = mapOf("word/media/image1.png" to ByteArray(8192) { 1 })
        )

        val text = extractDocxSourceText(ByteArrayInputStream(docx)) { null }

        assertEquals(listOf("上文", "下文"), text!!.split("\n\n"))
    }

    @Test
    fun documentWithoutAnyReadableTextReturnsNull() = runBlocking {
        val docx = buildDocx(
            bodyXml = """
                <w:p><w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r></w:p>
            """,
            rels = mapOf("rId1" to "media/image1.png"),
            media = mapOf("word/media/image1.png" to ByteArray(8192) { 1 })
        )

        assertNull(extractDocxSourceText(ByteArrayInputStream(docx)) { null })
    }

    @Test
    fun invalidZipReturnsNull() = runBlocking {
        assertNull(
            extractDocxSourceText(ByteArrayInputStream("not a zip".toByteArray())) { "x" }
        )
    }

    @Test
    fun docxDetectionCoversMimeAndExtension() {
        assertTrue(isDocxSource("prd.docx", null))
        assertTrue(isDocxSource("PRD.DOCX", null))
        assertTrue(isDocxSource(null, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertTrue(!isDocxSource("slides.pptx", null))
        assertTrue(!isDocxSource(null, "application/pdf"))
    }

    private fun buildDocx(
        bodyXml: String,
        rels: Map<String, String> = emptyMap(),
        media: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
            <w:body>$bodyXml</w:body></w:document>"""
        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            rels.entries.joinToString("") { (id, target) ->
                """<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="$target"/>"""
            } + "</Relationships>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(relsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, bytes) in media) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
