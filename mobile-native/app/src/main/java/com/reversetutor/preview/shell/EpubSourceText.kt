package com.reversetutor.preview.shell

import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 电子书（.epub）资料文本提取（NEWMP-V1-023，知识锚点第三期补全）。
 *
 * epub 本质是 zip：META-INF/container.xml 指到 OPF 包描述文件，
 * OPF 的 manifest 登记全部资源、spine 决定章节阅读顺序，内容是 XHTML。
 * 这里不用重型依赖，直接解 zip + DOM 按 spine 顺序还原：每章抽可见文字
 * （跳过 script/style，块级标签换行），<img>/SVG <image> 引用的图片
 * 转成文字后插回原位置（先本地 OCR，OCR 读不出再云端多模态——复用
 * DocxSourceImport.kt 的转写链）。
 *
 * 与 docx 一致：不按大小/尺寸跳过任何图片（NEWMP-V1-023 用户要求），
 * 单本书上限 [MaxEmbeddedImages] 张。
 *
 * 本文件不依赖 Android 类，方便 JVM 单元测试；Android 包装见
 * EpubSourceImport.kt。
 */

internal fun isEpubSource(fileName: String?, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.equals("application/epub+zip", ignoreCase = true)) {
        return true
    }
    return fileName != null && fileName.substringAfterLast('.', "").equals("epub", ignoreCase = true)
}

internal class EpubChapter(
    val href: String,
    val blocks: List<DocxBlock>
)

/**
 * 把 epub 字节流按 spine 顺序还原成文本，章与章之间空行分隔。
 * 整本书没有任何可用文字时返回 null，交由上层标记 FutureAssisted。
 */
internal suspend fun extractEpubSourceText(
    input: InputStream,
    transcribeImage: suspend (DocxEmbeddedImage) -> String?
): String? = withContext(Dispatchers.IO) {
    val chapters = runCatching { parseEpubChapters(input) }.getOrNull() ?: return@withContext null
    val parts = mutableListOf<String>()
    var transcribed = 0
    for (chapter in chapters) {
        val chapterParts = mutableListOf<String>()
        for (block in chapter.blocks) {
            when (block) {
                is DocxBlock.Text -> if (block.content.isNotBlank()) chapterParts.add(block.content.trim())
                is DocxBlock.Image -> {
                    if (transcribed >= MaxEmbeddedImages) continue
                    transcribed += 1
                    val text = runCatching { transcribeImage(block.image) }.getOrNull()
                    if (!text.isNullOrBlank()) chapterParts.add(text.trim())
                }
            }
        }
        if (chapterParts.isNotEmpty()) {
            parts.add(chapterParts.joinToString("\n"))
        }
    }
    parts.joinToString("\n\n").trim().takeIf { it.isNotEmpty() }
}

private class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String
)

internal fun parseEpubChapters(input: InputStream): List<EpubChapter> {
    var containerXml: String? = null
    val opfXml = sortedMapOf<String, String>()
    val contentFiles = mutableMapOf<String, ByteArray>()
    val media = mutableMapOf<String, ByteArray>()
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val name = entry.name
            when {
                name == "META-INF/container.xml" ->
                    containerXml = zip.readBytes().toString(Charsets.UTF_8)
                name.endsWith(".opf", ignoreCase = true) ->
                    opfXml[name] = zip.readBytes().toString(Charsets.UTF_8)
                name.endsWith(".xhtml", true) || name.endsWith(".html", true) || name.endsWith(".htm", true) ->
                    contentFiles[name] = zip.readBytes()
                mediaMimeFor(name) != null ->
                    media[name] = zip.readBytes()
            }
        }
    }
    if (opfXml.isEmpty()) return emptyList()
    // 正常入口：container.xml 指向 OPF；缺失时退回第一份 .opf（宽松容错）。
    val opfPath = resolveOpfPath(containerXml) ?: opfXml.firstKey()
    val opf = opfXml[opfPath] ?: return emptyList()
    val opfDir = opfPath.substringBeforeLast('/', "")
    val opfRoot = runCatching { parseXmlRoot(opf) }.getOrNull() ?: return emptyList()

    val manifest = mutableMapOf<String, EpubManifestItem>()
    val manifestItems = opfRoot.getElementsByTagNameNS("*", "item")
    for (i in 0 until manifestItems.length) {
        val item = manifestItems.item(i) as? Element ?: continue
        val id = item.getAttribute("id")
        val href = item.getAttribute("href")
        val mediaType = item.getAttribute("media-type")
        if (id.isNotEmpty() && href.isNotEmpty()) {
            manifest[id] = EpubManifestItem(id = id, href = href, mediaType = mediaType)
        }
    }
    val manifestByHref = manifest.values.associateBy { it.href }

    val chapters = mutableListOf<EpubChapter>()
    val spine = opfRoot.getElementsByTagNameNS("*", "itemref")
    for (i in 0 until spine.length) {
        val itemref = spine.item(i) as? Element ?: continue
        val idref = itemref.getAttribute("idref")
        val item = manifest[idref] ?: continue
        val hrefNoFragment = item.href.substringBefore('#')
        val basePath = if (opfDir.isEmpty()) "" else "$opfDir/"
        val chapterPath = resolveZipPartName(basePath, hrefNoFragment) ?: continue
        val bytes = contentFiles[chapterPath] ?: continue
        val root = runCatching { parseXhtmlRoot(bytes.toString(Charsets.UTF_8)) }.getOrNull() ?: continue
        val body = root.getElementsByTagNameNS("*", "body").item(0) as? Element ?: root
        val blocks = mutableListOf<DocxBlock>()
        val text = StringBuilder()
        walkXhtml(body, text, blocks, chapterPath.substringBeforeLast('/', "") + "/", media, manifestByHref, mutableSetOf())
        // 章节走完后冲刷剩余文字（图片之后的正文尾巴）。
        flushText(text)?.let { blocks.add(DocxBlock.Text(it)) }
        if (blocks.isNotEmpty()) chapters.add(EpubChapter(href = chapterPath, blocks = blocks))
    }
    return chapters
}

