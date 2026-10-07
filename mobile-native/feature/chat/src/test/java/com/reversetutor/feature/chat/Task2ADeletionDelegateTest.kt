package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class Task2ADeletionDelegateTest {
    @Test
    fun dangerousActionDelegatesEveryStepToTask2APort() {
        val calls = mutableListOf<String>()
        val delegate = Task2ADeletionDelegate(
            request = { calls += "request" },
            confirm = { calls += "confirm" },
            dismiss = { calls += "dismiss" },
            undo = { calls += "undo" }
        )

        delegate.request()
        delegate.confirm()
        delegate.dismiss()
        delegate.undo()

        assertEquals(listOf("request", "confirm", "dismiss", "undo"), calls)
    }
}
