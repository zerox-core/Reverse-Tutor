package com.reversetutor.core.llm

/** Fail-closed parser for the bounded P3 assistant reply envelope. */
object LlmAssistantReplyEnvelopeParser {
    private const val MAX_BLOCKS = 24
    private const val MAX_TOOL_CALLS = 8
    private const val MAX_TEXT = 4_000
    private val allowedToolNames = setOf(
        "session_document.create", "session_document.read", "session_document.replace_block",
        "session_table.create", "session_table.upsert_row", "reference.open"
    )
    private val allowedActionTypes = setOf(
        "ask", "probe", "challenge", "clue", "scaffold_example", "small_lecture",
        "examiner_verify", "emote", "persuade", "next", "recap", "decompose",
        "advance", "verify_done", "unblock", "empathize", "observe", "soft_guide"
    )
    private val allowedStudentRoles = setOf(
        "probing_student", "clue_student", "scaffold_student", "confused_student",
        "examiner", "review_student", "goal_partner", "companion"
    )
    private val allowedEvidenceTypes = setOf(
        "none", "explanation", "retrieval", "transfer", "delayed_retrieval", "correction"
    )
    private val allowedEvidenceStatuses = setOf("none", "passed", "partial", "failed")
    private val sensitivePattern = Regex("(?i)sk-[a-z0-9_-]+|authorization\\s*[:=]|bearer\\s+[a-z0-9._-]+|https?://")

    fun parse(rawText: String, allowedEvidenceIds: Set<String>): LlmAssistantReplyEnvelope =
        parseInternal(rawText, allowedEvidenceIds).envelope

    fun parseValidated(rawText: String, allowedEvidenceIds: Set<String>): LlmAssistantReplyEnvelope? =
        parseInternal(rawText, allowedEvidenceIds).takeIf { it.isEnvelope }?.envelope

    private fun parseInternal(rawText: String, allowedEvidenceIds: Set<String>): ParseResult {
        val root = StrictJson.parseObject(rawText) ?: return fallback(rawText)
        if (root["version"].stringValue() != "v1" || !root.hasOnly(ROOT_FIELDS)) return fallback(rawText)
        val blocks = parseBlocks(root["blocks"] as? JsonValue.Array) ?: return fallback(rawText)
        val references = parseReferences(root["evidenceReferenceIds"] as? JsonValue.Array, allowedEvidenceIds) ?: return fallback(rawText)
        val calls = parseToolCalls(root["toolCalls"] as? JsonValue.Array) ?: return fallback(rawText)
        val checkPlan = parseCheckPlan(root["checkPlan"] as? JsonValue.Object, references.toSet())
        return ParseResult(LlmAssistantReplyEnvelope(blocks, references, calls, parseOutcome(root["outcome"] as? JsonValue.Object), checkPlan), true)
    }

    private fun parseBlocks(value: JsonValue.Array?): List<LlmRichContentBlock>? {
        if (value == null || value.values.isEmpty() || value.values.size > MAX_BLOCKS) return null
        return value.values.map { parseBlock(it as? JsonValue.Object) ?: return null }
    }

