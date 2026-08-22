# Background Turn Preparation Implementation Plan

> For agentic workers: execute task by task. Steps use checkbox syntax.

**Goal:** After one user message is persisted, prepare bounded context plus a study-policy snapshot, enqueue exactly one background job, and retain Worker as the sole LLM executor and assistant-message writer.

**Architecture:** feature:chat publishes a Compose-free BackgroundTurnPreparationPort. app/wiring/session implements it with ConversationContextAssembler, SessionTurnPolicy, and the existing frozen BackgroundGenerationRepository. ChatRoute invokes it only after ChatSendCoordinator succeeds. Direct generation is removed.

**Tech Stack:** Kotlin, coroutines, Compose route boundary, core:domain, existing background repository, WorkManager, JUnit4.

---

## Non-negotiable boundaries

- Branch is newmp only. Do not merge or push Android.
- Do not edit Track B files SessionAssistantPanel.kt, SessionAssistantReplyHint.kt, or their tests.
- Do not change core:model, core:protocol, core:llm, core:data, Room, DAO, migration, Worker, notification, Python, PWA, signing, or Gradle.
- The approved temporary mode is study. Never parse dialogueStrategy free text.
- Prequeue validation only checks blank input and deleted session. Existing repository validates token only after job persistence.
- Stage exact paths with git add --; never git add dot.

## Files

| Path | Work |
|---|---|
| mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/BackgroundTurnPreparationContracts.kt | New request/result/port contracts, no Compose or Repository. |
| mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/BackgroundTurnPreparationContractsTest.kt | New contract tests. |
| mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationFacade.kt | New pure queued-to-LOADING projection. |
| mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionConversationContractTest.kt | Queued projection test. |
| mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/SessionPolicyInputMapper.kt | Fixed-study configuration mapper. |
| mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/BackgroundTurnPreparationCoordinator.kt | Context, policy, safe snapshot, single enqueue. |
| mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/SessionPolicyInputMapperTest.kt | Mapper tests. |
| mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/BackgroundTurnPreparationCoordinatorTest.kt | Coordinator tests with fakes. |
| mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt; MainActivity.kt; shell/AppShell.kt | Compose and forward exactly one port. |
| mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ChatScreen.kt | Replace post-send direct generation branch, no visual layout change. |
| mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/BackgroundTurnDispatchTest.kt | One message / one request / one job callback test. |

### Task 1: Publish feature port

- [ ] Write failing test:

~~~kotlin
@Test fun unavailable_never_enqueues() = runTest {
    assertEquals(
        BackgroundTurnPreparationResult.Unavailable,
        BackgroundTurnPreparationPort.Unavailable.prepareAndEnqueue(request())
    )
}
~~~

- [ ] Run from mobile-native and expect compile failure:

~~~powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME; .\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.BackgroundTurnPreparationContractsTest" --console=plain
~~~

- [ ] Create contracts:

~~~kotlin
fun interface BackgroundTurnPreparationPort {
    suspend fun prepareAndEnqueue(request: BackgroundTurnPreparationRequest): BackgroundTurnPreparationResult
    data object Unavailable : BackgroundTurnPreparationPort {
        override suspend fun prepareAndEnqueue(request: BackgroundTurnPreparationRequest) =
            BackgroundTurnPreparationResult.Unavailable
    }
}
data class BackgroundTurnPreparationRequest(
    val spaceId: String, val sessionId: String, val userMessageId: String,
    val userText: String, val token: String, val quoteExcerpt: String?,
    val imageAttachments: List<MessageAttachment>, val sessionSnapshot: NewSessionConfiguration?
)
sealed interface BackgroundTurnPreparationResult {
    data class Queued(val jobId: String, val contract: SessionConversationContract) : BackgroundTurnPreparationResult
    data object BlankInput : BackgroundTurnPreparationResult
    data object SessionUnavailable : BackgroundTurnPreparationResult
    data object Unavailable : BackgroundTurnPreparationResult
    data object Failed : BackgroundTurnPreparationResult
}
~~~

Only import MessageAttachment, never a Repository, DAO, Entity, Database, protocol DTO or Compose type.

- [ ] Rerun the focused test; expect BUILD SUCCESSFUL.
- [ ] Commit:

~~~powershell
git add -- mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/BackgroundTurnPreparationContracts.kt mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/BackgroundTurnPreparationContractsTest.kt
git commit -m "feat(chat): publish background turn preparation port"
~~~

### Task 2: Project a queued turn

