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

class PptxSourceTextTest {

    @Test
    fun slidesKeepOrderWithTextAndImageTranscription() = runBlocking {
        val pptx = buildPptx(
            slides = listOf(
                buildSlideXml(
                    """
                    <p:sp><p:txBody><a:p><a:r><a:t>产品定位</a:t></a:r></a:p></p:txBody></p:sp>
                    <p:pic><p:blipFill><a:blip r:embed="rId1"/></p:blipFill></p:pic>
                    """,
                    rels = mapOf("rId1" to "../media/image1.png")
                ),
                buildSlideXml(
                    """
                    <p:sp><p:txBody><a:p><a:r><a:t>第二章 竞品分析</a:t></a:r></a:p></p:txBody></p:sp>
                    """
                )
            ),
            media = mapOf("ppt/media/image1.png" to ByteArray(1024) { 1 })
        )

        val text = extractPptxSourceText(ByteArrayInputStream(pptx)) { image ->
            assertEquals("image/png", image.mimeType)
            "【插图转写】竞品功能对比矩阵"
        }

        assertEquals(
            listOf(
                "【第 1 页】\n产品定位\n【插图转写】竞品功能对比矩阵",
                "【第 2 页】\n第二章 竞品分析"
            ),
            text!!.split("\n\n")
        )
    }

    @Test
    fun slideParagraphsBecomeLines() = runBlocking {
        val pptx = buildPptx(
            slides = listOf(
                buildSlideXml(
                    """
                    <p:sp><p:txBody>
                        <a:p><a:r><a:t>要点一</a:t></a:r></a:p>
                        <a:p><a:r><a:t>要点二</a:t></a:r></a:p>
                    </p:txBody></p:sp>
                    """
                )
            )
        )

        val text = extractPptxSourceText(ByteArrayInputStream(pptx)) { null }

        assertEquals("【第 1 页】\n要点一\n要点二", text)
    }

    @Test
    fun tinyImagesAreAlsoTranscribed() = runBlocking {
        val pptx = buildPptx(
            slides = listOf(
                buildSlideXml(
                    """
                    <p:sp><p:txBody><a:p><a:r><a:t>正文</a:t></a:r></a:p></p:txBody></p:sp>
                    <p:pic><p:blipFill><a:blip r:embed="rId1"/></p:blipFill></p:pic>
                    """,
                    rels = mapOf("rId1" to "../media/icon.png")
                )
            ),
            media = mapOf("ppt/media/icon.png" to ByteArray(200) { 2 })
        )
        var calls = 0

        val text = extractPptxSourceText(ByteArrayInputStream(pptx)) {
            calls += 1
            "【小图转写】"
        }

        assertEquals(1, calls)
        assertEquals("【第 1 页】\n正文\n【小图转写】", text)
    }

    @Test
    fun slidesWithoutTextOrUsableImagesAreSkipped() = runBlocking {
        val pptx = buildPptx(
            slides = listOf(
                buildSlideXml("""<p:sp><p:txBody><a:p><a:r><a:t>唯一一页</a:t></a:r></a:p></p:txBody></p:sp>"""),
                buildSlideXml("""<p:sp><p:txBody><a:p><a:endParaRPr/></a:p></p:txBody></p:sp>""")
            )
        )

        val text = extractPptxSourceText(ByteArrayInputStream(pptx)) { null }

        assertEquals("【第 1 页】\n唯一一页", text)
    }

    @Test
    fun slidesAreSortedNumericallyNotLexicographically() = runBlocking {
        val pptx = buildPptx(
            slides = (1..11).map { index ->
                buildSlideXml(
                    """<p:sp><p:txBody><a:p><a:r><a:t>第${index}页内容</a:t></a:r></a:p></p:txBody></p:sp>"""
                )
            }
        )

        val text = extractPptxSourceText(ByteArrayInputStream(pptx)) { null }

        val firstPage = text!!.split("\n\n").first()
        assertTrue(firstPage.contains("第1页内容"))
        assertTrue(text.split("\n\n").last().contains("第11页内容"))
    }

    @Test
    fun invalidZipReturnsNull() = runBlocking {
        assertNull(
            extractPptxSourceText(ByteArrayInputStream("not a zip".toByteArray())) { "x" }
        )
    }

    @Test
    fun pptxDetectionCoversMimeAndExtension() {
        assertTrue(isPptxSource("deck.pptx", null))
        assertTrue(isPptxSource("DECK.PPTX", null))
        assertTrue(isPptxSource(null, "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        assertTrue(!isPptxSource("prd.docx", null))
        assertTrue(!isPptxSource(null, "application/pdf"))
    }

    private fun buildSlideXml(bodyXml: String, rels: Map<String, String> = emptyMap()): Pair<String, Map<String, String>> =
        bodyXml to rels

    private fun buildPptx(
        slides: List<Pair<String, Map<String, String>>>,
        media: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            slides.forEachIndexed { index, (bodyXml, rels) ->
                val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:cSld><p:spTree>$bodyXml</p:spTree></p:cSld></p:sld>"""
                val number = index + 1
                zip.putNextEntry(ZipEntry("ppt/slides/slide$number.xml"))
                zip.write(slideXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (rels.isNotEmpty()) {
                    val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                        rels.entries.joinToString("") { (id, target) ->
                            """<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="$target"/>"""
                        } + "</Relationships>"
                    zip.putNextEntry(ZipEntry("ppt/slides/_rels/slide$number.xml.rels"))
                    zip.write(relsXml.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
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