    private fun parseBlock(value: JsonValue.Object?): LlmRichContentBlock? {
        val block = value ?: return null
        return when (block["type"].stringValue()) {
            "heading" -> if (block.hasOnly(setOf("type", "level", "text"))) {
                val level = block["level"].numberValue()?.toIntOrNull()
                val text = safeText(block["text"].stringValue(), 240)
                if (level == null || level !in 1..4 || text == null) null else LlmRichContentBlock.Heading(level, text)
            } else null
            "paragraph" -> if (block.hasOnly(setOf("type", "text"))) safeText(block["text"].stringValue(), MAX_TEXT)?.let(LlmRichContentBlock::Paragraph) else null
            "bullet_list", "numbered_list" -> if (block.hasOnly(setOf("type", "items"))) {
                val items = parseTextList(block["items"] as? JsonValue.Array, 16, 320) ?: return null
                if (block["type"].stringValue() == "bullet_list") LlmRichContentBlock.BulletList(items) else LlmRichContentBlock.NumberedList(items)
            } else null
            "code" -> if (block.hasOnly(setOf("type", "language", "code"))) {
                val code = safeText(block["code"].stringValue(), MAX_TEXT) ?: return null
                val language = block["language"].stringValue()?.trim()?.take(32)?.ifEmpty { null }
                LlmRichContentBlock.CodeBlock(language, code)
            } else null
            "callout" -> if (block.hasOnly(setOf("type", "kind", "text"))) {
                val kind = block["kind"].stringValue()?.trim()?.lowercase()
                val text = safeText(block["text"].stringValue(), 600)
                if (kind !in setOf("info", "warning", "success") || text == null) null else LlmRichContentBlock.Callout(kind!!, text)
            } else null
            "table" -> if (block.hasOnly(setOf("type", "columns", "rows"))) {
                val columns = parseTextList(block["columns"] as? JsonValue.Array, 8, 80) ?: return null
                val rows = (block["rows"] as? JsonValue.Array)?.values?.takeIf { it.size <= 24 }?.map { row ->
                    parseTextList(row as? JsonValue.Array, columns.size, 240)?.takeIf { it.size == columns.size } ?: return null
                } ?: return null
                LlmRichContentBlock.SimpleTable(columns, rows)
            } else null
            else -> null
        }
    }

    private fun parseReferences(value: JsonValue.Array?, allowedEvidenceIds: Set<String>): List<String>? {
        if (value == null || value.values.size > 6) return null
        val allowed = allowedEvidenceIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val ids = value.values.map { it.stringValue()?.trim()?.take(120) ?: return null }
        return ids.distinct().takeIf { it.all(allowed::contains) }
    }

    private fun parseToolCalls(value: JsonValue.Array?): List<LlmToolCall>? {
        if (value == null || value.values.size > MAX_TOOL_CALLS) return null
        val calls = value.values.map { element ->
            val call = element as? JsonValue.Object ?: return null
            if (!call.hasOnly(setOf("callId", "name", "argumentsJson"))) return null
            val callId = call["callId"].stringValue()?.trim()?.take(120).orEmpty()
            val name = call["name"].stringValue()?.trim()?.lowercase().orEmpty()
            val argumentsJson = call["argumentsJson"].stringValue()?.trim()?.take(2_000).orEmpty()
            if (callId.isEmpty() || name !in allowedToolNames || StrictJson.parseObject(argumentsJson) == null || sensitivePattern.containsMatchIn(argumentsJson)) return null
            LlmToolCall(callId, name, argumentsJson)
        }
        return calls.takeIf { it.map(LlmToolCall::callId).distinct().size == it.size }
    }

    private fun parseOutcome(value: JsonValue.Object?): StructuredTurnOutcome {
        if (value == null || value.values.isEmpty() || !value.hasOnly(OUTCOME_FIELDS)) return StructuredTurnOutcome.EMPTY
        val action = value["actionType"].stringValue()?.trim()?.lowercase().orEmpty()
        val role = value["studentRole"].stringValue()?.trim()?.lowercase().orEmpty()
        val evidenceType = value["evidenceType"].stringValue()?.trim()?.lowercase() ?: "none"
        val evidenceStatus = value["evidenceStatus"].stringValue()?.trim()?.lowercase() ?: "none"
        if ((action.isNotEmpty() && action !in allowedActionTypes) || (role.isNotEmpty() && role !in allowedStudentRoles) || evidenceType !in allowedEvidenceTypes || evidenceStatus !in allowedEvidenceStatuses) return StructuredTurnOutcome.EMPTY
        val knowledgePoint = safeText(value["knowledgePoint"].stringValue(), 120) ?: ""
        val processSummary = safeText(value["processSummary"].stringValue(), 320) ?: ""
        return StructuredTurnOutcome(
            windowId = value["windowId"].stringValue()?.trim()?.take(120)?.ifEmpty { null }, actionType = action,
            studentRole = role, knowledgePoint = knowledgePoint,
            correctness = value["correctness"].numberValue()?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
            depth = value["depth"].numberValue()?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
            evidenceType = evidenceType, evidenceStatus = evidenceStatus, processSummary = processSummary,
            initiativeSource = value["initiativeSource"].stringValue()?.trim()?.take(64)?.ifEmpty { null }
        ).normalized()
    }

