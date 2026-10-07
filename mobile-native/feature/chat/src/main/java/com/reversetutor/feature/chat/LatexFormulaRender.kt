package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 1c-c2 LaTeX 公式渲染：轻量解析自绘路线（无原生/WebView 依赖）。
 *
 * 选型权衡（动工时拍板，按 1c 开发文档要求记录）：
 * - 采用「纯 Kotlin 子集解析 + Compose 自绘」：无 WebView 初始化开销、不占原生内存、
 *   流式渲染中途可安全回退、JVM 单测可全覆盖解析层。
 * - WebView + KaTeX/MathJax 兜底方案未启用：覆盖全量 TeX 但每条公式一个 WebView
 *   在会话列表里不可接受（滚动性能/内存），且加载异步会闪源码。若后续出现子集外的
 *   高频语法（如矩阵 pmatrix、多行对齐 aligned），再评估单公式 WebView 池化兜底。
 *
 * 支持子集：普通字符/数字/运算符、希腊字母与常见符号命令、\frac、\sqrt（含次数）、
 * 上下标 ^ _（可组合）、\sum/\prod/\int/\lim 带上下限、\text/\mathrm 等直立文本、
 * \binom、\overline 等重音（组合字符）、\( \) 与 \left \right 括号、常见函数名
 * （sin/cos/log/ln/lim 等）。未知命令按字面文本渲染（优雅降级，不报错）。
 *
 * 契约：parse 对任意输入不抛异常；结构性不完整（流式中途的半完整块：未闭合花括号、
 * \frac 缺参、悬空上下标等）返回 Failure，调用方回退为原始源码展示，不炸屏。
 */
sealed interface LatexNode {
    /** 连排普通字符（字母/数字/运算符/空格），渲染时纯字母串按数学惯例斜体。 */
    data class Atom(val text: String) : LatexNode

    /** 命令映射后的数学符号（α × ≤ ∑ ∈ 等），直立渲染。 */
    data class MathSymbol(val text: String) : LatexNode

    /** 直立文本（\text/\mathrm 内容与函数名 sin/log 等）。 */
    data class Upright(val text: String) : LatexNode

    /** 花括号分组，可作为上下标的整体基元。 */
    data class Group(val children: List<LatexNode>) : LatexNode

    data class Frac(val numerator: List<LatexNode>, val denominator: List<LatexNode>) : LatexNode

    data class Sqrt(val index: List<LatexNode>?, val radicand: List<LatexNode>) : LatexNode

    /** 上下标；sup/sub 至少一个非空。 */
    data class Script(val base: LatexNode, val sup: List<LatexNode>?, val sub: List<LatexNode>?) : LatexNode
}

sealed interface LatexParseResult {
    data class Success(val nodes: List<LatexNode>) : LatexParseResult
    data class Failure(val reason: String) : LatexParseResult
}

object LatexMiniParser {

    fun parse(source: String): LatexParseResult {
        if (source.isBlank()) return LatexParseResult.Failure("empty")
        return runCatching {
            val parser = Parser(source)
            val nodes = parser.parseNodes(stopAtBrace = false)
            if (!parser.atEnd()) {
                LatexParseResult.Failure("unexpected '${parser.peek()}' at ${parser.pos}")
            } else {
                LatexParseResult.Success(nodes)
            }
        }.getOrElse { LatexParseResult.Failure(it.message ?: "parse error") }
    }

    private class ParseError(message: String) : Exception(message)

    private class Parser(val source: String) {
        var pos = 0
            private set

        fun atEnd(): Boolean = pos >= source.length
        fun peek(): Char = source[pos]