- [ ] Write failing facade test:

~~~kotlin
@Test fun queued_turn_is_loading_and_keeps_policy_context() {
    val actual = facade.mapQueued("s-1", "turn-1", policyOutput("probe"),
        ConversationContextContract.empty("space-1", "s-1"), emptyList())
    assertEquals(GenerationState.LOADING, actual.generation.state)
    assertEquals("probe", actual.action?.type)
    assertEquals("open_ctx_turn-1", actual.events.single().eventId)
}
~~~

- [ ] Add this pure facade method. It must do no I/O:

~~~kotlin
fun mapQueued(sessionId: String, turnId: String, policy: SessionPolicyOutput,
    context: ConversationContextContract, messages: List<ConversationMessageContract>
) = SessionConversationContract(
    sessionId, turnId, messages, GenerationUiContract(GenerationState.LOADING),
    policy.evaluation, policy.action, policy.processSummary, policy.action.knowledgePoint,
    NextStepContract(policy.processSummary, policy.action.knowledgePoint, policy.action.type),
    context, listOf(ConversationUiEvent("open_ctx_$turnId", ConversationUiEventType.OPEN_CONTEXT))
)
~~~

- [ ] Verify and commit:

~~~powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME; .\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest" --console=plain
git add -- mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationFacade.kt mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionConversationContractTest.kt
git commit -m "feat(chat): project queued session turn contract"
~~~

### Task 3: Implement app-wiring coordinator

- [ ] First write mapper and coordinator tests. Use lambda seams for session check, context, enqueue, messages and clock; do not create Room:

~~~kotlin
@Test fun configuration_always_maps_to_study() {
    val input = NewSessionConfiguration(
        title = "代数", goal = "因式分解", dialogueStrategy = "陪伴式讲解",
        probingIntensity = 5, correctionPersistence = "严格"
    ).toSessionPolicyInput("我不会")
    assertEquals(SessionModeWire.STUDY, input.mode)
    assertEquals(5, input.settings.probingIntensity)
    assertEquals(CorrectionPersistenceWire.PERSISTENT, input.settings.correctionPersistence)
}
@Test fun preparation_enqueues_once_without_direct_generation() = runTest {
    assertTrue(coordinator.prepareAndEnqueue(request("解释一下")) is BackgroundTurnPreparationResult.Queued)
    assertEquals(1, queuedInputs.size)
    assertEquals(0, directGenerationCalls)
}
@Test fun blank_or_deleted_never_enqueues() = runTest {
    assertEquals(BackgroundTurnPreparationResult.BlankInput, coordinator.prepareAndEnqueue(request(" ")))
    assertEquals(BackgroundTurnPreparationResult.SessionUnavailable, deletedCoordinator.prepareAndEnqueue(request("问题")))
    assertTrue(queuedInputs.isEmpty())
}
~~~

- [ ] Run and expect failure:

~~~powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME; .\gradlew.bat :app:testDebugUnitTest --tests "*.SessionPolicyInputMapperTest" --tests "*.BackgroundTurnPreparationCoordinatorTest" --console=plain
~~~

- [ ] Add the mapper without mode inference:

~~~kotlin
internal fun NewSessionConfiguration?.toSessionPolicyInput(userText: String): SessionPolicyInput {
    val snapshot = this
    return SessionPolicyInput(
        mode = SessionModeWire.STUDY, userInput = userText,
        knowledgePoint = snapshot?.goal?.ifBlank { snapshot.title }.orEmpty(),
        settings = SessionStrategySettings(
            probingIntensity = snapshot?.probingIntensity ?: 3,
            correctionPersistence = when (snapshot?.correctionPersistence) {
                "宽松" -> CorrectionPersistenceWire.GENTLE
                "严格" -> CorrectionPersistenceWire.PERSISTENT
                else -> CorrectionPersistenceWire.BALANCED
            }
        )
    )
}
~~~

- [ ] Implement coordinator in strict order:

~~~kotlin
if (request.userText.isBlank()) return BlankInput
if (isSessionDeleted(request.sessionId)) return SessionUnavailable
val context = contextAssembler.assemble(request.spaceId, request.sessionId)
val policy = SessionTurnPolicy.normalize(request.sessionSnapshot.toSessionPolicyInput(request.userText))
val job = enqueueJob(BackgroundGenerationInput(
    spaceId = request.spaceId, sessionId = request.sessionId,
    userMessageId = request.userMessageId, userText = request.userText,
    token = LlmGenerationToken(request.token), quoteExcerpt = request.quoteExcerpt,
    imageAttachments = request.imageAttachments,
    contextEvidence = context.toLlmContextEvidence(),
    sessionPolicy = policy.toLlmSessionPolicyContext()
), nowEpochMillis())
return Queued(job.id, facade.mapQueued(/* policy, context, safe existing-message projection */))
~~~