/** 把积累的原始文字压成规整块：逐行去空白、空行丢弃；没内容返回 null。 */
private fun flushText(text: StringBuilder): String? {
    val normalized = text.toString()
        .lines()
        .map { it.trim().replace(Regex("[ \\t\\x0B\\f]+"), " ") }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
    return normalized.takeIf { it.isNotEmpty() }
}

/**
 * XHTML 逐节点走：文字累进；遇到 <img>/SVG <image> 先冲刷已积累文字、
 * 插入图片块，保证转写结果落在原文位置。
 */
private fun walkXhtml(
    element: Element,
    text: StringBuilder,
    blocks: MutableList<DocxBlock>,
    contentDir: String,
    media: Map<String, ByteArray>,
    manifestByHref: Map<String, EpubManifestItem>,
    seenImages: MutableSet<String>
) {
    fun flush() {
        flushText(text)?.let { blocks.add(DocxBlock.Text(it)) }
        text.setLength(0)
    }

    when (element.localName) {
        "script", "style", "head", "nav" -> return
        "img", "image" -> {
            val src = attributeByLocalName(element, "src")
                ?: attributeByLocalName(element, "href")
                ?: attributeByLocalName(element, "xlink:href")
            if (src != null) {
                val path = resolveZipPartName(contentDir, src.substringBefore('#'))
                if (path != null && seenImages.add(path)) {
                    val bytes = media[path]
                    val mimeType = mediaMimeFor(path)
                        ?: manifestByHref[src.substringBefore('#')]?.mediaType
                        ?.takeIf { it in setOf("image/png", "image/jpeg", "image/gif", "image/bmp", "image/webp") }
                    if (bytes != null && mimeType != null) {
                        flush()
                        blocks.add(DocxBlock.Image(DocxEmbeddedImage(partName = path, mimeType = mimeType, bytes = bytes)))
                    }
                }
            }
            // <img> 里的 alt 文字不再重复收集，图片单独处理
            return
        }
        "br" -> {
            text.append('\n')
            return
        }
    }
    val blockTags = setOf(
        "p", "div", "section", "article", "header", "footer", "main", "aside",
        "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
        "table", "tr", "blockquote", "figure", "figcaption", "pre", "hr"
    )
    if (element.localName in blockTags) text.append('\n')
    val children = element.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        when (node) {
            is Element -> walkXhtml(node, text, blocks, contentDir, media, manifestByHref, seenImages)
            is Node -> if (node.nodeType == Node.TEXT_NODE) {
                val value = node.nodeValue ?: continue
                text.append(value)
            }
        }
    }
    if (element.localName in blockTags) text.append('\n')
}

private fun resolveOpfPath(containerXml: String?): String? {
    if (containerXml == null) return null
    val root = runCatching { parseXmlRoot(containerXml) }.getOrNull() ?: return null
    val rootfiles = root.getElementsByTagNameNS("*", "rootfile")
    for (i in 0 until rootfiles.length) {
        val rootfile = rootfiles.item(i) as? Element ?: continue
        val fullPath = rootfile.getAttribute("full-path")
        if (fullPath.isNotEmpty()) return fullPath
    }
    return null
}

/**
 * XHTML 解析。与 [parseXmlRoot] 的区别：epub2 的 XHTML 常带 DOCTYPE，
 * 这里不禁 DOCTYPE（只是不加载外部 DTD、不展开外部实体），其余 XXE 防护一致。
 */
private fun parseXhtmlRoot(xml: String): Element {
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    return factory.newDocumentBuilder()
        .parse(java.io.ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        .documentElement
}
