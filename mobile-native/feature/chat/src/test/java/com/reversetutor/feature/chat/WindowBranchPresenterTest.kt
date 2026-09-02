package com.reversetutor.feature.chat

import com.reversetutor.core.domain.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBranchPresenterTest {
    @Test
    fun child_state_exposes_parent_navigation_and_explicit_heartbeat_action() {
        val state = WindowBranchPresenter.present(childSnapshot())

        assertEquals("parent-1", state.parent?.id)
        assertTrue(state.heartbeat.canToggle)
        assertTrue(state.current.kind == WindowKind.CHILD)
    }

    @Test
    fun root_state_has_no_parent_or_delete_action() {
        val state = WindowBranchPresenter.present(
            childSnapshot().copy(
                current = WindowBranchItem("root-1", "根会话", WindowKind.TASK_ROOT),
                parent = null,
                heartbeat = WindowBranchHeartbeat(true, false, "主动对话默认开启")
            )
        )

        assertEquals(null, state.parent)
        assertFalse(state.heartbeat.canToggle)
    }

    @Test
    fun absent_delta_never_enables_merge() {
        val state = WindowBranchPresenter.present(childSnapshot())

        assertFalse(state.mergeEnabled)
        assertEquals("暂无可归并的结构化记忆", state.mergeReason)
    }

    @Test
    fun child_delete_stays_disabled_until_a_safe_delete_capability_is_published() {
        val state = WindowBranchPresenter.present(childSnapshot())

        assertFalse(state.deleteEnabled)
        assertEquals("删除能力暂不可用", state.deleteReason)
    }

    private fun childSnapshot() = WindowBranchSnapshot(
        current = WindowBranchItem("child-1", "子分支", WindowKind.CHILD),
        parent = WindowBranchItem("parent-1", "父分支", WindowKind.TASK_ROOT),
        children = emptyList(),
        heartbeat = WindowBranchHeartbeat(false, true, "主动对话未开启"),
        merge = WindowBranchMerge.UnavailableNoDelta
    )
}