        fun parseNodes(stopAtBrace: Boolean): List<LatexNode> {
            val nodes = mutableListOf<LatexNode>()
            val pending = StringBuilder()

            fun flushPending() {
                if (pending.isNotEmpty()) {
                    nodes += LatexNode.Atom(pending.toString())
                    pending.clear()
                }
            }

            while (!atEnd()) {
                val c = peek()
                if (c == '}') {
                    if (stopAtBrace) {
                        flushPending()
                        return nodes
                    }
                    throw ParseError("unbalanced '}' at $pos")
                }
                if (c == '{') {
                    pos++
                    val children = parseNodes(stopAtBrace = true)
                    expectCloseBrace()
                    flushPending()
                    nodes += LatexNode.Group(children)
                    continue
                }
                if (c == '^' || c == '_') {
                    // 上下标只挂在前一个「原子」上：连排 Atom 只取最后一个非空字符作基元，
                    // 基元与上标之间的空格按数学模式惯例丢弃。
                    val base = takeBase(nodes, pending)
                        ?: throw ParseError("dangling script at $pos")
                    var sup: List<LatexNode>? = null
                    var sub: List<LatexNode>? = null
                    var guard = 0
                    while (!atEnd() && (peek() == '^' || peek() == '_')) {
                        if (guard++ >= 2) throw ParseError("too many scripts at $pos")
                        val kind = peek()
                        pos++
                        skipSpaces()
                        val arg = parseScriptArg()
                        if (kind == '^') {
                            if (sup != null) throw ParseError("duplicate superscript at $pos")
                            sup = arg
                        } else {
                            if (sub != null) throw ParseError("duplicate subscript at $pos")
                            sub = arg
                        }
                    }
                    nodes += LatexNode.Script(base, sup, sub)
                    continue
                }
                if (c == '\\') {
                    flushPending()
                    nodes += parseCommand()
                    continue
                }
                if (c == '&') throw ParseError("alignment '&' unsupported at $pos")
                pending.append(c)
                pos++
            }
            if (stopAtBrace) throw ParseError("unclosed '{'")
            flushPending()
            return nodes
        }

        /** 取脚本基元：pending 最后一个非空字符，其余前缀保留为普通 Atom。 */
        private fun takeBase(nodes: MutableList<LatexNode>, pending: StringBuilder): LatexNode? {
            if (pending.isNotEmpty()) {
                val text = pending.toString().trimEnd()
                pending.clear()
                if (text.isEmpty()) return nodes.removeLastOrNull()
                if (text.length > 1) nodes += LatexNode.Atom(text.dropLast(1))
                return LatexNode.Atom(text.last().toString())
            }
            return nodes.removeLastOrNull()
        }

        private fun parseScriptArg(): List<LatexNode> {
            if (atEnd()) throw ParseError("script arg missing at end")
            return when (peek()) {
                '{' -> {
                    pos++
                    val children = parseNodes(stopAtBrace = true)
                    expectCloseBrace()
                    children
                }
                '\\' -> listOf(parseCommand())
                '}' -> throw ParseError("script arg empty at $pos")
                else -> {
                    val c = peek()
                    pos++
                    listOf(LatexNode.Atom(c.toString()))
                }
            }
        }

        /** 读取命令参数：花括号组或单 token。缺参抛 ParseError（流式半完整块安全回退）。 */
        private fun parseRequiredArg(command: String): List<LatexNode> {
            skipSpaces()
            if (atEnd()) throw ParseError("$command missing argument at end")
            return when (peek()) {
                '{' -> {
                    pos++
                    val children = parseNodes(stopAtBrace = true)
                    expectCloseBrace()
                    children
                }
                '}' -> throw ParseError("$command missing argument at $pos")
                '\\' -> listOf(parseCommand())
                else -> {
                    val c = peek()
                    pos++
                    listOf(LatexNode.Atom(c.toString()))
                }
            }
        }

        private fun expectCloseBrace() {
            if (atEnd() || peek() != '}') throw ParseError("unclosed '{' at $pos")
            pos++
        }

        private fun skipSpaces() {
            while (!atEnd() && peek() == ' ') pos++
        }

