package com.reversetutor.feature.chat

import com.reversetutor.core.data.llm.ChatGenerationInput
import com.reversetutor.core.data.llm.ChatGenerationOutcome
import com.reversetutor.core.llm.LlmGenerationToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGenerationCoordinatorTest {
    @Test
    fun successPublishesPendingThenIdle() = runTest {
        val states = mutableListOf<ChatGenerationUiState>()
        val coordinator = ChatGenerationCoordinator(
            executor = ChatGenerationExecutor { _, _, _ ->
                ChatGenerationOutcome.Generated("assistant-1")
            },
            nowEpochMillis = { 100L }
        )

        coordinator.generate(input("session-1", "token-1"), states::add)

        assertEquals(listOf(ChatGenerationUiState.Pending, ChatGenerationUiState.Idle), states)
    }

    @Test
    fun providerFailureAndTimeoutPublishFailureThenAllowLaterSuccess() = runTest {
        val outcomes = ArrayDeque(
            listOf(
                ChatGenerationOutcome.ProviderFailed("Rate limited"),
                ChatGenerationOutcome.ProviderFailed("Timeout"),
                ChatGenerationOutcome.Generated("assistant-1")
            )
        )
        val states = mutableListOf<ChatGenerationUiState>()
        val coordinator = ChatGenerationCoordinator(
            executor = ChatGenerationExecutor { _, _, _ -> outcomes.removeFirst() }
        )

        coordinator.generate(input("session-1", "token-1"), states::add)
        coordinator.generate(input("session-1", "token-2"), states::add)
        coordinator.generate(input("session-1", "token-3"), states::add)

        assertEquals(
            listOf(
                ChatGenerationUiState.Pending,
                ChatGenerationUiState.Failure("Rate limited"),
                ChatGenerationUiState.Pending,
                ChatGenerationUiState.Failure("Timeout"),
                ChatGenerationUiState.Pending,
                ChatGenerationUiState.Idle
            ),
            states
        )
    }

    @Test
    fun noModelConfiguredPublishesPendingThenNoModel() = runTest {
        val states = mutableListOf<ChatGenerationUiState>()
        val coordinator = ChatGenerationCoordinator(
            executor = ChatGenerationExecutor { _, _, _ -> ChatGenerationOutcome.NoModelConfigured }
        )

        coordinator.generate(input("session-1", "token-1"), states::add)

        assertEquals(listOf(ChatGenerationUiState.Pending, ChatGenerationUiState.NoModel), states)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun staleSessionGenerationPublishesNoTerminalStateAfterInvalidation() = runTest {
        val sessionOneGate = CompletableDeferred<Unit>()
        val sessionOneStates = mutableListOf<ChatGenerationUiState>()
        val sessionTwoStates = mutableListOf<ChatGenerationUiState>()
        val coordinator = ChatGenerationCoordinator(
            executor = ChatGenerationExecutor { input, _, _ ->
                if (input.sessionId == "session-1") {
                    sessionOneGate.await()
                    ChatGenerationOutcome.Generated("assistant-1")
                } else {
                    ChatGenerationOutcome.Generated("assistant-2")
                }
            }
        )

        val sessionOneGeneration = async {
            coordinator.generate(input("session-1", "token-1"), sessionOneStates::add)
        }
        runCurrent()
        coordinator.invalidate()
        coordinator.generate(input("session-2", "token-2"), sessionTwoStates::add)
        sessionOneGate.complete(Unit)
        advanceUntilIdle()
        sessionOneGeneration.await()

        assertEquals(listOf(ChatGenerationUiState.Pending), sessionOneStates)
        assertEquals(
            listOf(ChatGenerationUiState.Pending, ChatGenerationUiState.Idle),
            sessionTwoStates
        )
    }

    private fun input(sessionId: String, token: String) =
        ChatGenerationInput(
            sessionId = sessionId,
            userMessageId = "message-$token",
            userText = "Explain $token",
            token = LlmGenerationToken(token)
        )
}
