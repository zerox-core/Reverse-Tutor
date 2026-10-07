package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V2-001 Red：会话结构化候选写入记忆层的契约测试。
 *
 * 被测对象 [MemoryIntakeCoordinator] 尚不存在，本文件当前预期编译失败（Red）。
 * 契约来源：docs/NEWMP-V2-001-memory-intake.md；约束来源：
 * [MemoryObservation.ALLOWED_PERSISTED_FIELDS] 与 [CompanionMemoryEvolutionPolicy.canEmit]。
 */
class MemoryIntakeContractTest {

    private val policy = CompanionMemoryEvolutionPolicy
    private val intake = MemoryIntakeCoordinator(policy)

    private val companionRoot = WindowRef(id = "companion-root", rootId = "companion-root", parentId = null, kind = WindowKind.COMPANION_ROOT)
    private val taskWindow = WindowRef(id = "task-1", rootId = "task-1", parentId = null, kind = WindowKind.TASK_ROOT)

    private fun companionObservation(value: String, partition: CompanionMemoryPartition? = CompanionMemoryPartition.STABLE_PREFERENCE) =
        MemoryObservation(
            domain = MemoryDomain.COMPANION,
            partition = partition,
            normalizedValue = value,
            sourceClass = MemoryObservationSourceClass.USER_STATEMENT,
            observedAtEpochMillis = 1_000L,
            confidence = 0.9f,
            provenanceHandle = "turn-1"
        )

    @Test
    fun `companion root window may emit companion candidates`() {
        val accepted = intake.candidatesFromTurn(companionRoot, listOf(companionObservation("喜欢先讲结论")))
        assertEquals(1, accepted.size)
    }

    @Test
    fun `task window cannot emit companion domain candidates`() {
        val accepted = intake.candidatesFromTurn(taskWindow, listOf(companionObservation("不该进入伴侣域")))
        assertTrue("任务窗不得向伴侣记忆域产出候选", accepted.isEmpty())
    }

    @Test
    fun `companion candidate without partition is rejected`() {
        val accepted = intake.candidatesFromTurn(companionRoot, listOf(companionObservation("缺分区", partition = null)))
        assertTrue("伴侣域候选必须携带分区", accepted.isEmpty())
    }

    @Test
    fun `candidates per turn are bounded`() {
        val proposed = (1..8).map { companionObservation("候选$it") }
        val accepted = intake.candidatesFromTurn(companionRoot, proposed)
        assertTrue(
            "每轮候选必须有界：期望 <= ${MemoryIntakeCoordinator.MAX_CANDIDATES_PER_TURN}，实际 ${accepted.size}",
            accepted.size <= MemoryIntakeCoordinator.MAX_CANDIDATES_PER_TURN
        )
    }
}
