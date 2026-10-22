package com.reversetutor.feature.chat

/**
 * LaTeX 片段 → 可读 Unicode 数学文本的纯文本美化器（公式显示兜底）。
 *
 * 分工：真正的二维数学排版（分数线/根式横线）由 [LatexMiniParser] + Compose 自绘承担；
 * 本对象只负责「排版层拿不出结果」时把 LaTeX 源码转成用户读得懂的线性数学文本，
 * 保证任何输入都不会把带反斜杠的原始 LaTeX 暴露给用户。
 *
 * 契约：
 * - 任意输入都不抛异常（内部 runCatching 再退化为「去掉反斜杠与 $ 定界符」的结果）；
 * - 纯函数：同一输入重复调用结果一致；
 * - 剥掉 `$`/`$$`/`\(`/`\)`/`\[`/`\]` 定界符；未知 `\command{...}` 只保留花括号内内容，不残留反斜杠；
 * - 只做线性化：`\frac{a}{b}` → `(a)/(b)`、`\sqrt[n]{x}` → `ⁿ√(x)`，不画分数线。
 */
object LatexPlainTextBeautifier {

    fun beautify(source: String): String {
        if (source.isEmpty()) return ""
        return runCatching { Scanner(source).render(stopAtBrace = false) }
            .getOrElse { source.filter { it != '\\' && it != '$' } }
    }

