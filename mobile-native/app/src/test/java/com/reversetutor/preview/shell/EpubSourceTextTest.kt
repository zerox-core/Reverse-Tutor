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

class EpubSourceTextTest {

    @Test
    fun chaptersFollowSpineOrderWithImageTranscription() = runBlocking {
        val epub = buildEpub(
            chapters = listOf(
                "chapter1" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <body>
                        <h1>第一章 起点</h1>
                        <p>主角离开了村庄。</p>
                        <p><img src="images/map.png"/></p>
                        <p>向着北方前进。</p>
                    </body></html>
                """,
                "chapter2" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <body><p>第二章 归途。</p></body></html>
                """
            ),
            media = mapOf("OEBPS/images/map.png" to ByteArray(1024) { 1 })
        )

        val text = extractEpubSourceText(ByteArrayInputStream(epub)) { image ->
            assertEquals("image/png", image.mimeType)
            "【插图转写】北方山脉地形图"
        }

        assertEquals(
            listOf(
                "第一章 起点\n主角离开了村庄。\n【插图转写】北方山脉地形图\n向着北方前进。",
                "第二章 归途。"
            ),
            text!!.split("\n\n")
        )
    }

    @Test
    fun doctypeXhtmlStillParses() = runBlocking {
        val epub = buildEpub(
            chapters = listOf(
                "chapter1" to """
                    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <body><p>带 DOCTYPE 的旧式电子书内容。</p></body></html>
                """
            )
        )

        val text = extractEpubSourceText(ByteArrayInputStream(epub)) { null }

        assertEquals("带 DOCTYPE 的旧式电子书内容。", text)
    }

    @Test
    fun scriptAndStyleTextIsSkipped() = runBlocking {
        val epub = buildEpub(
            chapters = listOf(
                "chapter1" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head><style>p { color: red; }</style></head>
                    <body>
                        <p>正文内容。</p>
                        <script>var hidden = "should not appear";</script>
                    </body></html>
                """
            )
        )

        val text = extractEpubSourceText(ByteArrayInputStream(epub)) { null }

        assertEquals("正文内容。", text)
    }

    @Test
    fun tinyImagesAreAlsoTranscribed() = runBlocking {
        val epub = buildEpub(
            chapters = listOf(
                "chapter1" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <body><p>文字</p><p><img src="images/dot.png"/></p></body></html>
                """
            ),
            media = mapOf("OEBPS/images/dot.png" to ByteArray(200) { 2 })
        )
        var calls = 0

        val text = extractEpubSourceText(ByteArrayInputStream(epub)) {
            calls += 1
            "【小图转写】"
        }

        assertEquals(1, calls)
        assertEquals("文字\n【小图转写】", text)
    }

    @Test
    fun chaptersWithOnlyUntranscribableImagesAreDropped() = runBlocking {
        val epub = buildEpub(
            chapters = listOf(
                "chapter1" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <body><p>唯一章节</p></body></html>
                """,
                "chapter2" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <body><p><img src="images/cover.png"/></p></body></html>
                """
            ),
            media = mapOf("OEBPS/images/cover.png" to ByteArray(1024) { 1 })
        )

        val text = extractEpubSourceText(ByteArrayInputStream(epub)) { null }

        assertEquals("唯一章节", text)
    }

    @Test
    fun invalidZipReturnsNull() = runBlocking {
        assertNull(
            extractEpubSourceText(ByteArrayInputStream("not a zip".toByteArray())) { "x" }
        )
    }

    @Test
    fun epubDetectionCoversMimeAndExtension() {
        assertTrue(isEpubSource("book.epub", null))
        assertTrue(isEpubSource("BOOK.EPUB", null))
        assertTrue(isEpubSource(null, "application/epub+zip"))
        assertTrue(!isEpubSource("deck.pptx", null))
        assertTrue(!isEpubSource(null, "application/pdf"))
    }

    private fun buildEpub(
        chapters: List<Pair<String, String>>,
        media: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""
        val opfXml = """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
            <metadata/><manifest>""" +
            chapters.joinToString("") { (id, _) ->
                """<item id="$id" href="$id.xhtml" media-type="application/xhtml+xml"/>"""
            } +
            media.entries.joinToString("") { (name, _) ->
                val href = name.removePrefix("OEBPS/")
                """<item id="img-${href.substringAfterLast('/')}" href="$href" media-type="image/png"/>"""
            } +
            """</manifest><spine>""" +
            chapters.joinToString("") { (id, _) -> """<itemref idref="$id"/>""" } +
            "</spine></package>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(containerXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opfXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((id, xhtml) in chapters) {
                zip.putNextEntry(ZipEntry("OEBPS/$id.xhtml"))
                zip.write(xhtml.trimIndent().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            for ((name, bytes) in media) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