    private fun parseCheckPlan(value: JsonValue.Object?, allowedReferenceIds: Set<String>): LlmSourceGroundedCheckPlan? {
        val plan = value ?: return null
        if (!plan.hasOnly(setOf("id", "sourceRevision", "sourceReferenceIds", "prompt", "expectedAnswer", "rule", "conceptKey"))) return null
        val id = safeText(plan["id"].stringValue(), 80) ?: return null
        val revision = safeText(plan["sourceRevision"].stringValue(), 120) ?: return null
        val refs = (plan["sourceReferenceIds"] as? JsonValue.Array)?.values?.map { safeText(it.stringValue(), 160) ?: return null } ?: return null
        if (refs.isEmpty() || refs.any { it !in allowedReferenceIds }) return null
        val prompt = safeText(plan["prompt"].stringValue(), 400) ?: return null
        val expected = plan["expectedAnswer"].stringValue()?.trim()?.take(200).orEmpty()
        val concept = plan["conceptKey"].stringValue()?.trim()?.take(40).orEmpty()
        val ruleObject = plan["rule"] as? JsonValue.Object ?: return null
        val rule = when (ruleObject["type"].stringValue()?.trim()?.lowercase()) {
            "exact_text" -> ruleObject["normalizedAnswer"].stringValue()?.let(LlmSourceCheckRule::ExactText)
            "numeric_tolerance" -> {
                val number = ruleObject["expected"].numberValue()?.toDoubleOrNull() ?: return null
                val tolerance = ruleObject["tolerance"].numberValue()?.toDoubleOrNull() ?: return null
                LlmSourceCheckRule.NumericTolerance(number, tolerance)
            }
            "required_concepts" -> (ruleObject["terms"] as? JsonValue.Array)?.values?.map { it.stringValue() ?: return null }?.let(LlmSourceCheckRule::RequiredConcepts)
            "rubric" -> (ruleObject["criteria"] as? JsonValue.Array)?.values?.map { it.stringValue() ?: return null }?.let(LlmSourceCheckRule::Rubric)
            else -> null
        } ?: return null
        return LlmSourceGroundedCheckPlan(id, revision, refs, prompt, expected, rule, concept).normalized()
    }

    private fun parseTextList(value: JsonValue.Array?, maxSize: Int, maxText: Int): List<String>? {
        if (value == null || value.values.isEmpty() || value.values.size > maxSize) return null
        return value.values.map { safeText(it.stringValue(), maxText) ?: return null }
    }

    private fun fallback(rawText: String) = ParseResult(LlmAssistantReplyEnvelope(listOf(LlmRichContentBlock.Paragraph(sanitizeFallback(rawText)))), false)
    private fun safeText(value: String?, maxLength: Int): String? = value?.trim()?.take(maxLength)?.takeIf { it.isNotEmpty() && !sensitivePattern.containsMatchIn(it) }
    private fun sanitizeFallback(value: String): String = value.trim().replace(sensitivePattern, "[redacted]").take(MAX_TEXT).ifEmpty { "回复不可用" }
    private fun JsonValue?.stringValue(): String? = (this as? JsonValue.Text)?.value
    private fun JsonValue?.numberValue(): String? = (this as? JsonValue.Decimal)?.value
    private fun JsonValue.Object.hasOnly(fields: Set<String>): Boolean = values.keys.all(fields::contains)

    private data class ParseResult(val envelope: LlmAssistantReplyEnvelope, val isEnvelope: Boolean)
    private val ROOT_FIELDS = setOf("version", "blocks", "evidenceReferenceIds", "toolCalls", "outcome", "checkPlan")
    private val OUTCOME_FIELDS = setOf("windowId", "actionType", "studentRole", "knowledgePoint", "correctness", "depth", "evidenceType", "evidenceStatus", "processSummary", "initiativeSource")
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
