package com.reversetutor.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/**
 * 1c-c1 代码块语法高亮：纯 Kotlin 轻量分词器（无原生/重型依赖）。
 *
 * 支持语言：kotlin / java / python / json / xml / sql / bash（含常见别名）。
 * 未知语言返回单一 Plain token（等价不高亮），调用方仍可按等宽字体渲染。
 *
 * 契约：tokenize 返回的 token 列表连续、非空段、完整覆盖 [0, code.length)，
 * 对任意输入（含流式中途的半完整块：未闭合字符串/注释）不抛异常。
 */
enum class CodeTokenKind { Plain, Keyword, Type, String, Number, Comment }

data class CodeToken(val kind: CodeTokenKind, val start: Int, val end: Int)

object CodeSyntaxTokenizer {

    /** 归一化语言名；不支持的语言返回 null。 */
    fun supportedLanguage(language: String?): String? = when (language?.trim()?.lowercase()) {
        "kotlin", "kt", "kts" -> "kotlin"
        "java" -> "java"
        "python", "py", "python3" -> "python"
        "json" -> "json"
        "xml", "html", "svg", "xhtml" -> "xml"
        "sql" -> "sql"
        "bash", "sh", "shell", "zsh" -> "bash"
        else -> null
    }

    fun tokenize(language: String?, code: String): List<CodeToken> {
        if (code.isEmpty()) return emptyList()
        val normalized = supportedLanguage(language)
            ?: return listOf(CodeToken(CodeTokenKind.Plain, 0, code.length))
        val tokens = when (normalized) {
            "kotlin" -> CLikeScanner(KOTLIN_KEYWORDS, kotlinRawStrings = true).scan(code)
            "java" -> CLikeScanner(JAVA_KEYWORDS, kotlinRawStrings = false).scan(code)
            "python" -> PythonScanner.scan(code)
            "json" -> JsonScanner.scan(code)
            "xml" -> XmlScanner.scan(code)
            "sql" -> SqlScanner.scan(code)
            "bash" -> BashScanner.scan(code)
            else -> listOf(CodeToken(CodeTokenKind.Plain, 0, code.length))
        }
        return tokens.ensureCoverage(code.length)
    }

    /** 防御：任何 scanner 漏段时用 Plain 补齐，保证连续覆盖契约。 */
    private fun List<CodeToken>.ensureCoverage(length: Int): List<CodeToken> {
        if (isEmpty()) return listOf(CodeToken(CodeTokenKind.Plain, 0, length))
        val result = mutableListOf<CodeToken>()
        var cursor = 0
        for (token in this) {
            if (token.start > cursor) result += CodeToken(CodeTokenKind.Plain, cursor, token.start)
            if (token.end > token.start) result += token
            cursor = maxOf(cursor, token.end)
        }
        if (cursor < length) result += CodeToken(CodeTokenKind.Plain, cursor, length)
        return result
    }

    private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'
    private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    /** 数字字面量：十进制/浮点/指数/十六进制/分隔符与类型后缀，宽松吞掉标识字符。 */
    private fun scanNumber(code: String, start: Int): Int {
        var i = start
        if (i < code.length && (code[i] == '-' || code[i] == '+')) i++
        while (i < code.length) {
            val c = code[i]
            if (c.isLetterOrDigit() || c == '.' || c == '_') {
                i++
                continue
            }
            // +/- 仅跟在 e/E 后视为指数符号
            if ((c == '+' || c == '-') && i > start && (code[i - 1] == 'e' || code[i - 1] == 'E')) {
                i++
                continue
            }
            break
        }
        return i
    }

    /** 带反斜杠转义的引号字符串；未闭合（流式中途）扫到文本末尾。 */
    private fun scanQuoted(code: String, start: Int, quote: Char, escapes: Boolean = true): Int {
        var i = start + 1
        while (i < code.length) {
            val c = code[i]
            if (escapes && c == '\\') {
                i += 2
                continue
            }
            if (c == quote) return i + 1
            i++
        }
        return code.length
    }

    private fun scanLineComment(code: String, start: Int): Int {
        val nl = code.indexOf('\n', start)
        return if (nl < 0) code.length else nl
    }