    /**
     * 化学方程式专用线性美化器（针对 `\ce{...}` / `\pu{...}` 内容）。
     *
     * mhchem 语法在移动端原生渲染里没有落点：`\ce{2H2 + O2 -> 2H2O}` 原样展示就是
     * 一串看不懂的字母数字。这里做确定性线性化，保证任何输入都不残留反斜杠：
     * - 元素后的数字自动转下标：H2O → H₂O、Fe2(SO4)3 → Fe₂(SO₄)₃；系数不受影响：2H2O → 2H₂O；
     * - 反应箭头：`->` → ⟶、`<=>` → ⇌、`<-` → ⟵；`->[条件]` → —条件→；
     * - 离子电荷上标：`Fe^3+` → Fe³⁺、`SO4^2-` → SO₄²⁻；
     * - 沉淀 / 气体：末尾 `v` → ↓、末尾 `^` → ↑；
     * - `\text{...}` 与分组花括号外壳剥离，只保留内容。
     *
     * 纯函数、任意输入不抛异常（内部 runCatching 再退化为去反斜杠结果）。
     */
    fun beautifyChemistry(raw: String): String = runCatching {
        val src = raw
            .replace("\\text{", "{")
            .replace("\\mathrm{", "{")
            .replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\ ", " ")
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("<=>", i) -> {
                    i += 3
                    // 可逆反应条件：<=>[高温高压]（条件在箭头上方，线性化放箭头前）
                    if (i < src.length && src[i] == '[') {
                        val close = src.indexOf(']', i)
                        if (close > i) {
                            val condition = beautifyChemistry(src.substring(i + 1, close))
                            out.append(" —").append(condition).append("⇌ ")
                            i = close + 1
                        } else {
                            out.append(" ⇌ ")
                        }
                    } else {
                        out.append(" ⇌ ")
                    }
                }
                src.startsWith("->", i) -> {
                    i += 2
                    // 反应条件：->[点燃] / ->[\text{加热}]
                    if (i < src.length && src[i] == '[') {
                        val close = src.indexOf(']', i)
                        if (close > i) {
                            val condition = beautifyChemistry(src.substring(i + 1, close))
                            out.append(" —").append(condition).append("→ ")
                            i = close + 1
                        } else {
                            out.append(" → ")
                        }
                    } else {
                        out.append(" → ")
                    }
                }
                src.startsWith("<-", i) -> {
                    out.append(" ← ")
                    i += 2
                }
                // 分组外壳剥离（\ce{...} 内部还会再分组）
                c == '{' || c == '}' -> i++
                c == '^' -> {
                    val next = src.getOrNull(i + 1)
                    if (next == null || next.isWhitespace()) {
                        // 气体符号：去掉前面多余空格再贴 ↑
                        if (out.isNotEmpty() && out.last() == ' ') out.deleteCharAt(out.length - 1)
                        out.append('↑')
                        i++
                    } else {
                        // 离子电荷上标
                        i++
                        val charge = readScriptRun(src, i) { ch -> ch.isDigit() || ch == '+' || ch == '-' }
                        i = charge.second
                        out.append(toSuperscript(charge.first) ?: "^(${charge.first})")
                    }
                }
                c == '_' -> {
                    i++
                    val sub = readScriptRun(src, i) { ch -> ch.isDigit() || ch.isLetter() }
                    i = sub.second
                    out.append(toSubscript(sub.first) ?: "_(${sub.first})")
                }
                // 元素/括号后的数字自动下标（系数不受影响：前面不是字母/右括号的数字保持原样）
                c.isDigit() && isSubscriptPosition(src, i) -> {
                    var j = i
                    while (j < src.length && src[j].isDigit()) j++
                    val sub = toSubscript(src.substring(i, j))
                    if (sub != null) {
                        out.append(sub)
                        i = j
                    } else {
                        out.append(c)
                        i++
                    }
                }
                // 沉淀符号：单独出现的 v（前后是空白或行尾）
                (c == 'v' || c == 'V') && isPrecipitateMark(src, i) -> {
                    if (out.isNotEmpty() && out.last() == ' ') out.deleteCharAt(out.length - 1)
                    out.append('↓')
                    i++
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        out.toString().replace(Regex(" {2,}"), " ").trim()
    }.getOrElse { raw.filter { it != '\\' } }

    private fun toSuperscript(text: String): String? = mapChars(text, SUPERSCRIPTS)

    private fun toSubscript(text: String): String? = mapChars(text, SUBSCRIPTS)

    /** 该位置的数字是否应作为化学下标：紧跟在元素字母 / 右括号之后（系数与反应条件里的数字保持原样）。 */
    private fun isSubscriptPosition(src: String, index: Int): Boolean {
        if (index <= 0) return false
        val prev = src[index - 1]
        return prev.isLetter() || prev == ')' || prev == ']'
    }

    /** 单独出现的 v / V 视作沉淀符号（mhchem 写法：`CaCO3 v`，前面可以是空白）。 */
    private fun isPrecipitateMark(src: String, index: Int): Boolean {
        val next = src.getOrNull(index + 1)
        val nextOk = next == null || next.isWhitespace() || next == '+' || next == '=' || next == ')' || next == ']'
        // 前面必须是空白/边界（独立成词的 v），紧跟字母的 v 是元素符号的一部分，不能动。
        val prev = src.getOrNull(index - 1)
        val prevOk = prev == null || prev.isWhitespace() || prev == ')' || prev == ']'
        return nextOk && prevOk
    }

    /** 读取上下标内容：`{...}` 取组内原文，否则按 [accept] 连续读取（不消耗无匹配输入）。 */
    private fun readScriptRun(src: String, start: Int, accept: (Char) -> Boolean): Pair<String, Int> {
        if (start >= src.length) return "" to start
        if (src[start] == '{') {
            var i = start + 1
            val out = StringBuilder()
            while (i < src.length && src[i] != '}') {
                out.append(src[i])
                i++
            }
            return out.toString() to (if (i < src.length) i + 1 else i)
        }
        var i = start
        val out = StringBuilder()
        while (i < src.length && accept(src[i])) {
            out.append(src[i])
            i++
        }
        return out.toString() to i
    }

    private fun mapChars(text: String, table: Map<Char, String>): String? {
        val out = StringBuilder()
        text.forEach { ch ->
            val mapped = table[ch] ?: return null
            out.append(mapped)
        }
        return out.toString()
    }

    private class Scanner(private val src: String) {
        private var pos = 0

        /** 线性渲染文本；[stopAtBrace] 为真时遇到配对右花括号即返回（不抛异常，半截输入也安全）。 */
        fun render(stopAtBrace: Boolean): String {
            val out = StringBuilder()
            while (pos < src.length) {
                val c = src[pos]
                when {
                    c == '}' -> {
                        pos++
                        if (stopAtBrace) return out.toString()
                        // 顶层多余的右花括号直接丢弃，避免输出悬空括号
                    }
                    c == '{' -> {
                        pos++
                        out.append(render(stopAtBrace = true))
                    }
                    c == '\\' -> out.append(renderCommand())
                    c == '$' -> pos++ // $ / $$ 定界符剥离
                    c == '^' -> {
                        pos++
                        out.append(script(superscript = true))
                    }
                    c == '_' -> {
                        pos++
                        out.append(script(superscript = false))
                    }
                    // 不可见空白与对齐符线性化为空格（~ 是不换行空格，& 是矩阵/多行对齐）
                    c == '~' || c == '&' -> {
                        pos++
                        out.append(' ')
                    }
                    else -> {
                        out.append(c)
                        pos++
                    }
                }
            }
            return out.toString()
        }

        /** 上下标：能整体映射成 Unicode 上下标就映射，否则退化为 `^(...)`/`_(...)`，绝不残留反斜杠。 */
        private fun script(superscript: Boolean): String {
            val raw = argument()?.trim().orEmpty()
            if (raw.isEmpty()) return ""
            val mapped = if (superscript) toSuperscript(raw) else toSubscript(raw)
            return mapped ?: if (superscript) "^($raw)" else "_($raw)"
        }

        /** 读取一个参数：花括号组 / 单个字符 / 单条命令；没有参数可读时返回 null（不消耗输入）。 */
        private fun argument(): String? {
            skipSpaces()
            if (pos >= src.length) return null
            return when (val c = src[pos]) {
                '{' -> {
                    pos++
                    render(stopAtBrace = true)
                }
                '\\' -> renderCommand()
                '}' -> null
                else -> {
                    pos++
                    c.toString()
                }
            }
        }

        private fun renderCommand(): String {
            pos++ // 吃掉反斜杠
            if (pos >= src.length) return "" // 悬空反斜杠：丢弃，绝不留给用户
            val c = src[pos]
            when (c) {
                '%', '$', '#', '&', '{', '}', '_' -> {
                    pos++
                    return c.toString() // 转义字符：\% → %、\{ → { 等
                }
                ',', ';', ':' -> {
                    pos++
                    return " " // 细空格
                }
                '!' -> {
                    pos++
                    return "" // 负空格
                }
                '(', ')', '[', ']' -> {
                    pos++
                    return "" // \( \) \[ \] 定界符
                }
                '\\' -> {
                    pos++
                    return " " // \\ 换行线性化为空格
                }
                else -> {
                    if (!c.isLetter()) {
                        pos++
                        return c.toString() // \ 后跟非字母：按字面（含 \ 空格）
                    }
                }
            }
            val start = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            return commandText(src.substring(start, pos))
        }

        /**
         * 读取一个参数的**原始文本**（不做数学渲染）：`{...}` 取组内原文，否则读到空白为止。
         *
         * 化学式必须走这条路径：数学渲染器会把 `^`/`_` 当上下标吃掉，`\ce{2H2 ^}` 的
         * 气体符号会被渲染成电荷 ⁺，化学语义直接丢失。
         */
        private fun rawArgument(): String {
            skipSpaces()
            if (pos >= src.length) return ""
            if (src[pos] == '{') {
                val start = pos
                var depth = 0
                while (pos < src.length) {
                    when (src[pos]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val body = src.substring(start + 1, pos)
                                pos++
                                return body
                            }
                        }
                    }
                    pos++
                }
                // 未闭合（流式半完整块）：取到结尾，安全降级
                return src.substring(start + 1)
            }
            val start = pos
            while (pos < src.length && !src[pos].isWhitespace()) pos++
            return src.substring(start, pos)
        }