toLlmContextEvidence may use only bounded messages, memory, sources, gaps, review points and historical errors; cap text with SessionTurnContracts.sanitizeContractText. Move the existing SessionPolicyOutput to LlmSessionPolicyContext mapper out of ChatGenerationPortAdapter into shared app wiring. Rethrow CancellationException; map other exceptions to Failed. Never call ChatGenerationRepository, Worker, or a message-write API.

- [ ] Rerun focused tests and commit:

~~~powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME; .\gradlew.bat :app:testDebugUnitTest --tests "*.SessionPolicyInputMapperTest" --tests "*.BackgroundTurnPreparationCoordinatorTest" --console=plain
git add -- mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/SessionPolicyInputMapper.kt mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/BackgroundTurnPreparationCoordinator.kt mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/ChatGenerationPortAdapter.kt mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/SessionPolicyInputMapperTest.kt mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/BackgroundTurnPreparationCoordinatorTest.kt
git commit -m "feat(app): prepare background session turns"
~~~

### Task 4: Wire and replace direct generation

- [ ] Write a JVM BackgroundTurnDispatchTest proving one sent message gives one port request and one Worker callback; a rejected result gives no callback and no direct-generation call.
- [ ] Add one port instance to HybridAppGraph and pass it through MainActivity and AppShell to ChatRoute.
- [ ] Preserve this sole Worker scheduler:

~~~kotlin
onBackgroundGenerationQueued = { jobId ->
    BackgroundGenerationWorker.enqueue(context, jobId)
}
~~~

- [ ] After ChatSendCoordinator returns Sent, retain reload/message lookup and use only:

~~~kotlin
when (val prepared = backgroundTurnPreparationPort.prepareAndEnqueue(request)) {
    is BackgroundTurnPreparationResult.Queued -> {
        activeGenerationToken = token
        generation = ChatGenerationUiState.Pending
        activeBackgroundJobId = prepared.jobId
        onBackgroundGenerationQueued(prepared.jobId)
    }
    BackgroundTurnPreparationResult.BlankInput,
    BackgroundTurnPreparationResult.SessionUnavailable,
    BackgroundTurnPreparationResult.Unavailable,
    BackgroundTurnPreparationResult.Failed -> generation = ChatGenerationUiState.Failed
}
~~~

Remove buildGenerationChatContextEvidence, direct BackgroundGenerationInput construction, and directGenerationCoordinator.generate. Preserve layout, polling, attachments and callback.

- [ ] Verify and commit:

~~~powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME; .\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.BackgroundTurnDispatchTest" :app:testDebugUnitTest --tests "*.BackgroundTurnPreparationCoordinatorTest" --console=plain
git add -- mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt mobile-native/app/src/main/java/com/reversetutor/preview/MainActivity.kt mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ChatScreen.kt mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/BackgroundTurnDispatchTest.kt
git commit -m "feat(chat): route turns through background preparation"
~~~

### Task 5: Acceptance

- [ ] After Track B is committed or isolated, run:

~~~powershell
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT=$env:ANDROID_HOME; .\gradlew.bat test :app:lint :app:assembleDebug --console=plain
~~~

Expected: BUILD SUCCESSFUL.

- [ ] From repository root run mocked Python regression:

~~~powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
~~~

- [ ] Confirm scope:

~~~powershell
git diff --check HEAD~4..HEAD
git diff --name-only HEAD~4..HEAD -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
git status --short --branch
~~~

Expected: no whitespace errors, frozen paths empty, Track B still unstaged.

- [ ] Optional device smoke only after automated green: send one message, verify one queued job and one assistant reply. After an instrumentation run, uninstall its test package and restore com.reversetutor.preview/.MainActivity foreground. Do not retry an install/test without a concrete repair.

- [ ] Update docs/contracts/native-frontend-handoff.md only if actual public port/result names differ. Do not push unless asked.

## Plan self-review

This plan covers the port, fixed study mapping, bounded context, policy snapshot, one job, Worker-only generation, rejection, app composition and route replacement. It changes neither frozen modules nor Track B files.