    private fun scanDelimited(code: String, start: Int, delimiter: String): Int {
        val closing = code.indexOf(delimiter, start + delimiter.length)
        return if (closing < 0) code.length else closing + delimiter.length
    }

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
        "get", "import", "init", "param", "property", "receiver", "set", "setparam",
        "where", "actual", "abstract", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
        "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend", "tailrec",
        "vararg", "it"
    )

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while", "true", "false", "null", "var", "record",
        "sealed", "permits", "yield"
    )

    private val PYTHON_KEYWORDS = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
        "del", "elif", "else", "except", "finally", "for", "from", "global", "if",
        "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield", "True", "False", "None"
    )

    private val PYTHON_BUILTINS = setOf(
        "print", "len", "range", "str", "int", "float", "bool", "list", "dict", "set",
        "tuple", "type", "isinstance", "enumerate", "zip", "map", "filter", "sorted",
        "sum", "min", "max", "abs", "open", "input", "super", "self", "cls", "repr",
        "format", "hasattr", "getattr", "setattr", "Exception", "ValueError", "KeyError",
        "TypeError", "IndexError", "RuntimeError", "StopIteration", "object", "property",
        "staticmethod", "classmethod", "any", "all", "next", "iter", "bytes", "bytearray"
    )

    private val SQL_KEYWORDS = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "drop", "alter", "add", "join", "inner", "left", "right",
        "full", "outer", "on", "group", "by", "order", "having", "limit", "offset",
        "and", "or", "not", "null", "is", "in", "like", "between", "exists", "as",
        "distinct", "union", "all", "case", "when", "then", "else", "end", "primary",
        "key", "foreign", "references", "index", "unique", "default", "check",
        "constraint", "view", "trigger", "procedure", "function", "begin", "commit",
        "rollback", "transaction", "asc", "desc", "count", "sum", "avg", "min", "max",
        "true", "false", "if", "replace", "temporary", "cascade"
    )

    private val BASH_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done",
        "case", "esac", "in", "function", "return", "local", "export", "readonly",
        "declare", "unset", "shift", "break", "continue", "exit", "echo", "printf",
        "read", "cd", "pwd", "source", "alias", "eval", "exec", "trap", "wait",
        "true", "false", "test", "set", "let"
    )

    /** C 系（kotlin/java）扫描器。 */
    private class CLikeScanner(
        private val keywords: Set<String>,
        private val kotlinRawStrings: Boolean
    ) {
        fun scan(code: String): List<CodeToken> {
            val tokens = mutableListOf<CodeToken>()
            var i = 0
            var plainStart = 0
            fun emit(kind: CodeTokenKind, start: Int, end: Int) {
                if (start > plainStart) tokens += CodeToken(CodeTokenKind.Plain, plainStart, start)
                tokens += CodeToken(kind, start, end)
                plainStart = end
            }
            while (i < code.length) {
                val c = code[i]
                when {
                    code.startsWith("//", i) -> {
                        val end = scanLineComment(code, i)
                        emit(CodeTokenKind.Comment, i, end); i = end
                    }
                    code.startsWith("/*", i) -> {
                        val end = scanDelimited(code, i, "*/")
                        emit(CodeTokenKind.Comment, i, end); i = end
                    }
                    kotlinRawStrings && code.startsWith("\"\"\"", i) -> {
                        val end = scanDelimited(code, i, "\"\"\"")
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    c == '"' -> {
                        val end = scanQuoted(code, i, '"')
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    c == '\'' -> {
                        val end = scanQuoted(code, i, '\'')
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    c == '@' && i + 1 < code.length && isIdentStart(code[i + 1]) -> {
                        var end = i + 1
                        while (end < code.length && isIdentPart(code[end])) end++
                        emit(CodeTokenKind.Type, i, end); i = end
                    }
                    c.isDigit() -> {
                        val end = scanNumber(code, i)
                        emit(CodeTokenKind.Number, i, end); i = end
                    }
                    isIdentStart(c) -> {
                        var end = i + 1
                        while (end < code.length && isIdentPart(code[end])) end++
                        val word = code.substring(i, end)
                        val kind = when {
                            word in keywords -> CodeTokenKind.Keyword
                            word.first().isUpperCase() -> CodeTokenKind.Type
                            else -> CodeTokenKind.Plain
                        }
                        emit(kind, i, end); i = end
                    }
                    else -> i++
                }
            }
            if (plainStart < code.length) tokens += CodeToken(CodeTokenKind.Plain, plainStart, code.length)
            return tokens
        }
    }

    /** Python 扫描器：# 注释、单/双/三引号字符串（含 r/b/f/u 前缀）、装饰器与内置名。 */
    private object PythonScanner {
        fun scan(code: String): List<CodeToken> {
            val tokens = mutableListOf<CodeToken>()
            var i = 0
            var plainStart = 0
            fun emit(kind: CodeTokenKind, start: Int, end: Int) {
                if (start > plainStart) tokens += CodeToken(CodeTokenKind.Plain, plainStart, start)
                tokens += CodeToken(kind, start, end)
                plainStart = end
            }
            while (i < code.length) {
                val c = code[i]
                when {
                    c == '#' -> {
                        val end = scanLineComment(code, i)
                        emit(CodeTokenKind.Comment, i, end); i = end
                    }
                    c == '"' || c == '\'' -> {
                        val triple = code.startsWith("\"\"\"", i) || code.startsWith("'''", i)
                        val end = if (triple) {
                            scanDelimited(code, i, code.substring(i, i + 3))
                        } else {
                            scanQuoted(code, i, c)
                        }
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    (c == 'r' || c == 'b' || c == 'f' || c == 'u' ||
                        (c == 'R' || c == 'B' || c == 'F' || c == 'U')) &&
                        i + 1 < code.length && (code[i + 1] == '"' || code[i + 1] == '\'') &&
                        (i == 0 || !isIdentPart(code[i - 1])) -> {
                        val quote = code[i + 1]
                        val triple = code.startsWith("\"\"\"", i + 1) || code.startsWith("'''", i + 1)
                        val end = if (triple) {
                            scanDelimited(code, i + 1, code.substring(i + 1, i + 4))
                        } else {
                            scanQuoted(code, i + 1, quote, escapes = c != 'r' && c != 'R')
                        }
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    c == '@' && i + 1 < code.length && isIdentStart(code[i + 1]) -> {
                        var end = i + 1
                        while (end < code.length && (isIdentPart(code[end]) || code[end] == '.')) end++
                        emit(CodeTokenKind.Type, i, end); i = end
                    }
                    c.isDigit() -> {
                        val end = scanNumber(code, i)
                        emit(CodeTokenKind.Number, i, end); i = end
                    }
                    isIdentStart(c) -> {
                        var end = i + 1
                        while (end < code.length && isIdentPart(code[end])) end++
                        val word = code.substring(i, end)
                        val kind = when (word) {
                            in PYTHON_KEYWORDS -> CodeTokenKind.Keyword
                            in PYTHON_BUILTINS -> CodeTokenKind.Type
                            else -> CodeTokenKind.Plain
                        }
                        emit(kind, i, end); i = end
                    }
                    else -> i++
                }
            }
            if (plainStart < code.length) tokens += CodeToken(CodeTokenKind.Plain, plainStart, code.length)
            return tokens
        }
    }

    /** JSON 扫描器：键（字符串后紧跟冒号）按 Keyword 高亮。 */
    private object JsonScanner {
        fun scan(code: String): List<CodeToken> {
            val tokens = mutableListOf<CodeToken>()
            var i = 0
            var plainStart = 0
            fun emit(kind: CodeTokenKind, start: Int, end: Int) {
                if (start > plainStart) tokens += CodeToken(CodeTokenKind.Plain, plainStart, start)
                tokens += CodeToken(kind, start, end)
                plainStart = end
            }
            while (i < code.length) {
                val c = code[i]
                when {
                    c == '"' -> {
                        val end = scanQuoted(code, i, '"')
                        var lookahead = end
                        while (lookahead < code.length && code[lookahead].isWhitespace()) lookahead++
                        val isKey = lookahead < code.length && code[lookahead] == ':'
                        emit(if (isKey) CodeTokenKind.Keyword else CodeTokenKind.String, i, end)
                        i = end
                    }
                    c.isDigit() || (c == '-' && i + 1 < code.length && code[i + 1].isDigit()) -> {
                        val end = scanNumber(code, i)
                        emit(CodeTokenKind.Number, i, end); i = end
                    }
                    isIdentStart(c) -> {
                        var end = i + 1
                        while (end < code.length && isIdentPart(code[end])) end++
                        val kind = if (code.substring(i, end) in setOf("true", "false", "null")) {
                            CodeTokenKind.Keyword
                        } else {
                            CodeTokenKind.Plain
                        }
                        emit(kind, i, end); i = end
                    }
                    else -> i++
                }
            }
            if (plainStart < code.length) tokens += CodeToken(CodeTokenKind.Plain, plainStart, code.length)
            return tokens
        }
    }

    /** XML/HTML 扫描器：注释、标签名 Keyword、属性名 Type、属性值 String。 */
    private object XmlScanner {
        fun scan(code: String): List<CodeToken> {
            val tokens = mutableListOf<CodeToken>()
            var i = 0
            var plainStart = 0
            fun emit(kind: CodeTokenKind, start: Int, end: Int) {
                if (start > plainStart) tokens += CodeToken(CodeTokenKind.Plain, plainStart, start)
                tokens += CodeToken(kind, start, end)
                plainStart = end
            }
            while (i < code.length) {
                when {
                    code.startsWith("<!--", i) -> {
                        val end = scanDelimited(code, i, "-->")
                        emit(CodeTokenKind.Comment, i, end); i = end
                    }
                    code[i] == '<' -> {
                        // 标签整体扫描到匹配的 '>'（引号内的 > 不算）
                        var j = i + 1
                        var quote: Char? = null
                        while (j < code.length) {
                            val cj = code[j]
                            if (quote != null) {
                                if (cj == quote) quote = null
                            } else when (cj) {
                                '"', '\'' -> quote = cj
                                '>' -> break
                            }
                            j++
                        }
                        val tagEnd = if (j < code.length) j + 1 else code.length
                        scanTag(code, i, tagEnd, ::emit)
                        i = tagEnd
                    }
                    else -> i++
                }
            }
            if (plainStart < code.length) tokens += CodeToken(CodeTokenKind.Plain, plainStart, code.length)
            return tokens
        }

        private fun scanTag(
            code: String,
            start: Int,
            end: Int,
            emit: (CodeTokenKind, Int, Int) -> Unit
        ) {
            var i = start
            // 标签开口：< / </ <? <! 一律 Keyword
            var openEnd = i + 1
            if (openEnd < end && (code[openEnd] == '/' || code[openEnd] == '?' || code[openEnd] == '!')) openEnd++
            emit(CodeTokenKind.Keyword, i, openEnd)
            i = openEnd
            // 标签名
            if (i < end && (isIdentStart(code[i]) || code[i] == ':')) {
                var nameEnd = i
                while (nameEnd < end && (isIdentPart(code[nameEnd]) || code[nameEnd] == '-' ||
                        code[nameEnd] == ':' || code[nameEnd] == '.')
                ) nameEnd++
                emit(CodeTokenKind.Keyword, i, nameEnd)
                i = nameEnd
            }
            // 属性与收尾
            while (i < end) {
                val c = code[i]
                when {
                    c == '"' || c == '\'' -> {
                        val valueEnd = scanQuoted(code, i, c, escapes = false)
                        emit(CodeTokenKind.String, i, minOf(valueEnd, end))
                        i = minOf(valueEnd, end)
                    }
                    c == '>' || (c == '/' && i + 1 < end && code[i + 1] == '>') ||
                        (c == '?' && i + 1 < end && code[i + 1] == '>') -> {
                        val closeEnd = if (c == '>') i + 1 else i + 2
                        emit(CodeTokenKind.Keyword, i, minOf(closeEnd, end))
                        i = minOf(closeEnd, end)
                    }
                    isIdentStart(c) -> {
                        var attrEnd = i + 1
                        while (attrEnd < end && (isIdentPart(code[attrEnd]) || code[attrEnd] == '-' ||
                                code[attrEnd] == ':' || code[attrEnd] == '.')
                        ) attrEnd++
                        // 属性名仅在后面跟 = 时按 Type 高亮
                        var lookahead = attrEnd
                        while (lookahead < end && code[lookahead].isWhitespace()) lookahead++
                        if (lookahead < end && code[lookahead] == '=') {
                            emit(CodeTokenKind.Type, i, attrEnd)
                            i = attrEnd
                        } else {
                            i = attrEnd
                        }
                    }
                    else -> i++
                }
            }
        }
    }

    /** SQL 扫描器：大小写不敏感关键字、-- 与块注释、单引号字符串、反引号标识。 */
    private object SqlScanner {
        fun scan(code: String): List<CodeToken> {
            val tokens = mutableListOf<CodeToken>()
            var i = 0
            var plainStart = 0
            fun emit(kind: CodeTokenKind, start: Int, end: Int) {
                if (start > plainStart) tokens += CodeToken(CodeTokenKind.Plain, plainStart, start)
                tokens += CodeToken(kind, start, end)
                plainStart = end
            }
            while (i < code.length) {
                val c = code[i]
                when {
                    code.startsWith("--", i) -> {
                        val end = scanLineComment(code, i)
                        emit(CodeTokenKind.Comment, i, end); i = end
                    }
                    code.startsWith("/*", i) -> {
                        val end = scanDelimited(code, i, "*/")
                        emit(CodeTokenKind.Comment, i, end); i = end
                    }
                    c == '\'' -> {
                        val end = scanQuoted(code, i, '\'')
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    c == '`' -> {
                        val end = scanQuoted(code, i, '`', escapes = false)
                        emit(CodeTokenKind.Type, i, end); i = end
                    }
                    c.isDigit() -> {
                        val end = scanNumber(code, i)
                        emit(CodeTokenKind.Number, i, end); i = end
                    }
                    isIdentStart(c) -> {
                        var end = i + 1
                        while (end < code.length && isIdentPart(code[end])) end++
                        val kind = if (code.substring(i, end).lowercase() in SQL_KEYWORDS) {
                            CodeTokenKind.Keyword
                        } else {
                            CodeTokenKind.Plain
                        }
                        emit(kind, i, end); i = end
                    }
                    else -> i++
                }
            }
            if (plainStart < code.length) tokens += CodeToken(CodeTokenKind.Plain, plainStart, code.length)
            return tokens
        }
    }

    /** Bash 扫描器：# 注释（行首或空白后）、关键字、$VAR/${VAR} 变量、单双引号字符串。 */
    private object BashScanner {
        fun scan(code: String): List<CodeToken> {
            val tokens = mutableListOf<CodeToken>()
            var i = 0
            var plainStart = 0
            fun emit(kind: CodeTokenKind, start: Int, end: Int) {
                if (start > plainStart) tokens += CodeToken(CodeTokenKind.Plain, plainStart, start)
                tokens += CodeToken(kind, start, end)
                plainStart = end
            }
            while (i < code.length) {
                val c = code[i]
                when {
                    c == '#' && (i == 0 || code[i - 1] == '\n' || code[i - 1].isWhitespace()) -> {
                        val end = scanLineComment(code, i)
                        emit(CodeTokenKind.Comment, i, end); i = end
                    }
                    c == '"' -> {
                        val end = scanQuoted(code, i, '"')
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    c == '\'' -> {
                        val end = scanQuoted(code, i, '\'', escapes = false)
                        emit(CodeTokenKind.String, i, end); i = end
                    }
                    c == '$' && i + 1 < code.length &&
                        (isIdentStart(code[i + 1]) || code[i + 1] == '{') -> {
                        val end = if (code[i + 1] == '{') {
                            scanDelimited(code, i, "}")
                        } else {
                            var j = i + 1
                            while (j < code.length && isIdentPart(code[j])) j++
                            j
                        }
                        emit(CodeTokenKind.Type, i, end); i = end
                    }
                    c.isDigit() -> {
                        val end = scanNumber(code, i)
                        emit(CodeTokenKind.Number, i, end); i = end
                    }
                    isIdentStart(c) -> {
                        var end = i + 1
                        while (end < code.length && (isIdentPart(code[end]) || code[end] == '-' ||
                                code[end] == '.' || code[end] == '/')
                        ) end++
                        val kind = if (code.substring(i, end) in BASH_KEYWORDS) {
                            CodeTokenKind.Keyword
                        } else {
                            CodeTokenKind.Plain
                        }
                        emit(kind, i, end); i = end
                    }
                    else -> i++
                }
            }
            if (plainStart < code.length) tokens += CodeToken(CodeTokenKind.Plain, plainStart, code.length)
            return tokens
        }
    }
}

/** 深色代码块底（0xFF202631）上的哑光高亮配色。 */
internal object CodeHighlightPalette {
    val keyword = Color(0xFF82AFFF)
    val type = Color(0xFF5BC8C0)
    val string = Color(0xFF9ECE6A)
    val number = Color(0xFFFF9E64)
    val comment = Color(0xFF8590A6)
}

/**
 * 把代码块文本转成带语法高亮的 AnnotatedString；不支持的语言返回纯文本
 * （调用方按等宽字体渲染即可，不会出现无样式断层）。任意输入不抛异常。
 */
internal fun highlightedCodeAnnotatedString(language: String?, code: String): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    val tokens = runCatching { CodeSyntaxTokenizer.tokenize(language, code) }
        .getOrElse { return AnnotatedString(code) }
    return buildAnnotatedString {
        tokens.forEach { token ->
            val style = when (token.kind) {
                CodeTokenKind.Keyword -> SpanStyle(color = CodeHighlightPalette.keyword)
                CodeTokenKind.Type -> SpanStyle(color = CodeHighlightPalette.type)
                CodeTokenKind.String -> SpanStyle(color = CodeHighlightPalette.string)
                CodeTokenKind.Number -> SpanStyle(color = CodeHighlightPalette.number)
                CodeTokenKind.Comment -> SpanStyle(
                    color = CodeHighlightPalette.comment,
                    fontStyle = FontStyle.Italic
                )
                CodeTokenKind.Plain -> null
            }
            val segment = code.substring(token.start, token.end)
            if (style != null) {
                withStyle(style) { append(segment) }
            } else {
                append(segment)
            }
        }
    }
}
