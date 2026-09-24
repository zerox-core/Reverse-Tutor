package com.reversetutor.core.domain

import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Full-chain validation (task R83 direction 2): feed 100 hand-written mock
 * conversation messages (no LLM, deterministic rule extractor only) through
 * the production intake pipeline and verify a structured memory/graph
 * projection comes out:
 *
 *   100 mock messages
 *     -> WindowMemoryIntakeCoordinator (sliding window eviction, batch extraction)
 *     -> RuleBasedMemoryExtractor (layer-1 observations)
 *     -> WindowActiveValuePolicy (layer-3 active values)
 *     -> WindowMemoryPolicy.aggregatePatterns (layer-2 patterns)
 *     -> GlobalProjectionPolicy (what may leave the window)
 *     -> MasteryLedgerProjection (learning facts -> mastery snapshots)
 *
 * All output is dumped to build/mock-graph-pipeline.json for inspection.
 */
class MockConversationGraphPipelineValidation {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private class InMemoryMessagePort : WindowIntakeMessagePort {
        val messages = mutableListOf<WindowIntakeMessage>()
        override suspend fun listMessages(sessionId: String): List<WindowIntakeMessage> = messages.toList()
    }

    private class InMemoryStore : WindowIntakeStore {
        var watermark: WindowIntakeWatermark? = null
        val observations = mutableListOf<WindowLocalObservation>()
        val activeValues = linkedMapOf<String, WindowActiveValue>()
        var rollingSummary: String? = null
        var summaryCoversUntil: String? = null

        override suspend fun loadWatermark(sessionId: String): WindowIntakeWatermark? = watermark
        override suspend fun saveWatermark(sessionId: String, watermark: WindowIntakeWatermark) {
            this.watermark = watermark
        }
        override suspend fun appendObservations(sessionId: String, observations: List<WindowLocalObservation>) {
            this.observations += observations
        }
        override suspend fun loadActiveValue(
            sessionId: String,
            category: WindowObservationCategory,
            slotKey: String,
        ): WindowActiveValue? = activeValues[category.name + "/" + slotKey]
        override suspend fun saveActiveValue(sessionId: String, value: WindowActiveValue) {
            activeValues[value.category.name + "/" + value.slotKey] = value
        }
        override suspend fun loadRollingSummary(sessionId: String): String? = rollingSummary
        override suspend fun saveRollingSummary(
            sessionId: String,
            summary: String,
            coversUntilMessageId: String,
            nowEpochMillis: Long,
        ) {
            rollingSummary = summary
            summaryCoversUntil = coversUntilMessageId
        }
    }

    private fun hourOf(epochMillis: Long): Int =
        Instant.ofEpochMilli(epochMillis).atZone(zone).hour

