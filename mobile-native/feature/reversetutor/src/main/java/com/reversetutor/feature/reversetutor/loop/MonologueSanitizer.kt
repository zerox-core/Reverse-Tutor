package com.reversetutor.feature.reversetutor.loop

/**
 * 模型输出卫生（2026-10-15 真机反馈）：
 *
 * 提示词约定独白走 `<thinking>` 信封，但部分模型（实测 Qwen 系经中转）会用
 * QQ 风格的灰色小字标记表达「内部推理」，例如
 * `<font color="#999999" size="1">（注：以上思考过程为内部推理，不对外展示）</font>`。
 * 这种标记既不是信封也不是正文，此前会原样漏进学生气泡（截图实证）。
 *
 * 两步处理：
 * 1. [normalizeForEnvelope] 把灰色内部块归一成 `<thinking>` 信封 → 继续走既有
 *    独白链路（进内心独白抽屉）；若该块内容只是「不要外露」这类元信息注解，直接丢弃，
 *    避免抽屉里出现废话。
 * 2. [stripTags] 兜底剥掉正文里残留的富文本标签，任何模型怪癖都不会裸奔到用户面前。
 */
internal object MonologueSanitizer {

    /** QQ 风格灰色小字块：整块内容视为模型想藏在内部的推理。 */
    private val grayFontBlock = Regex(
        """<font\s+[^>]*color\s*=\s*["']?#?999999["']?[^>]*>(.*?)</font>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 其他富文本标签（含闭合标签）：只剥标签，保留中间文字。 */
    private val anyTag = Regex("""</?[A-Za-z][^>]{0,160}>""")

    /** 纯元信息注解：模型把「以上是内部推理」的说明写进了独白，进抽屉也是噪音。 */
    private val metaNote = Regex("""(不对外展示|以上思考过程|内部推理|不要外露|不应展示)""")

    /**
     * 把灰色内部块归一成 `<thinking>` 信封。
     * 元信息注解块直接删除；其余原样保留（未闭合的 `<font …>` 由 [stripTags] 兜底）。
     */
    fun normalizeForEnvelope(raw: String): String {
        if (raw.isEmpty() || !raw.contains("<font", ignoreCase = true)) return raw
        return grayFontBlock.replace(raw) { match ->
            val inner = match.groupValues.getOrNull(1).orEmpty().trim()
            when {
                inner.isEmpty() -> ""
                isMetaNote(inner) -> ""
                else -> "<thinking>$inner</thinking>"
            }
        }
    }

    /** 剥离残留富文本标签（正文兜底）。 */
    fun stripTags(text: String): String =
        if (!text.contains('<')) text.trim() else anyTag.replace(text, "").trim()

    fun isMetaNote(text: String): Boolean = metaNote.containsMatchIn(text)
}
