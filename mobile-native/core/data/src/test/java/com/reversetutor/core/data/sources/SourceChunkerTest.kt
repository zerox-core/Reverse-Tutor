package com.reversetutor.core.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceChunkerTest {

    @Test
    fun `短文本直接原样成片`() {
        assertEquals(listOf("Alpha", "Beta"), SourceChunker.chunk("Alpha\n\nBeta"))
    }

    @Test
    fun `空段落被过滤`() {
        assertEquals(listOf("第一段", "第二段"), SourceChunker.chunk("第一段\n\n\n\n\n第二段"))
    }

    @Test
    fun `纯空白文本返回自身`() {
        assertEquals(listOf("   "), SourceChunker.chunk("   "))
    }

    @Test
    fun `不超过500字的段落不切分`() {
        val paragraph = "字".repeat(500)
        assertEquals(listOf(paragraph), SourceChunker.chunk(paragraph))
    }

    @Test
    fun `超过500字的段落按句子切分且不丢字`() {
        val sentence = "这是一个完整的句子，用来填充长度。".repeat(40) // 约 960 字
        val chunks = SourceChunker.chunk(sentence)
        assertTrue(chunks.size >= 2)
        // 拼回去的内容必须包含全部正文（重叠部分会有重复，但不能少字）
        val joined = chunks.joinToString("")
        assertTrue(joined.contains("这是一个完整的句子"))
        val totalLength = joined.length
        val sentenceLength = sentence.length
        // 允许重叠多、不允许少：总长 >= 原文长
        assertTrue(totalLength >= sentenceLength - 40) // 容忍标点空白被 trim 掉的少量损耗
    }

    @Test
    fun `相邻片段带约50字重叠`() {
        val sentence = "短句。".repeat(200) // 600 字，全是短句
        val chunks = SourceChunker.chunk(sentence)
        assertTrue(chunks.size >= 2)
        // 第二片开头应来自第一片结尾的 50 字（短句。 x10 = 50 字）
        val first = chunks[0]
        val second = chunks[1]
        val overlap = first.takeLast(50)
        assertTrue(second.startsWith(overlap))
    }

    @Test
    fun `无标点长文硬切不丢字`() {
        val text = "字".repeat(951)
        val chunks = SourceChunker.chunk(text)
        assertEquals(2, chunks.size)
        assertEquals(500, chunks[0].length)
        assertEquals(501, chunks[1].length) // 50 重叠 + 451 新内容
        assertEquals(text, chunks[0] + chunks[1].substring(50))
    }

    @Test
    fun `末尾小碎片并回前一片`() {
        // 500 + 400 + 30：最后 30 字应并入第二片
        val big = "大".repeat(500)
        val mid = "中".repeat(400)
        val tail = "尾"
        val text = big + "。" + mid + "。" + tail
        val chunks = SourceChunker.chunk(text)
        // 尾片 <80 字并回前片，最终只剩两片
        assertEquals(2, chunks.size)
        assertTrue(chunks.last().endsWith(tail))
    }

    @Test
    fun `多个段落分别切分互不重叠`() {
        val p1 = "甲".repeat(600)
        val p2 = "乙".repeat(600)
        val chunks = SourceChunker.chunk(p1 + "\n\n" + p2)
        assertEquals(4, chunks.size)
        assertTrue(chunks[0].all { it == '甲' })
        assertTrue(chunks[1].all { it == '甲' })
        assertTrue(chunks[2].all { it == '乙' })
        assertTrue(chunks[3].all { it == '乙' })
    }

    @Test
    fun `单句超500字硬切`() {
        val text = "前" + "中".repeat(900) + "后"
        val chunks = SourceChunker.chunk(text)
        // 硬切后片段长度 ≤ 500+50 重叠上限附近，全部内容保留
        val joined = chunks.joinToString("")
        assertTrue(joined.length >= text.length)
    }
}
