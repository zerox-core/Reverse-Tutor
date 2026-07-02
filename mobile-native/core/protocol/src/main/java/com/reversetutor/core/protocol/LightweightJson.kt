package com.reversetutor.core.protocol

internal sealed interface JsonValue {
    fun toCanonicalJson(): String
}

internal data class JsonObject(
    val fields: LinkedHashMap<String, JsonValue>
) : JsonValue {
    override fun toCanonicalJson(): String =
        fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${key.escapeJsonString()}\":${value.toCanonicalJson()}"
        }
}

internal data class JsonArray(
    val values: List<JsonValue>
) : JsonValue {
    override fun toCanonicalJson(): String =
        values.joinToString(prefix = "[", postfix = "]") { it.toCanonicalJson() }
}

internal data class JsonString(
    val value: String
) : JsonValue {
    override fun toCanonicalJson(): String = "\"${value.escapeJsonString()}\""
}

internal data class JsonNumber(
    val raw: String
) : JsonValue {
    override fun toCanonicalJson(): String = raw
}

internal data class JsonBoolean(
    val value: Boolean
) : JsonValue {
    override fun toCanonicalJson(): String = value.toString()
}

internal object JsonNull : JsonValue {
    override fun toCanonicalJson(): String = "null"
}

internal class JsonParseException(message: String) : IllegalArgumentException(message)

internal object LightweightJsonParser {
    fun parse(input: String): JsonValue {
        val parser = Parser(input)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw JsonParseException("Unexpected trailing content at index ${parser.index}.")
        }
        return value
    }

    private class Parser(private val input: String) {
        var index: Int = 0
            private set

        fun isAtEnd(): Boolean = index >= input.length

        fun skipWhitespace() {
            while (!isAtEnd() && input[index].isWhitespace()) {
                index += 1
            }
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (isAtEnd()) {
                throw JsonParseException("Unexpected end of JSON.")
            }
            return when (input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonString(parseString())
                't' -> parseLiteral("true", JsonBoolean(true))
                'f' -> parseLiteral("false", JsonBoolean(false))
                'n' -> parseLiteral("null", JsonNull)
                '-', in '0'..'9' -> parseNumber()
                else -> throw JsonParseException("Unexpected character '${input[index]}' at index $index.")
            }
        }

        private fun parseObject(): JsonObject {
            expect('{')
            skipWhitespace()
            val fields = linkedMapOf<String, JsonValue>()
            if (peek('}')) {
                index += 1
                return JsonObject(fields)
            }

            while (true) {
                skipWhitespace()
                if (!peek('"')) {
                    throw JsonParseException("Object key must be a string at index $index.")
                }
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                fields[key] = value
                skipWhitespace()
                when {
                    peek(',') -> index += 1
                    peek('}') -> {
                        index += 1
                        return JsonObject(fields)
                    }
                    else -> throw JsonParseException("Expected ',' or '}' at index $index.")
                }
            }
        }

        private fun parseArray(): JsonArray {
            expect('[')
            skipWhitespace()
            val values = mutableListOf<JsonValue>()
            if (peek(']')) {
                index += 1
                return JsonArray(values)
            }

            while (true) {
                values += parseValue()
                skipWhitespace()
                when {
                    peek(',') -> index += 1
                    peek(']') -> {
                        index += 1
                        return JsonArray(values)
                    }
                    else -> throw JsonParseException("Expected ',' or ']' at index $index.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val output = StringBuilder()
            while (!isAtEnd()) {
                val char = input[index++]
                when (char) {
                    '"' -> return output.toString()
                    '\\' -> output.append(parseEscape())
                    else -> output.append(char)
                }
            }
            throw JsonParseException("Unterminated string.")
        }

        private fun parseEscape(): Char {
            if (isAtEnd()) {
                throw JsonParseException("Unterminated escape sequence.")
            }
            return when (val escaped = input[index++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> throw JsonParseException("Unsupported escape sequence \\$escaped at index ${index - 1}.")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > input.length) {
                throw JsonParseException("Incomplete unicode escape at index $index.")
            }
            val hex = input.substring(index, index + 4)
            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                throw JsonParseException("Invalid unicode escape \\u$hex at index $index.")
            }
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun parseNumber(): JsonNumber {
            val start = index
            if (peek('-')) {
                index += 1
            }
            readDigits()
            if (peek('.')) {
                index += 1
                readDigits()
            }
            if (!isAtEnd() && (input[index] == 'e' || input[index] == 'E')) {
                index += 1
                if (!isAtEnd() && (input[index] == '+' || input[index] == '-')) {
                    index += 1
                }
                readDigits()
            }
            return JsonNumber(input.substring(start, index))
        }

        private fun readDigits() {
            val start = index
            while (!isAtEnd() && input[index] in '0'..'9') {
                index += 1
            }
            if (start == index) {
                throw JsonParseException("Expected digit at index $index.")
            }
        }

        private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
            if (!input.startsWith(literal, index)) {
                throw JsonParseException("Expected $literal at index $index.")
            }
            index += literal.length
            return value
        }

        private fun expect(expected: Char) {
            if (isAtEnd() || input[index] != expected) {
                throw JsonParseException("Expected '$expected' at index $index.")
            }
            index += 1
        }

        private fun peek(expected: Char): Boolean = !isAtEnd() && input[index] == expected
    }
}

internal fun String.escapeJsonString(): String {
    val output = StringBuilder(length)
    forEach { char ->
        when (char) {
            '"' -> output.append("\\\"")
            '\\' -> output.append("\\\\")
            '\b' -> output.append("\\b")
            '\u000C' -> output.append("\\f")
            '\n' -> output.append("\\n")
            '\r' -> output.append("\\r")
            '\t' -> output.append("\\t")
            else -> {
                if (char.code < 0x20) {
                    output.append("\\u")
                    output.append(char.code.toString(16).padStart(4, '0'))
                } else {
                    output.append(char)
                }
            }
        }
    }
    return output.toString()
}

internal fun JsonObject.stringField(name: String): String? =
    (fields[name] as? JsonString)?.value

internal fun JsonObject.intField(name: String): Int? =
    (fields[name] as? JsonNumber)?.raw?.toIntOrNull()

internal fun JsonObject.hasField(name: String): Boolean = fields.containsKey(name)
