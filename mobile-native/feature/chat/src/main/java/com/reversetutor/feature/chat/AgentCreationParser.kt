package com.reversetutor.feature.chat

/**
 * P2' 契约 JSON 解析。
 *
 * 设计约束：
 * - LLM 可能输出 markdown 围栏 / 前后杂文，先提取首个平衡的 `{...}` 对象再解析；
 * - 解析永不抛异常，失败返回 null，由协调器走「自动原样重试 1 次 → 仍失败按生成失败」降级；
 * - draft 只保留实际出现的字段（字段级补丁），叠加语义由 [AgentCreationDraftPatch.applyTo] 保证。
 */
object AgentCreationParser {

    fun parseTurnResult(rawText: String): AgentCreationTurnResult? {
        val json = extractJsonObject(rawText) ?: return null
        val root = StrictJson.parseObject(json) ?: return null
        val understanding = (root["understanding"] as? JsonValue.Decimal)
            ?.value?.toDoubleOrNull()?.toInt()?.coerceIn(0, 100) ?: 0
        val followUp = root.text("followUpQuestion")
        val note = root.text("assistantNote")
        val requestDocument = (root["requestDocument"] as? JsonValue.Flag)?.value == true
        val draft = (root["draft"] as? JsonValue.Object)?.let(::parseDraftPatch)
        if (followUp == null && note == null && draft == null) return null
        return AgentCreationTurnResult(
            understanding = understanding,
            followUpQuestion = followUp,
            assistantNote = note,
            requestDocument = requestDocument,
            draft = draft
        )
    }

    fun parseDocAnalysis(rawText: String): AgentCreationDocAnalysis? {
        val json = extractJsonObject(rawText) ?: return null
        val root = StrictJson.parseObject(json) ?: return null
        val title = root.text("materialTitle") ?: return null
        return AgentCreationDocAnalysis(
            materialTitle = title,
            materialType = root.text("materialType") ?: "其他",
            outline = root.textList("outline", maxItems = 20),
            knowledgePoints = root.textList("knowledgePoints", maxItems = 30),
            difficulty = (root["difficulty"] as? JsonValue.Decimal)
                ?.value?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f,
            prerequisites = root.textList("prerequisites", maxItems = 10),
            suggestedPath = root.textList("suggestedPath", maxItems = 12),
            summary = root.text("summary") ?: ""
        )
    }

    /**
     * R93：从「还没生成完的契约 JSON 前缀」宽容抽取当前可见口语文本——
     * 字符串未闭合、结构残缺都不报错。只认 assistantNote / followUpQuestion
     * 两个展示字段（草案补丁等不上屏），按 JSON 出现顺序换行拼接；
     * 返回 null = 暂时抽不出可展示文本（保持现态）。
     */
    fun extractPartialSpoken(rawSoFar: String): String? {
        val fields = listOf("assistantNote", "followUpQuestion")
            .mapNotNull { key ->
                val keyIndex = rawSoFar.indexOf("\"$key\"")
                if (keyIndex < 0) {
                    null
                } else {
                    extractStringFieldPrefix(rawSoFar, key)?.let { keyIndex to it.first }
                }
            }
            .sortedBy { it.first }
            .map { it.second.trim() }
            .filter { it.isNotEmpty() }
        if (fields.isEmpty()) return null
        return fields.joinToString("\n").take(MAX_FIELD_TEXT)
    }