        private fun parseCommand(): LatexNode {
            pos++ // consume '\'
            if (atEnd()) throw ParseError("dangling '\\' at end")
            val c = peek()
            // 单字符转义命令
            when (c) {
                '%', '$', '#', '&', '{', '}' -> {
                    pos++
                    return LatexNode.Atom(c.toString())
                }
                '_' -> {
                    pos++
                    return LatexNode.Atom("_")
                }
                ',', ';', ':', ' ' -> {
                    pos++
                    return LatexNode.Atom(" ")
                }
                '!' -> {
                    pos++
                    return LatexNode.Group(emptyList())
                }
                '\\' -> {
                    pos++
                    return LatexNode.Atom(" ")
                }
            }
            if (!c.isLetter()) throw ParseError("invalid command escape '\\$c' at $pos")
            val start = pos
            while (!atEnd() && peek().isLetter()) pos++
            val name = source.substring(start, pos)
            return commandNode(name)
        }

        private fun readDelimiter(name: String): LatexNode {
            skipSpaces()
            if (atEnd()) throw ParseError("$name missing delimiter at end")
            val c = peek()
            pos++
            if (c == '.') return LatexNode.Group(emptyList())
            val mapped = when (c) {
                '\\' -> {
                    // \left\| 等形式
                    if (!atEnd() && peek() == '|') pos++
                    "‖"
                }
                else -> c.toString()
            }
            return LatexNode.Atom(mapped)
        }

        private fun commandNode(name: String): LatexNode = when {
            name == "frac" || name == "dfrac" || name == "tfrac" || name == "cfrac" -> {
                val numerator = parseRequiredArg("\\frac")
                val denominator = parseRequiredArg("\\frac")
                LatexNode.Frac(numerator, denominator)
            }
            name == "sqrt" -> {
                skipSpaces()
                var index: List<LatexNode>? = null
                if (!atEnd() && peek() == '[') {
                    pos++
                    val idxNodes = mutableListOf<LatexNode>()
                    val idxPending = StringBuilder()
                    while (!atEnd() && peek() != ']') {
                        val ch = peek()
                        if (ch == '\\') {
                            if (idxPending.isNotEmpty()) {
                                idxNodes += LatexNode.Atom(idxPending.toString())
                                idxPending.clear()
                            }
                            idxNodes += parseCommand()
                        } else {
                            idxPending.append(ch)
                            pos++
                        }
                    }
                    if (atEnd()) throw ParseError("\\sqrt index unclosed '['")
                    pos++
                    if (idxPending.isNotEmpty()) idxNodes += LatexNode.Atom(idxPending.toString())
                    index = idxNodes
                }
                val radicand = parseRequiredArg("\\sqrt")
                LatexNode.Sqrt(index, radicand)
            }
            name == "binom" || name == "dbinom" || name == "tbinom" -> {
                val top = parseRequiredArg("\\binom")
                val bottom = parseRequiredArg("\\binom")
                LatexNode.Group(
                    listOf(LatexNode.Atom("("), LatexNode.Frac(top, bottom), LatexNode.Atom(")"))
                )
            }
            name == "text" || name == "mathrm" || name == "mathbf" || name == "mathit" ||
                name == "mathsf" || name == "operatorname" || name == "textbf" || name == "textit" -> {
                val arg = parseRequiredArg("\\$name")
                LatexNode.Upright(plainText(arg))
            }
            name == "overline" || name == "bar" || name == "hat" || name == "widehat" ||
                name == "tilde" || name == "widetilde" || name == "dot" || name == "ddot" || name == "vec" -> {
                val arg = parseRequiredArg("\\$name")
                val text = plainText(arg)
                val combining = when (name) {
                    "overline", "bar" -> "̅"
                    "hat", "widehat" -> "̂"
                    "tilde", "widetilde" -> "̃"
                    "dot" -> "̇"
                    "ddot" -> "̈"
                    else -> "⃗"
                }
                if (text.isEmpty()) LatexNode.Upright(combining) else LatexNode.Upright(text + combining)
            }
            name == "left" || name == "right" || name == "big" || name == "Big" ||
                name == "bigg" || name == "Bigg" || name == "bigl" || name == "bigr" ||
                name == "Bigl" || name == "Bigr" -> readDelimiter("\\$name")
            name == "quad" -> LatexNode.Atom("  ")
            name == "qquad" -> LatexNode.Atom("    ")
            name in SYMBOLS -> LatexNode.MathSymbol(SYMBOLS.getValue(name))
            name in FUNCTIONS -> LatexNode.Upright(name)
            else -> LatexNode.Atom("\\$name") // 未知命令：字面渲染，优雅降级不报错
        }
    }