        private fun commandText(name: String): String = when {
            name == "ce" || name == "pu" -> beautifyChemistry(rawArgument())
            // mhchem 条件箭头：\xrightarrow{点燃} / \xrightarrow[Δ]{点燃}（模型常用来写化学方程式）
            name == "xrightarrow" || name == "xRightarrow" -> xArrow(rightward = true)
            name == "xleftarrow" || name == "xLeftarrow" -> xArrow(rightward = false)
            // 可逆反应：\rightleftharpoons / \xrightleftharpoons{条件}
            name == "rightleftharpoons" -> "⇌"
            name == "xrightleftharpoons" -> xEquilibrium()
            name in FRACTIONS -> fraction()
            name == "sqrt" -> sqrt()
            name in TEXT_COMMANDS -> argument().orEmpty()
            name in ACCENTS -> accent(name)
            name in DELIMITERS -> delimiterPrefix()
            name == "begin" || name == "end" -> {
                argument()
                "" // 环境标记整体隐去，矩阵/多行内容本身照常输出
            }
            name == "quad" -> "  "
            name == "qquad" -> "    "
            name == "bmod" -> "mod"
            name == "pmod" -> " (mod ${argument().orEmpty()})"
            name in SYMBOLS -> SYMBOLS.getValue(name)
            name in FUNCTIONS -> name
            name in IGNORED_COMMANDS -> ""
            // 未知命令：去掉反斜杠命令名，保留花括号内内容（\foo{bar} → bar、\foo → 空）
            else -> argument().orEmpty()
        }