    /**
     * 宽容字符串字段前缀读取：定位 key 后的字符串值，读到未转义的闭合引号为止；
     * 流中断（未闭合）读到末尾，complete=false。转义按 JSON 语义还原，
     * \uXXXX 残部直接舍弃。字段未出现或值不是字符串返回 null。
     */
    private fun extractStringFieldPrefix(raw: String, key: String): Pair<String, Boolean>? {
        val keyIndex = raw.indexOf("\"$key\"")
        if (keyIndex < 0) return null
        var index = keyIndex + key.length + 2
        while (index < raw.length && raw[index].isWhitespace()) index++
        if (index >= raw.length || raw[index] != ':') return null
        index++
        while (index < raw.length && raw[index].isWhitespace()) index++
        if (index >= raw.length || raw[index] != '"') return null
        index++
        val result = StringBuilder()
        while (index < raw.length) {
            when (val char = raw[index]) {
                '"' -> return result.toString() to true
                '\\' -> {
                    if (index + 1 >= raw.length) return result.toString() to false
                    when (raw[index + 1]) {
                        '"', '\\', '/' -> { result.append(raw[index + 1]); index += 2 }
                        'b' -> { result.append('\b'); index += 2 }
                        'f' -> { result.append('\u000C'); index += 2 }
                        'n' -> { result.append('\n'); index += 2 }
                        'r' -> { result.append('\r'); index += 2 }
                        't' -> { result.append('\t'); index += 2 }
                        'u' -> {
                            if (index + 6 <= raw.length) {
                                raw.substring(index + 2, index + 6).toIntOrNull(16)?.let { code ->
                                    result.append(code.toChar())
                                }
                                index += 6
                            } else {
                                return result.toString() to false
                            }
                        }
                        else -> index += 2
                    }
                }
                else -> { result.append(char); index++ }
            }
        }
        return result.toString() to false
    }

    private fun parseDraftPatch(draft: JsonValue.Object): AgentCreationDraftPatch = AgentCreationDraftPatch(
        title = draft.text("title"),
        learnerRole = draft.text("learnerRole"),
        learnerProfile = draft.text("learnerProfile"),
        learnerDisplayName = draft.text("learnerDisplayName"),
        goal = draft.text("goal"),
        plan = draft.text("plan"),
        learningScope = draft.text("learningScope"),
        modules = draft.text("modules"),
        stageMilestones = draft.text("stageMilestones"),
        dialogueStrategy = draft.text("dialogueStrategy"),
        feedbackIntensity = draft.intIn("feedbackIntensity", 1..5),
        probingIntensity = draft.intIn("probingIntensity", 1..5),
        scaffoldingIntensity = draft.intIn("scaffoldingIntensity", 1..5),
        correctionPersistence = draft.text("correctionPersistence"),
        reviewFrequency = draft.text("reviewFrequency"),
        speakingTone = draft.text("speakingTone"),
        story = draft.text("story"),
        openingMessage = draft.text("openingMessage"),
        persona = draft.text("persona"),
        learningPath = draft.textListOrNull("learningPath", maxItems = 12)
    )

    private fun JsonValue.Object.text(key: String): String? =
        (this[key] as? JsonValue.Text)?.value?.trim()?.take(MAX_FIELD_TEXT)?.takeIf { it.isNotEmpty() }

    private fun JsonValue.Object.intIn(key: String, range: IntRange): Int? =
        (this[key] as? JsonValue.Decimal)?.value?.toIntOrNull()?.takeIf { it in range }

    private fun JsonValue.Object.textList(key: String, maxItems: Int): List<String> {
        val array = (this[key] as? JsonValue.Array) ?: return emptyList()
        return array.values.mapNotNull { (it as? JsonValue.Text)?.value?.trim()?.take(MAX_LIST_ITEM) }
            .filter { it.isNotEmpty() }
            .take(maxItems)
    }

    /** 字段级补丁语义（R86）：数组字段缺失 / 解析为空一律 null（= 本轮未提及），绝不误清空。 */
    private fun JsonValue.Object.textListOrNull(key: String, maxItems: Int): List<String>? {
        val array = (this[key] as? JsonValue.Array) ?: return null
        return array.values.mapNotNull { (it as? JsonValue.Text)?.value?.trim()?.take(MAX_LIST_ITEM) }
            .filter { it.isNotEmpty() }
            .take(maxItems)
            .takeIf { it.isNotEmpty() }
    }