    /** 提取节点树的纯文本（供 \text/\mathrm 与重音参数、行内拍平使用）。 */
    internal fun plainText(nodes: List<LatexNode>): String = buildString {
        nodes.forEach { node ->
            when (node) {
                is LatexNode.Atom -> append(node.text)
                is LatexNode.MathSymbol -> append(node.text)
                is LatexNode.Upright -> append(node.text)
                is LatexNode.Group -> append(plainText(node.children))
                is LatexNode.Frac -> append(plainText(node.numerator)).append('/').append(plainText(node.denominator))
                is LatexNode.Sqrt -> append("√(").append(plainText(node.radicand)).append(')')
                is LatexNode.Script -> {
                    append(plainText(listOf(node.base)))
                    node.sup?.let { append('^').append(plainText(it)) }
                    node.sub?.let { append('_').append(plainText(it)) }
                }
            }
        }
    }

    private val SYMBOLS: Map<String, String> = mapOf(
        // 希腊字母小写
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ϵ", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "ϕ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        // 希腊字母大写
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        // 二元运算与关系
        "times" to "×", "div" to "÷", "cdot" to "·", "ast" to "∗",
        "pm" to "±", "mp" to "∓", "oplus" to "⊕", "otimes" to "⊗",
        "circ" to "∘", "bullet" to "∙", "star" to "⋆", "cap" to "∩", "cup" to "∪",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
        "ll" to "≪", "gg" to "≫", "doteq" to "≐",
        "prec" to "≺", "succ" to "≻", "preceq" to "≼", "succeq" to "≽",
        // 集合与逻辑
        "in" to "∈", "notin" to "∉", "ni" to "∋",
        "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "supseteq" to "⊇",
        "emptyset" to "∅", "varnothing" to "∅", "setminus" to "∖",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬", "lnot" to "¬",
        "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨",
        "because" to "∵", "therefore" to "∴",
        // 箭头
        "rightarrow" to "→", "to" to "→", "leftarrow" to "←", "gets" to "←",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "leftrightarrow" to "↔", "mapsto" to "↦",
        "uparrow" to "↑", "downarrow" to "↓",
        "longrightarrow" to "⟶", "Longrightarrow" to "⟹",
        // 大运算符
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "bigcup" to "⋃", "bigcap" to "⋂", "bigoplus" to "⨁", "bigotimes" to "⨂",
        // 杂项
        "partial" to "∂", "nabla" to "∇", "ell" to "ℓ", "hbar" to "ℏ",
        "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ", "wp" to "℘",
        "prime" to "′", "angle" to "∠", "measuredangle" to "∡",
        "triangle" to "△", "square" to "□", "blacksquare" to "■",
        "perp" to "⊥", "parallel" to "∥", "nparallel" to "∦",
        "mid" to "∣", "nmid" to "∤", "degree" to "°",
        "surd" to "√", "checkmark" to "✓", "dagger" to "†",
        "ldots" to "…", "dots" to "…", "cdots" to "…", "vdots" to "⋮", "ddots" to "⋱",
        "infty" to "∞"
    )

    private val FUNCTIONS: Set<String> = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "coth",
        "log", "ln", "lg", "exp", "min", "max", "sup", "inf", "lim",
        "limsup", "liminf", "gcd", "lcm", "det", "deg", "dim", "ker", "arg", "Pr", "mod"
    )
}

// ---------------- 行内渲染（AnnotatedString 路线） ----------------

enum class LatexSegmentStyle { Normal, Superscript, Subscript }

data class LatexSegment(val text: String, val style: LatexSegmentStyle = LatexSegmentStyle.Normal)

/** 把公式节点树拍平为行内片段；结构性构造（分数/根式）退化为线性记号。 */
object LatexInlineFlattener {

