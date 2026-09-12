package com.reversetutor.preview.shell

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Word（.docx）资料文本提取（NEWMP-V1-021，知识锚点第三期）。
 *
 * docx 本质是 zip：word/document.xml 是正文，word/media/ 是嵌入图片。
 * 这里不用任何重型依赖，直接解 zip + DOM 按文档顺序还原正文：
 * 段落抽文字、表格按行展开成 "a | b | c"、嵌入图片在原始位置转成文字
 * （先本地 OCR，OCR 读不出再由上层决定是否调云端多模态——见
 * [transcribeDocxEmbeddedImage]）。图片转写插回原位置，上下文不跑丢。
 *
 * 本文件不依赖 Android 类，方便 JVM 单元测试；Android 包装见
 * DocxSourceImport.kt。
 */

internal fun isDocxSource(fileName: String?, mimeType: String?): Boolean {
    if (mimeType != null &&
        mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ignoreCase = true)
    ) {
        return true
    }
    return fileName != null && fileName.substringAfterLast('.', "").equals("docx", ignoreCase = true)
}

/** 单文档最多处理的嵌入图片数（防超大文档把导入拖死 / 云端转写费用失控）。 */
internal const val MaxEmbeddedImages = 30

/** 小于该字节数的嵌入图直接跳过——基本是项目符号、logo、装饰线，转写只会添噪。 */
internal const val MinEmbeddedImageBytes = 3072

internal class DocxEmbeddedImage(
    val partName: String,
    val mimeType: String,
    val bytes: ByteArray
)

internal sealed interface DocxBlock {
    class Text(val content: String) : DocxBlock
    class Image(val image: DocxEmbeddedImage) : DocxBlock
}

/**
 * 把 docx 字节流按文档顺序还原成文本：段落/表格原样保留，
 * 嵌入图片用 [transcribeImage] 的转写结果替换；转写不出就丢弃该图。
 * 全文没有任何可用文字时返回 null，交由上层标记 FutureAssisted。
 */
internal suspend fun extractDocxSourceText(
    input: InputStream,
    transcribeImage: suspend (DocxEmbeddedImage) -> String?
): String? = withContext(Dispatchers.IO) {
    val blocks = runCatching { parseDocxBlocks(input) }.getOrNull() ?: return@withContext null
    val parts = mutableListOf<String>()
    var transcribed = 0
    for (block in blocks) {
        when (block) {
            is DocxBlock.Text -> if (block.content.isNotBlank()) parts.add(block.content.trim())
            is DocxBlock.Image -> {
                if (block.image.bytes.size < MinEmbeddedImageBytes) continue
                if (transcribed >= MaxEmbeddedImages) continue
                transcribed += 1
                val text = runCatching { transcribeImage(block.image) }.getOrNull()
                if (!text.isNullOrBlank()) parts.add(text.trim())
            }
        }
    }
    parts.joinToString("\n\n").trim().takeIf { it.isNotEmpty() }
}

internal fun parseDocxBlocks(input: InputStream): List<DocxBlock> {
    var documentXml: String? = null
    var relsXml: String? = null
    val media = mutableMapOf<String, ByteArray>()
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val name = entry.name
            when {
                name == "word/document.xml" ->
                    documentXml = zip.readBytes().toString(Charsets.UTF_8)
                name == "word/_rels/document.xml.rels" ->
                    relsXml = zip.readBytes().toString(Charsets.UTF_8)
                name.startsWith("word/media/") && mediaMimeFor(name) != null ->
                    media[name] = zip.readBytes()
            }
        }
    }
    val document = documentXml ?: return emptyList()
    val rels = parseImageRels(relsXml)
    val body = childElements(parseXmlRoot(document), "body").firstOrNull() ?: return emptyList()
    val blocks = mutableListOf<DocxBlock>()
    for (child in childElements(body)) {
        walkBlocks(child, blocks, rels, media)
    }
    return blocks
}