    /** 提取首个平衡的花括号对象；兼容 ```json 围栏与前后杂文。 */
    private fun extractJsonObject(rawText: String): String? {
        val text = rawText.replace("```json", "```")
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            when {
                escaped -> escaped = false
                inString -> when (char) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
                char == '"' -> inString = true
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private const val MAX_FIELD_TEXT = 800
    private const val MAX_LIST_ITEM = 200
}

private sealed interface JsonValue {
    data class Object(val values: Map<String, JsonValue>) : JsonValue { operator fun get(key: String): JsonValue? = values[key] }
    data class Array(val values: List<JsonValue>) : JsonValue
    data class Text(val value: kotlin.String) : JsonValue
    data class Decimal(val value: kotlin.String) : JsonValue
    data class Flag(val value: kotlin.Boolean) : JsonValue
    data object Null : JsonValue
}

/** Minimal JSON reader: accepts exactly one JSON value and never throws to callers. */
private class StrictJson(private val source: kotlin.String) {
    private var index = 0

    fun read(): JsonValue? = runCatching { parseValue().also { skipWhitespace(); if (index != source.length) error("trailing input") } }.getOrNull()

    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObject(); '[' -> parseArray(); '"' -> JsonValue.Text(parseString())
            't' -> literal("true", JsonValue.Flag(true)); 'f' -> literal("false", JsonValue.Flag(false)); 'n' -> literal("null", JsonValue.Null)
            '-', in '0'..'9' -> JsonValue.Decimal(parseNumber()); else -> error("invalid JSON")
        }
    }

    private fun parseObject(): JsonValue.Object {
        expect('{'); skipWhitespace(); val values = linkedMapOf<kotlin.String, JsonValue>()
        if (consume('}')) return JsonValue.Object(values)
        while (true) {
            skipWhitespace(); val key = parseString(); skipWhitespace(); expect(':'); values[key] = parseValue(); skipWhitespace()
            if (consume('}')) return JsonValue.Object(values)
            expect(',')
        }
    }

    private fun parseArray(): JsonValue.Array {
        expect('['); skipWhitespace(); val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonValue.Array(values)
        while (true) {
            values += parseValue(); skipWhitespace()
            if (consume(']')) return JsonValue.Array(values)
            expect(',')
        }
    }

    private fun parseString(): kotlin.String {
        expect('"'); val result = StringBuilder()
        while (true) {
            when (val char = next()) {
                '"' -> return result.toString()
                '\\' -> when (val escaped = next()) {
                    '"', '\\', '/' -> result.append(escaped)
                    'b' -> result.append('\b'); 'f' -> result.append('\u000C'); 'n' -> result.append('\n'); 'r' -> result.append('\r'); 't' -> result.append('\t')
                    'u' -> result.append(source.substring(index, index + 4).toInt(16).toChar().also { index += 4 })
                    else -> error("bad escape")
                }
                else -> if (char.code < 0x20) error("control character") else result.append(char)
            }
        }
    }

    private fun parseNumber(): kotlin.String {
        val start = index; if (peek() == '-') index++; digits()
        if (peekOrNull() == '.') { index++; digits() }
        if (peekOrNull() in listOf('e', 'E')) { index++; if (peekOrNull() in listOf('+', '-')) index++; digits() }
        return source.substring(start, index)
    }

    private fun digits() { val start = index; while (peekOrNull()?.isDigit() == true) index++; if (start == index) error("number") }
    private fun <T : JsonValue> literal(text: kotlin.String, value: T): T { if (!source.startsWith(text, index)) error("literal"); index += text.length; return value }
    private fun skipWhitespace() { while (peekOrNull()?.isWhitespace() == true) index++ }
    private fun consume(expected: Char): Boolean = if (peekOrNull() == expected) { index++; true } else false
    private fun expect(expected: Char) { if (!consume(expected)) error("expected $expected") }
    private fun next(): Char {
        val value = peekOrNull() ?: error("eof")
        index += 1
        return value
    }
    private fun peek(): Char = peekOrNull() ?: error("eof")
    private fun peekOrNull(): Char? = source.getOrNull(index)

    companion object { fun parseObject(text: kotlin.String): JsonValue.Object? = StrictJson(text).read() as? JsonValue.Object }
}