        private fun fraction(): String {
            val numerator = argument()
            val denominator = argument()
            if (numerator.isNullOrEmpty() && denominator.isNullOrEmpty()) return ""
            return "(${numerator.orEmpty()})/(${denominator.orEmpty()})"
        }

        private fun sqrt(): String {
            skipSpaces()
            val index = if (pos < src.length && src[pos] == '[') {
                pos++
                val start = pos
                while (pos < src.length && src[pos] != ']') pos++
                val raw = src.substring(start, pos)
                if (pos < src.length) pos++ // 吃掉 ']'
                beautify(raw) // 指数内容同样美化，避免 \sqrt[\alpha]{x} 残留反斜杠
            } else {
                null
            }
            val prefix = index?.let { toSuperscript(it) ?: "^($it)" }.orEmpty()
            val radicand = argument()
            return if (radicand.isNullOrEmpty()) "$prefix√" else "$prefix√($radicand)"
        }

        private fun accent(name: String): String {
            val arg = argument().orEmpty()
            val combining = when (name) {
                "overline", "bar" -> "\u0305"
                "hat", "widehat" -> "\u0302"
                "tilde", "widetilde" -> "\u0303"
                "dot" -> "\u0307"
                "ddot" -> "\u0308"
                else -> "\u20D7"
            }
            return arg + combining
        }

        private fun delimiterPrefix(): String {
            skipSpaces()
            if (pos < src.length && src[pos] == '.') pos++ // \left. / \right. 隐形定界符
            return "" // 定界符命令本身隐去，后面的括号由正常文本流输出
        }

        private fun skipSpaces() {
            while (pos < src.length && src[pos] == ' ') pos++
        }

        /** 读取 `[...]` 原文（可选参数，如 \xrightarrow[Δ]{点燃} 的下方标注）。 */
        private fun rawBracketGroup(): String? {
            skipSpaces()
            if (pos >= src.length || src[pos] != '[') return null
            val start = pos
            while (pos < src.length && src[pos] != ']') pos++
            return if (pos < src.length) {
                val body = src.substring(start + 1, pos)
                pos++
                body
            } else {
                src.substring(start + 1) // 未闭合：取到结尾，安全降级
            }
        }

        /**
         * 条件箭头线性化：`\xrightarrow{点燃}` → —点燃→（无条件时就是 →）。
         * 条件放上方标注优先，其次下方标注；字形选 — / → / ←（常见字形，系统字体可兜底）。
         */
        private fun xArrow(rightward: Boolean): String {
            val below = rawBracketGroup()?.trim()
            val above = argument().orEmpty().trim()
            val label = above.ifBlank { below.orEmpty() }
            val arrow = if (rightward) "→" else "←"
            return if (label.isBlank()) arrow else "—${beautify(label)}$arrow"
        }