private fun walkBlocks(
    element: Element,
    blocks: MutableList<DocxBlock>,
    rels: Map<String, String>,
    media: Map<String, ByteArray>
) {
    when (element.localName) {
        "p" -> {
            val text = collectText(element)
            if (text.isNotBlank()) blocks.add(DocxBlock.Text(text))
            blocks.addAll(collectImages(element, rels, media).map { DocxBlock.Image(it) })
        }
        "tbl" -> {
            for (row in childElements(element, "tr")) {
                val cells = childElements(row, "tc")
                    .map { collectText(it).trim() }
                    .filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) blocks.add(DocxBlock.Text(cells.joinToString(" | ")))
                blocks.addAll(collectImages(row, rels, media).map { DocxBlock.Image(it) })
            }
        }
        else -> for (child in childElements(element)) walkBlocks(child, blocks, rels, media)
    }
}

/** 段落/单元格内的可见文字：w:t 原文、w:tab 制表符、w:br/w:cr 换行。 */
private fun collectText(element: Element): String {
    val out = StringBuilder()
    fun walk(node: Node) {
        if (node !is Element) return
        when (node.localName) {
            "t" -> out.append(node.textContent)
            "tab" -> out.append('\t')
            "br", "cr" -> out.append('\n')
            // 图形/文本框里的文字不重复收集（图片单独处理）
            "drawing", "pict" -> Unit
            else -> {
                val children = node.childNodes
                for (i in 0 until children.length) walk(children.item(i))
            }
        }
    }
    walk(element)
    return out.toString()
}

/** 段落/表格行内的嵌入图片：a:blip r:embed（含 VML 的 v:imagedata r:id），按 rId 去重。 */
private fun collectImages(
    element: Element,
    rels: Map<String, String>,
    media: Map<String, ByteArray>
): List<DocxEmbeddedImage> {
    val out = mutableListOf<DocxEmbeddedImage>()
    val seen = mutableSetOf<String>()
    fun addFrom(node: Element, attrLocalName: String) {
        val embedId = attributeByLocalName(node, attrLocalName) ?: return
        if (!seen.add(embedId)) return
        val partName = rels[embedId]?.let { normalizePartName(it) } ?: return
        val bytes = media[partName] ?: return
        val mimeType = mediaMimeFor(partName) ?: return
        out.add(DocxEmbeddedImage(partName = partName, mimeType = mimeType, bytes = bytes))
    }
    val blips = element.getElementsByTagNameNS("*", "blip")
    for (i in 0 until blips.length) {
        (blips.item(i) as? Element)?.let { addFrom(it, "embed") }
    }
    val vml = element.getElementsByTagNameNS("*", "imagedata")
    for (i in 0 until vml.length) {
        (vml.item(i) as? Element)?.let { addFrom(it, "id") }
    }
    return out
}

private fun parseImageRels(relsXml: String?): Map<String, String> {
    if (relsXml == null) return emptyMap()
    val root = runCatching { parseXmlRoot(relsXml) }.getOrNull() ?: return emptyMap()
    val out = mutableMapOf<String, String>()
    for (rel in childElements(root, "Relationship")) {
        val id = rel.getAttribute("Id")
        val type = rel.getAttribute("Type")
        val target = rel.getAttribute("Target")
        if (id.isNotEmpty() && target.isNotEmpty() && type.endsWith("/image")) {
            out[id] = target
        }
    }
    return out
}

/** rels 里的 Target 相对 word/ 目录，归一成完整 part 名。 */
private fun normalizePartName(target: String): String? {
    val trimmed = target.trim()
    if (trimmed.isEmpty() || trimmed.contains("..")) return null
    if (trimmed.startsWith("/")) return trimmed.removePrefix("/")
    return "word/" + trimmed
}

private fun mediaMimeFor(partName: String): String? =
    when (partName.lowercase().substringAfterLast('.', "")) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        else -> null
    }

private fun attributeByLocalName(element: Element, localName: String): String? {
    val attrs = element.attributes
    for (i in 0 until attrs.length) {
        val attr = attrs.item(i)
        if (attr.localName == localName || attr.nodeName == localName) {
            return attr.nodeValue.takeIf { it.isNotEmpty() }
        }
    }
    return null
}

private fun childElements(element: Element, localName: String? = null): List<Element> {
    val out = mutableListOf<Element>()
    val children = element.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node is Element && (localName == null || node.localName == localName)) out.add(node)
    }
    return out
}

private fun parseXmlRoot(xml: String): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // 防 XXE：docx 来自用户文件，禁 DOCTYPE 与外部实体
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    return factory.newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        .documentElement
}