    /** 100 hand-written mock messages: alternating user/assistant, 10 minutes apart,
     *  starting 2026-09-20 20:00 (Asia/Shanghai), so the batch crosses midnight
     *  and exercises the late-night rules. No LLM involved. */
    private fun buildMockConversation(): List<WindowIntakeMessage> {
        val base = Instant.parse("2026-09-20T12:00:00Z").toEpochMilli() // 20:00 +08:00
        val topics = listOf("圆锥曲线", "集合", "导数", "数列", "三角函数")
        val goalTexts = listOf(
            "我要这周把%s的基础题型过一遍",
            "我想在下个月考试前搞定%s",
            "目标是%s不再丢分",
            "我打算每天刷十道%s的题",
            "争取把%s的错题全部消化掉",
        )
        val prefTexts = listOf(
            "我喜欢先看书再做题",
            "我不喜欢一上来就刷难题",
            "别给我大段大段的文字，讲重点就行",
            "我习惯晚上学习",
            "以后都先讲思路再讲答案",
        )
        val learningTexts = listOf(
            "我懂了，%s的几何意义原来是这样",
            "明白了，刚才那一步我终于想通了",
            "原来是定义域没考虑，难怪错了",
            "我错了，刚才把符号看反了",
            "这道题搞不懂，%s的判定条件太绕了",
        )
        val lateNightTexts = listOf("还在写代码", "还在刷题", "我在学习数学", "还在改代码")
        val smallTalk = listOf("嗯嗯，好的", "哈哈可以", "OK", "行，知道了")
        val assistantTexts = listOf(
            "我们来看这道例题，先分析已知条件。",
            "这一步的关键是把条件转化成方程。",
            "你可以试着自己推一遍，我在旁边看。",
            "这个结论很重要，回头出题会用到。",
            "先停下来眨眨眼，喝口水再继续。",
        )
        val assistantReminders = listOf(
            "都凌晨一点了，快去睡觉吧，别熬夜了。",
            "两点了，该睡了，熬夜效率反而低。",
        )

        val messages = mutableListOf<WindowIntakeMessage>()
        var userGoal = 0
        var userPref = 0
        var userLearn = 0
        var userLate = 0
        var userSmall = 0
        for (i in 0 until 100) {
            val at = base + i * 600_000L
            val hour = hourOf(at)
            val lateNight = hour >= 23 || hour < 6
            val isUser = i % 2 == 0
            val text: String
            if (isUser) {
                text = when {
                    // After an assistant reminder, the user's next message is feedback.
                    i >= 2 && messages.last().text.contains("睡觉") -> "好，写完这题我就睡"
                    lateNight && i % 8 == 0 -> lateNightTexts[userLate++ % lateNightTexts.size]
                    i % 10 == 0 -> {
                        val g = userGoal++
                        goalTexts[g % goalTexts.size].format(topics[(g / goalTexts.size) % topics.size])
                    }
                    i % 10 == 4 -> prefTexts[userPref++ % prefTexts.size]
                    i % 10 == 6 -> {
                        val l = userLearn++
                        learningTexts[l % learningTexts.size].format(topics[l % topics.size])
                    }
                    else -> smallTalk[userSmall++ % smallTalk.size]
                }
            } else {
                text = if (lateNight && i % 16 == 15) {
                    assistantReminders[(i / 16) % assistantReminders.size]
                } else {
                    assistantTexts[(i / 2) % assistantTexts.size]
                }
            }
            messages += WindowIntakeMessage(
                messageId = "m%03d".format(i),
                role = if (isUser) ExtractionRole.USER else ExtractionRole.ASSISTANT,
                text = text,
                occurredAtEpochMillis = at,
            )
        }
        return messages
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    @Test
    fun hundredMockMessagesProduceStructuredGraphProjection() = runBlocking {
        val port = InMemoryMessagePort()
        val store = InMemoryStore()
        val coordinator = WindowMemoryIntakeCoordinator(
            messagePort = port,
            store = store,
            config = WindowMemoryConfig(), // production defaults: 60 messages / 6000 tokens
            hourOfDayAt = { ts -> hourOf(ts) },
            foldSummary = { _, current, evicted ->
                (current ?: "") + "[fold:" + evicted.first().messageId + ".." + evicted.last().messageId + "]"
            },
        )

        val conversation = buildMockConversation()
        assertEquals(100, conversation.size)

        // Feed 10 turns of 10 messages; intake runs after each turn, exactly
        // like BackgroundGenerationWorker dispatches it after a completed turn.
        val reports = mutableListOf<WindowIntakeReport>()
        for (turn in 0 until 10) {
            port.messages += conversation.subList(turn * 10, turn * 10 + 10)
            reports += coordinator.onTurnCompleted("session-1", conversation[turn * 10 + 9].occurredAtEpochMillis)
        }

        // ---- Layer 2: patterns over stored observations -------------------
        val patterns = WindowMemoryPolicy.aggregatePatterns(
            store.observations.map {
                CategorizedObservation(
                    category = it.category,
                    occurredAtEpochMillis = it.occurredAtEpochMillis,
                    hourOfDay = hourOf(it.occurredAtEpochMillis),
                )
            },
        )

        // ---- Global projection (what may leave the window) ----------------
        val projections = store.activeValues.values.associate { value ->
            (value.category.name + "/" + value.slotKey) to GlobalProjectionPolicy.channelFor(value)
        }

        // ---- Learning facts -> mastery snapshots --------------------------
        val knowledgePoints = listOf("圆锥曲线", "集合", "导数", "数列", "三角函数")
        val evidenceCycle = listOf("explanation", "retrieval", "transfer", "delayed_retrieval", "correction")
        val resultCycle = listOf("passed", "passed", "partial", "passed", "failed")
        val learningFacts = mutableListOf<LearningFactReceipt>()
        store.observations
            .filter {
                it.category == WindowObservationCategory.LEARNING_EVENT ||
                    it.category == WindowObservationCategory.GOAL_STATEMENT
            }
            .forEachIndexed { index, obs ->
                learningFacts += LearningFactReceipt(
                    knowledgePoint = knowledgePoints[index % knowledgePoints.size],
                    evidenceType = evidenceCycle[index % evidenceCycle.size],
                    result = resultCycle[index % resultCycle.size],
                    confidence = obs.confidence.toFloat(),
                    sourceWindowId = "session-1",
                    sourceTurnId = obs.provenanceHandle,
                    occurredAtEpochMillis = obs.occurredAtEpochMillis,
                )
            }
        val mastery = MasteryLedgerProjection(snapshotLimit = 50).project(learningFacts)
        val due = MasteryLedgerProjection(snapshotLimit = 50)
            .projectDue(learningFacts, conversation.last().occurredAtEpochMillis + 2L * 86_400_000L)

        // ---- Assertions: structural integrity of the projection -----------
        val totalExtracted = reports.sumOf { it.observationsExtracted }
        assertTrue("no observations extracted from 100 messages", totalExtracted > 0)
        assertEquals(totalExtracted, store.observations.size)
        assertTrue(
            "observation without turn provenance",
            store.observations.all { it.provenanceHandle.startsWith("turn:m") },
        )
        val allowedValues = setOf("stated_goal", "stated_preference", "acknowledged_reminder", "learning_moment")
        assertTrue(
            "observation carries non-normalized value",
            store.observations.all { it.value in allowedValues || it.value.startsWith("late_night_") },
        )
        assertTrue(
            "active value weight out of bounds",
            store.activeValues.values.all { it.weight in 0.0..1.0 && it.revision >= 1L },
        )
        assertTrue(
            "no learning fact left the window",
            projections.values.any { it == ProjectionChannel.LEARNING_FACT },
        )
        assertTrue("no learning facts built", learningFacts.isNotEmpty())
        assertTrue("no mastery snapshots", mastery.isNotEmpty())
        assertTrue(
            "mastery score out of [0,100]",
            mastery.all { it.score in 0f..100f },
        )
        assertTrue(
            "review interval off the ladder",
            mastery.all { it.reviewIntervalDays in listOf(0, 1, 3, 7, 14) },
        )
        assertTrue(
            "every knowledge point attempts must equal its receipt count",
            mastery.all { snap -> snap.attempts == learningFacts.count { it.knowledgePoint == snap.knowledgePoint } },
        )
        assertNotNull("watermark never advanced", store.watermark)
        assertTrue("rolling summary never folded", store.rollingSummary != null)
        assertTrue("no goal observed", store.observations.any { it.category == WindowObservationCategory.GOAL_STATEMENT })
        assertTrue("no learning event observed", store.observations.any { it.category == WindowObservationCategory.LEARNING_EVENT })

        // ---- Dump the structured result ----------------------------------
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"messageCount\": ").append(conversation.size).append(",\n")
        sb.append("  \"config\": {\"tokenBudget\": ").append(WindowMemoryConfig.DEFAULT_TOKEN_BUDGET)
            .append(", \"messageCap\": ").append(WindowMemoryConfig.DEFAULT_MESSAGE_CAP).append("},\n")
        sb.append("  \"turnReports\": [")
        sb.append(
            reports.joinToString(",") {
                "{\"kept\":${it.windowKeptCount},\"evicted\":${it.evictedCount},\"processed\":${it.newlyProcessedCount}," +
                    "\"obs\":${it.observationsExtracted},\"created\":${it.createdCount},\"reinforced\":${it.reinforcedCount}," +
                    "\"superseded\":${it.supersededCount},\"conflict\":${it.conflictCount},\"ignored\":${it.ignoredCount}," +
                    "\"folded\":${it.summaryFolded}}"
            },
        )
        sb.append("],\n")
        sb.append("  \"totals\": {\"observations\": ").append(store.observations.size)
            .append(", \"activeValues\": ").append(store.activeValues.size)
            .append(", \"learningFacts\": ").append(learningFacts.size)
            .append(", \"masterySnapshots\": ").append(mastery.size).append("},\n")
        sb.append("  \"observations\": [")
        sb.append(
            store.observations.joinToString(",") {
                "{\"cat\":\"${it.category}\",\"value\":\"${it.value}\",\"conf\":${it.confidence}," +
                    "\"salience\":\"${it.salience}\",\"src\":\"${it.sourceClass}\",\"prov\":\"${it.provenanceHandle}\"}"
            },
        )
        sb.append("],\n")
        sb.append("  \"activeValues\": [")
        sb.append(
            store.activeValues.values.joinToString(",") {
                "{\"cat\":\"${it.category}\",\"value\":\"${jsonEscape(it.value)}\",\"rev\":${it.revision}," +
                    "\"weight\":${it.weight},\"channel\":\"${GlobalProjectionPolicy.channelFor(it)}\"}"
            },
        )
        sb.append("],\n")
        sb.append("  \"patterns\": [")
        sb.append(
            patterns.joinToString(",") {
                "{\"cat\":\"${it.category}\",\"count\":${it.occurrenceCount},\"hours\":${it.activeHours.sorted()}}"
            },
        )
        sb.append("],\n")
        sb.append("  \"watermark\": \"").append(store.watermark?.lastProcessedMessageId).append("\",\n")
        sb.append("  \"rollingSummary\": \"").append(jsonEscape(store.rollingSummary ?: "")).append("\",\n")
        sb.append("  \"mastery\": [")
        sb.append(
            mastery.joinToString(",") {
                "{\"kp\":\"${it.knowledgePoint}\",\"score\":${it.score},\"band\":\"${it.band}\"," +
                    "\"intervalDays\":${it.reviewIntervalDays},\"attempts\":${it.attempts}}"
            },
        )
        sb.append("],\n")
        sb.append("  \"dueIn2Days\": [")
        sb.append(due.joinToString(",") { "\"$it\"" })
        sb.append("]\n}\n")

        val outFile = File("build/mock-graph-pipeline.json")
        outFile.parentFile?.mkdirs()
        outFile.writeText(sb.toString(), Charsets.UTF_8)
        println("MOCK_GRAPH_DUMP=" + outFile.absolutePath)
    }
}
