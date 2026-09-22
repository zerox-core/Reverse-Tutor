package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSyntaxHighlightTest {

    private fun kindsAt(tokens: List<CodeToken>, code: String, fragment: String): List<CodeTokenKind> {
        val offset = code.indexOf(fragment)
        assertTrue("fragment not found: $fragment", offset >= 0)
        return tokens.filter { it.start < offset + fragment.length && it.end > offset }
            .map { it.kind }
    }

    private fun assertCoverage(tokens: List<CodeToken>, length: Int) {
        var cursor = 0
        tokens.forEach { token ->
            assertEquals("tokens must be contiguous", cursor, token.start)
            assertTrue(token.end > token.start)
            cursor = token.end
        }
        assertEquals("tokens must cover the whole input", length, cursor)
    }

    @Test
    fun supportedLanguageNormalizesAliasesAndRejectsUnknown() {
        assertEquals("kotlin", CodeSyntaxTokenizer.supportedLanguage("Kotlin"))
        assertEquals("kotlin", CodeSyntaxTokenizer.supportedLanguage("kt"))
        assertEquals("python", CodeSyntaxTokenizer.supportedLanguage(" py "))
        assertEquals("bash", CodeSyntaxTokenizer.supportedLanguage("sh"))
        assertEquals("xml", CodeSyntaxTokenizer.supportedLanguage("html"))
        assertNull(CodeSyntaxTokenizer.supportedLanguage("brainfuck"))
        assertNull(CodeSyntaxTokenizer.supportedLanguage(null))
    }

    @Test
    fun unknownLanguageFallsBackToSinglePlainToken() {
        val code = "fn main() {}"
        assertEquals(
            listOf(CodeToken(CodeTokenKind.Plain, 0, code.length)),
            CodeSyntaxTokenizer.tokenize("cobol", code)
        )
    }

    @Test
    fun emptyCodeProducesNoTokensAndEmptyAnnotatedString() {
        assertTrue(CodeSyntaxTokenizer.tokenize("kotlin", "").isEmpty())
        assertEquals("", highlightedCodeAnnotatedString("kotlin", "").text)
    }

    @Test
    fun kotlinKeywordsStringsCommentsAndNumbersAreClassified() {
        val code = """
            // 行注释
            val name: String = "小明" // tail
            /* 块注释
               跨行 */
            fun add(a: Int, b: Int): Int {
                val raw = """ + "\"\"\"" + """原始""" + "\"\"\"" + """
                return a + b + 42
            }
        """.trimIndent()
        val tokens = CodeSyntaxTokenizer.tokenize("kotlin", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "val"))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "\"小明\""))
        assertEquals(listOf(CodeTokenKind.Comment), kindsAt(tokens, code, "行注释"))
        assertEquals(listOf(CodeTokenKind.Comment), kindsAt(tokens, code, "跨行"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "fun"))
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "String"))
        assertEquals(listOf(CodeTokenKind.Number), kindsAt(tokens, code, "42"))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "原始"))
    }

    @Test
    fun javaAnnotationAndClassNamesAreTypes() {
        val code = "@Override\npublic String greet() { return \"hi\"; }"
        val tokens = CodeSyntaxTokenizer.tokenize("java", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "@Override"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "public"))
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "String"))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "\"hi\""))
    }

    @Test
    fun pythonTripleQuotedCommentBuiltinAndDecorator() {
        val code = "# 注释\n@dataclass\nclass Point:\n    \"\"\"文档\n    字符串\"\"\"\n    def x(self):\n        return len(self.y)  # tail"
        val tokens = CodeSyntaxTokenizer.tokenize("python", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.Comment), kindsAt(tokens, code, "注释"))
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "@dataclass"))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "文档"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "def"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "return"))
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "len"))
    }

    @Test
    fun pythonPrefixedRawStringIsSingleStringToken() {
        val code = "path = r\"C:\\new\\folder\""
        val tokens = CodeSyntaxTokenizer.tokenize("python", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "r\"C:\\new\\folder\""))
    }

    @Test
    fun jsonKeysAreKeywordsAndValuesKeepTheirKinds() {
        val code = "{\"name\": \"小明\", \"age\": 12, \"ok\": true, \"pet\": null}"
        val tokens = CodeSyntaxTokenizer.tokenize("json", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "\"name\""))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "\"小明\""))
        assertEquals(listOf(CodeTokenKind.Number), kindsAt(tokens, code, "12"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "true"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "null"))
    }

    @Test
    fun xmlTagsAttributesValuesAndCommentsAreClassified() {
        val code = "<!-- 注释 -->\n<view id=\"main\" enabled>\n文本\n</view>"
        val tokens = CodeSyntaxTokenizer.tokenize("xml", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.Comment), kindsAt(tokens, code, "注释"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "view"))
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "id"))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "\"main\""))
        assertEquals(listOf(CodeTokenKind.Plain), kindsAt(tokens, code, "文本"))
    }

    @Test
    fun sqlIsCaseInsensitiveWithCommentsAndStrings() {
        val code = "SELECT name, `age` FROM users WHERE note = 'x' -- 注释\nLIMIT 10"
        val tokens = CodeSyntaxTokenizer.tokenize("sql", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "SELECT"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "FROM"))
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "`age`"))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "'x'"))
        assertEquals(listOf(CodeTokenKind.Comment), kindsAt(tokens, code, "注释"))
        assertEquals(listOf(CodeTokenKind.Number), kindsAt(tokens, code, "10"))
    }

    @Test
    fun bashKeywordsVariablesCommentsAndStrings() {
        val code = "# 注释\nif [ -f /tmp/a ]; then\n  echo \$HOME 'ok'\nfi"
        val tokens = CodeSyntaxTokenizer.tokenize("bash", code)
        assertCoverage(tokens, code.length)
        assertEquals(listOf(CodeTokenKind.Comment), kindsAt(tokens, code, "注释"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "if"))
        assertEquals(listOf(CodeTokenKind.Keyword), kindsAt(tokens, code, "then"))
        assertEquals(listOf(CodeTokenKind.Type), kindsAt(tokens, code, "\$HOME"))
        assertEquals(listOf(CodeTokenKind.String), kindsAt(tokens, code, "'ok'"))
    }

    @Test
    fun streamingUnterminatedStringDoesNotCrashAndCoversToEnd() {
        val code = "fun main() {\n    val s = \"未闭合"
        val tokens = CodeSyntaxTokenizer.tokenize("kotlin", code)
        assertCoverage(tokens, code.length)
        assertEquals(CodeTokenKind.String, tokens.last().kind)
        assertEquals(code.length, tokens.last().end)
    }

    @Test
    fun streamingUnterminatedBlockCommentAndTripleQuoteAreSafe() {
        val cStyle = "int x = 1; /* 注释未闭合\nint y = 2;"
        val cTokens = CodeSyntaxTokenizer.tokenize("java", cStyle)
        assertCoverage(cTokens, cStyle.length)
        assertEquals(CodeTokenKind.Comment, cTokens.last().kind)
        val pyCode = "def f():\n    s = \"\"\"未闭合"
        val pyTokens = CodeSyntaxTokenizer.tokenize("python", pyCode)
        assertCoverage(pyTokens, pyCode.length)
        assertEquals(CodeTokenKind.String, pyTokens.last().kind)
    }

    @Test
    fun annotatedStringKeepsFullTextAndAppliesExpectedColors() {
        val code = "val x = 1 // 注释"
        val annotated = highlightedCodeAnnotatedString("kotlin", code)
        assertEquals(code, annotated.text)
        val keywordSpans = annotated.spanStyles.filter {
            it.item.color == CodeHighlightPalette.keyword
        }
        assertTrue(keywordSpans.any { annotated.text.substring(it.start, it.end) == "val" })
        val commentSpans = annotated.spanStyles.filter {
            it.item.color == CodeHighlightPalette.comment
        }
        assertTrue(commentSpans.any { annotated.text.substring(it.start, it.end) == "// 注释" })
    }

    @Test
    fun annotatedStringForUnsupportedLanguageIsPlainButIntact() {
        val code = "MOVE 1 TO X."
        val annotated = highlightedCodeAnnotatedString("cobol", code)
        assertEquals(code, annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }
}