    fun flatten(nodes: List<LatexNode>): List<LatexSegment> {
        val out = mutableListOf<LatexSegment>()
        flattenInto(nodes, LatexSegmentStyle.Normal, out)
        return mergeAdjacent(out)
    }

    private fun flattenInto(nodes: List<LatexNode>, style: LatexSegmentStyle, out: MutableList<LatexSegment>) {
        nodes.forEach { node ->
            when (node) {
                is LatexNode.Atom -> out += LatexSegment(node.text, style)
                is LatexNode.MathSymbol -> out += LatexSegment(node.text, style)
                is LatexNode.Upright -> out += LatexSegment(node.text, style)
                is LatexNode.Group -> flattenInto(node.children, style, out)
                is LatexNode.Script -> {
                    flattenInto(listOf(node.base), style, out)
                    node.sup?.let { flattenInto(it, LatexSegmentStyle.Superscript, out) }
                    node.sub?.let { flattenInto(it, LatexSegmentStyle.Subscript, out) }
                }
                is LatexNode.Frac -> {
                    val numerator = LatexMiniParser.plainText(node.numerator)
                    val denominator = LatexMiniParser.plainText(node.denominator)
                    out += LatexSegment(wrapIfComplex(numerator), style)
                    out += LatexSegment("/", style)
                    out += LatexSegment(wrapIfComplex(denominator), style)
                }
                is LatexNode.Sqrt -> {
                    val index = node.index?.let { LatexMiniParser.plainText(it) }
                    val radicand = LatexMiniParser.plainText(node.radicand)
                    out += LatexSegment((index ?: "") + "√(" + radicand + ")", style)
                }
            }
        }
    }

    /** 多字符的分子/分母加括号，避免歧义：\frac{1}{2}→1/2；\frac{a+b}{c}→(a+b)/c。 */
    private fun wrapIfComplex(text: String): String =
        if (text.length > 1) "($text)" else text

    private fun mergeAdjacent(segments: List<LatexSegment>): List<LatexSegment> {
        if (segments.isEmpty()) return segments
        val out = mutableListOf<LatexSegment>()
        segments.forEach { segment ->
            val last = out.lastOrNull()
            if (last != null && last.style == segment.style) {
                out[out.lastIndex] = last.copy(text = last.text + segment.text)
            } else {
                out += segment
            }
        }
        return out.filter { it.text.isNotEmpty() }
    }
}

/**
 * 行内 $...$ 公式 → 带上下标样式的 AnnotatedString。
 * 解析失败（含流式半完整块）返回 null，调用方回退原始源码样式。
 */
internal fun latexInlineAnnotatedString(source: String): AnnotatedString? {
    val result = LatexMiniParser.parse(source)
    if (result !is LatexParseResult.Success) return null
    val segments = runCatching { LatexInlineFlattener.flatten(result.nodes) }.getOrNull() ?: return null
    if (segments.isEmpty()) return null
    return buildAnnotatedString {
        segments.forEach { segment ->
            when (segment.style) {
                LatexSegmentStyle.Normal -> withStyle(
                    SpanStyle(color = LatexFormulaPalette.inlineInk, fontFamily = FontFamily.Serif)
                ) { append(segment.text) }
                LatexSegmentStyle.Superscript -> withStyle(
                    SpanStyle(
                        color = LatexFormulaPalette.inlineInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 0.72.em,
                        baselineShift = BaselineShift.Superscript
                    )
                ) { append(segment.text) }
                LatexSegmentStyle.Subscript -> withStyle(
                    SpanStyle(
                        color = LatexFormulaPalette.inlineInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 0.72.em,
                        baselineShift = BaselineShift.Subscript
                    )
                ) { append(segment.text) }
            }
        }
    }
}

// ---------------- 块级渲染（$$...$$，Compose 自绘） ----------------

internal object LatexFormulaPalette {
    val card = Color(0xFFF7F9FC)
    val border = Color(0xFFD8DEE9)
    val ink = Color(0xFF22304A)
    val label = Color(0xFF6D778C)
    val inlineInk = Color(0xFF334D7A)
    const val ruleStroke = 1.2f
}

