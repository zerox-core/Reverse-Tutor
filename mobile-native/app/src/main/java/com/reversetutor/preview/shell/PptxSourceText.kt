package com.reversetutor.preview.shell

import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

/**
 * PPT（.pptx）资料文本提取（NEWMP-V1-023，知识锚点第三期补全）。
 *
 * pptx 本质是 zip：ppt/slides/slideN.xml 是每页幻灯片正文，
 * ppt/slides/_rels/slideN.xml.rels 登记该页引用的资源，
 * ppt/media/ 存图片。这里不用重型依赖，直接解 zip + DOM 按页码顺序还原：
 * 每页先抽文字（a:t，按形状在页面里的出现顺序、段落间换行），
 * 再把该页嵌入图片转成文字（先本地 OCR，OCR 读不出再云端多模态——
 * 复用 DocxSourceImport.kt 的转写链）。图片转写插回原页。
 *
 * 与 docx 一致：不按大小/尺寸跳过任何图片（NEWMP-V1-023 用户要求），
 * 单文档上限 [MaxEmbeddedImages] 张。
 *
 * 本文件不依赖 Android 类，方便 JVM 单元测试；Android 包装见
 * PptxSourceImport.kt。
 */

internal fun isPptxSource(fileName: String?, mimeType: String?): Boolean {
    if (mimeType != null &&
        mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation", ignoreCase = true)
    ) {
        return true
    }
    return fileName != null && fileName.substringAfterLast('.', "").equals("pptx", ignoreCase = true)
}

internal class PptxSlide(
    val index: Int,
    val blocks: List<DocxBlock>
)

/**
 * 把 pptx 字节流按页还原成文本：每页输出 "【第 N 页】" + 页内文字/图片转写，
 * 页与页之间空行分隔。全文没有任何可用文字时返回 null，交由上层标记
 * FutureAssisted。
 */
internal suspend fun extractPptxSourceText(
    input: InputStream,
    transcribeImage: suspend (DocxEmbeddedImage) -> String?
): String? = withContext(Dispatchers.IO) {
    val slides = runCatching { parsePptxSlides(input) }.getOrNull() ?: return@withContext null
    val parts = mutableListOf<String>()
    var transcribed = 0
    for (slide in slides) {
        val slideParts = mutableListOf<String>()
        for (block in slide.blocks) {
            when (block) {
                is DocxBlock.Text -> if (block.content.isNotBlank()) slideParts.add(block.content.trim())
                is DocxBlock.Image -> {
                    if (transcribed >= MaxEmbeddedImages) continue
                    transcribed += 1
                    val text = runCatching { transcribeImage(block.image) }.getOrNull()
                    if (!text.isNullOrBlank()) slideParts.add(text.trim())
                }
            }
        }
        if (slideParts.isNotEmpty()) {
            parts.add("【第 ${slide.index} 页】\n" + slideParts.joinToString("\n"))
        }
    }
    parts.joinToString("\n\n").trim().takeIf { it.isNotEmpty() }
}

internal fun parsePptxSlides(input: InputStream): List<PptxSlide> {
    val slideNumberRegex = Regex("ppt/slides/slide(\\d+)\\.xml")
    val slideRelsRegex = Regex("ppt/slides/_rels/slide(\\d+)\\.xml\\.rels")
    val slideXml = sortedMapOf<Int, String>()
    val slideRels = mutableMapOf<Int, String>()
    val media = mutableMapOf<String, ByteArray>()
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val name = entry.name
            when {
                slideNumberRegex.matchEntire(name) != null ->
                    slideXml[slideNumberRegex.matchEntire(name)!!.groupValues[1].toInt()] =
                        zip.readBytes().toString(Charsets.UTF_8)
                slideRelsRegex.matchEntire(name) != null ->
                    slideRels[slideRelsRegex.matchEntire(name)!!.groupValues[1].toInt()] =
                        zip.readBytes().toString(Charsets.UTF_8)
                name.startsWith("ppt/media/") && mediaMimeFor(name) != null ->
                    media[name] = zip.readBytes()
            }
        }
    }
    val slides = mutableListOf<PptxSlide>()
    for ((index, xml) in slideXml) {
        val rels = parseImageRels(slideRels[index])
        val root = runCatching { parseXmlRoot(xml) }.getOrNull() ?: continue
        val spTree = root.getElementsByTagNameNS("*", "spTree").item(0) as? Element ?: continue
        val blocks = mutableListOf<DocxBlock>()
        // 按形状在页面里的出现顺序处理：先文字后图，同一形状内的多张图按顺序跟随。
        for (shape in childElements(spTree)) {
            addPptxTextBlocks(shape, blocks)
            addPptxImageBlocks(shape, rels, media, blocks)
        }
        if (blocks.isNotEmpty()) slides.add(PptxSlide(index = index, blocks = blocks))
    }
    return slides
}

/** 单个形状（含表格 graphicFrame、组合 grpSp）里的文字：a:p 逐段成行，空行丢弃。 */
private fun addPptxTextBlocks(shape: Element, blocks: MutableList<DocxBlock>) {
    val paragraphs = shape.getElementsByTagNameNS("*", "p")
    val lines = mutableListOf<String>()
    for (i in 0 until paragraphs.length) {
        val paragraph = paragraphs.item(i) as? Element ?: continue
        val runs = paragraph.getElementsByTagNameNS("*", "t")
        val line = StringBuilder()
        for (j in 0 until runs.length) {
            line.append(runs.item(j).textContent)
        }
        val trimmed = line.toString().trim()
        if (trimmed.isNotEmpty()) lines.add(trimmed)
    }
    if (lines.isNotEmpty()) blocks.add(DocxBlock.Text(lines.joinToString("\n")))
}

/** 单个形状里的嵌入图片：a:blip r:embed → 本页 rels → ppt/media，按 rId 去重。 */
private fun addPptxImageBlocks(
    shape: Element,
    rels: Map<String, String>,
    media: Map<String, ByteArray>,
    blocks: MutableList<DocxBlock>
) {
    val blips = shape.getElementsByTagNameNS("*", "blip")
    if (blips.length == 0) return
    val seen = mutableSetOf<String>()
    for (i in 0 until blips.length) {
        val blip = blips.item(i) as? Element ?: continue
        val embedId = attributeByLocalName(blip, "embed") ?: continue
        if (!seen.add(embedId)) continue
        val target = rels[embedId] ?: continue
        val partName = resolveZipPartName("ppt/slides/", target) ?: continue
        val bytes = media[partName] ?: continue
        val mimeType = mediaMimeFor(partName) ?: continue
        blocks.add(DocxBlock.Image(DocxEmbeddedImage(partName = partName, mimeType = mimeType, bytes = bytes)))
    }
}

/**
 * 把 rels 里的相对 Target 解析成 zip 内完整路径（baseDir 以 / 结尾）。
 * pptx 里 Target 通常是 "../media/image1.png"，需要吃掉一层 ".."。
 * 绝对路径（以 / 开头）原样去前导斜杠；越出根目录视为非法。
 */
internal fun resolveZipPartName(baseDir: String, target: String): String? {
    val trimmed = target.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("/")) return trimmed.removePrefix("/")
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:")) return null
    val combined = (baseDir + trimmed).split('/')
    val stack = mutableListOf<String>()
    for (segment in combined) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.size - 1)
            else -> stack.add(segment)
        }
    }
    if (stack.isEmpty()) return null
    return stack.joinToString("/")
}