        /** 可逆反应箭头：`\xrightleftharpoons{高温高压}` → —高温高压⇌（无条件时就是 ⇌）。 */
        private fun xEquilibrium(): String {
            val below = rawBracketGroup()?.trim()
            val above = argument().orEmpty().trim()
            val label = above.ifBlank { below.orEmpty() }
            return if (label.isBlank()) "⇌" else "—${beautify(label)}⇌"
        }
    }

    private val FRACTIONS = setOf("frac", "dfrac", "tfrac", "cfrac")

    private val TEXT_COMMANDS = setOf(
        "text", "mathrm", "mathbf", "mathit", "mathsf", "operatorname",
        "textbf", "textit", "mbox", "textrm", "textsf", "texttt", "textnormal"
    )

    private val ACCENTS = setOf(
        "overline", "bar", "hat", "widehat", "tilde", "widetilde", "dot", "ddot", "vec"
    )

    private val DELIMITERS = setOf(
        "left", "right", "middle", "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr"
    )

    /** 只影响排版样式、不产生可见文本的命令。 */
    private val IGNORED_COMMANDS = setOf(
        "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle", "limits", "nolimits",
        "mathstrut", "mathop", "mathrel", "mathbin", "mathord", "mathpunct", "mathopen", "mathclose",
        "rm", "bf", "it", "tt", "sf", "cal", "label", "tag", "nonumber", "notag"
    )

    private val SUPERSCRIPTS: Map<Char, String> = mapOf(
        '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
        '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹",
        '+' to "⁺", '-' to "⁻", '=' to "⁼", '(' to "⁽", ')' to "⁾",
        'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ", 'd' to "ᵈ", 'e' to "ᵉ", 'f' to "ᶠ", 'g' to "ᵍ",
        'h' to "ʰ", 'i' to "ⁱ", 'j' to "ʲ", 'k' to "ᵏ", 'l' to "ˡ", 'm' to "ᵐ", 'n' to "ⁿ",
        'o' to "ᵒ", 'p' to "ᵖ", 'r' to "ʳ", 's' to "ˢ", 't' to "ᵗ", 'u' to "ᵘ", 'v' to "ᵛ",
        'w' to "ʷ", 'x' to "ˣ", 'y' to "ʸ", 'z' to "ᶻ",
        'A' to "ᴬ", 'B' to "ᴮ", 'D' to "ᴰ", 'E' to "ᴱ", 'G' to "ᴳ", 'H' to "ᴴ", 'I' to "ᴵ",
        'J' to "ᴶ", 'K' to "ᴷ", 'L' to "ᴸ", 'M' to "ᴹ", 'N' to "ᴺ", 'O' to "ᴼ", 'P' to "ᴾ",
        'R' to "ᴿ", 'T' to "ᵀ", 'U' to "ᵁ", 'V' to "ⱽ", 'W' to "ᵂ"
    )

    private val SUBSCRIPTS: Map<Char, String> = mapOf(
        '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄",
        '5' to "₅", '6' to "₆", '7' to "₇", '8' to "₈", '9' to "₉",
        '+' to "₊", '-' to "₋", '=' to "₌", '(' to "₍", ')' to "₎",
        'a' to "ₐ", 'e' to "ₑ", 'h' to "ₕ", 'i' to "ᵢ", 'j' to "ⱼ", 'k' to "ₖ", 'l' to "ₗ",
        'm' to "ₘ", 'n' to "ₙ", 'o' to "ₒ", 'p' to "ₚ", 'r' to "ᵣ", 's' to "ₛ", 't' to "ₜ",
        'u' to "ᵤ", 'v' to "ᵥ", 'x' to "ₓ"
    )

    /** 与 [LatexMiniParser] 的符号表保持一致：兜底文本与迷你排版看到的符号是同一套。 */
    private val SYMBOLS: Map<String, String> = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ϵ", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "ϕ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "times" to "×", "div" to "÷", "cdot" to "·", "ast" to "∗",
        "pm" to "±", "mp" to "∓", "oplus" to "⊕", "otimes" to "⊗",
        "circ" to "∘", "bullet" to "∙", "star" to "⋆", "cap" to "∩", "cup" to "∪",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
        "ll" to "≪", "gg" to "≫", "doteq" to "≐",
        "prec" to "≺", "succ" to "≻", "preceq" to "≼", "succeq" to "≽",
        "in" to "∈", "notin" to "∉", "ni" to "∋",
        "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "supseteq" to "⊇",
        "emptyset" to "∅", "varnothing" to "∅", "setminus" to "∖",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬", "lnot" to "¬",
        "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨",
        "because" to "∵", "therefore" to "∴",
        "rightarrow" to "→", "to" to "→", "leftarrow" to "←", "gets" to "←",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "leftrightarrow" to "↔", "mapsto" to "↦",
        "uparrow" to "↑", "downarrow" to "↓",
        "longrightarrow" to "⟶", "Longrightarrow" to "⟹",
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "bigcup" to "⋃", "bigcap" to "⋂", "bigoplus" to "⨁", "bigotimes" to "⨂",
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