/**
 * 块级公式卡片：头部标签 + 复制源码按钮 + Compose 自绘公式树。
 * 长公式横向滚动（与代码块一致）。
 */
@Composable
internal fun RichFormulaBlock(
    source: String,
    nodes: List<LatexNode>,
    onCopySource: (String) -> ChatClipboardResult
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LatexFormulaPalette.card,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, LatexFormulaPalette.border)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "公式",
                    modifier = Modifier.weight(1f),
                    color = LatexFormulaPalette.label,
                    fontSize = 11.sp
                )
                IconButton(onClick = { onCopySource(source) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制公式",
                        modifier = Modifier.size(18.dp),
                        tint = LatexFormulaPalette.label
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                LatexNodesView(nodes = nodes, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun LatexNodesView(nodes: List<LatexNode>, fontSize: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        nodes.forEach { node ->
            LatexNodeView(node = node, fontSize = fontSize)
        }
    }
}

@Composable
private fun LatexNodeView(node: LatexNode, fontSize: TextUnit) {
    val scriptSize = fontSize * 0.72
    val subSize = fontSize * 0.82
    when (node) {
        is LatexNode.Atom -> Text(
            text = node.text,
            color = LatexFormulaPalette.ink,
            fontSize = fontSize,
            fontFamily = FontFamily.Serif,
            fontStyle = if (node.text.all { it.isLetter() }) FontStyle.Italic else FontStyle.Normal
        )
        is LatexNode.MathSymbol -> Text(
            text = node.text,
            color = LatexFormulaPalette.ink,
            fontSize = fontSize,
            fontFamily = FontFamily.Serif
        )
        is LatexNode.Upright -> Text(
            text = node.text,
            color = LatexFormulaPalette.ink,
            fontSize = fontSize,
            fontFamily = FontFamily.Serif
        )
        is LatexNode.Group -> LatexNodesView(nodes = node.children, fontSize = fontSize)
        is LatexNode.Script -> Row(verticalAlignment = Alignment.CenterVertically) {
            LatexNodeView(node = node.base, fontSize = fontSize)
            when {
                node.sup != null && node.sub != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LatexNodesView(nodes = node.sup, fontSize = scriptSize)
                    LatexNodesView(nodes = node.sub, fontSize = scriptSize)
                }
                node.sup != null -> Box(modifier = Modifier.align(Alignment.Top)) {
                    LatexNodesView(nodes = node.sup, fontSize = scriptSize)
                }
                node.sub != null -> Box(modifier = Modifier.align(Alignment.Bottom)) {
                    LatexNodesView(nodes = node.sub, fontSize = scriptSize)
                }
            }
        }
        is LatexNode.Frac -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LatexNodesView(nodes = node.numerator, fontSize = subSize)
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 14.dp)
                    .height(1.dp)
                    .background(LatexFormulaPalette.ink)
            )
            Spacer(modifier = Modifier.height(2.dp))
            LatexNodesView(nodes = node.denominator, fontSize = subSize)
        }
        is LatexNode.Sqrt -> Row(verticalAlignment = Alignment.CenterVertically) {
            node.index?.let { indexNodes ->
                Box(modifier = Modifier.align(Alignment.Top).padding(top = 1.dp)) {
                    LatexNodesView(nodes = indexNodes, fontSize = scriptSize)
                }
            }
            Text(
                text = "√",
                color = LatexFormulaPalette.ink,
                fontSize = fontSize * 1.12,
                fontFamily = FontFamily.Serif
            )
            Box(
                modifier = Modifier
                    .drawBehind {
                        drawLine(
                            color = LatexFormulaPalette.ink,
                            start = Offset(0f, LatexFormulaPalette.ruleStroke / 2),
                            end = Offset(size.width, LatexFormulaPalette.ruleStroke / 2),
                            strokeWidth = LatexFormulaPalette.ruleStroke
                        )
                    }
                    .padding(top = 2.dp, start = 1.dp)
            ) {
                LatexNodesView(nodes = node.radicand, fontSize = fontSize)
            }
        }
    }
}
