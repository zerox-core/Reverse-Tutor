package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.learning.LearningLedgerRepository
import com.reversetutor.core.data.learning.LearningRepositoryImpl
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.domain.ConversationContextAssembler
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.domain.ContextWarning
import com.reversetutor.core.domain.MessageContextPort
import com.reversetutor.core.domain.SessionTurnContracts
import com.reversetutor.core.domain.ConversationRunRepository
import com.reversetutor.core.domain.ConversationSessionCoordinator
import com.reversetutor.core.domain.LearningOverviewCoordinator
import com.reversetutor.core.domain.LearningOverviewScope
import com.reversetutor.core.domain.SessionPolicyInput
import com.reversetutor.core.domain.WindowMemoryContextPort
import com.reversetutor.feature.chat.ConversationMessageContract
import com.reversetutor.feature.chat.SessionConversationContract
import com.reversetutor.feature.chat.SessionConversationFacade
import com.reversetutor.core.domain.LearningFactReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import com.reversetutor.core.domain.LearningOverviewContract

/**
 * Compose-free application wiring entry point for the session conversation and
 * home learning overview capabilities.
 *
 * This is the single production composition root that binds the non-frozen
 * domain ports to existing frozen repositories:
 *
 * ```
 * SessionConversationFacade
 *   -> ConversationSessionCoordinator
 *        -> ConversationContextAssembler -> context port adapters -> Repositories
 *        -> ChatGenerationPortAdapter   -> ChatGenerationRepository
 *        -> SessionTurnPersistencePortAdapter -> MessageRepository / run repo
 * ```
 *
 * The UI layer may ONLY consume [SessionConversationContract] and
 * [LearningOverviewContract] returned from here. It must not reach DAOs,
 * Entities, `ReverseTutorDatabase`, `SecretStore`, or protocol DTOs.
 *
 * This class imports no Compose type. Its outputs are the immutable
 * feature-chat contract and the immutable domain overview contract.
 */
class SessionConversationAssembly(
    private val chatGenerationRepository: ChatGenerationRepository,
    private val messageRepository: MessageRepository,
    private val conversationRunRepository: ConversationRunRepository,
    private val sessionRepository: SessionRepository,
    private val memoryRepository: MemoryRepository,
    private val graphRepository: GraphRepository,
    private val sourceRepository: SourceRepository,
    private val learningRepository: LearningRepositoryImpl,
    private val learningLedgerRepository: LearningLedgerRepository? = null,
    private val messageContextPort: MessageContextPort = MessageContextPortAdapter(messageRepository),
    private val sessionSummaryStore: SessionSummaryStore? = null,
    private val windowIntakeDispatcher: WindowIntakeDispatcher? = null,
    private val windowMemoryContextPort: WindowMemoryContextPort? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {

    private val persistencePort: SessionTurnPersistencePortAdapter =
        SessionTurnPersistencePortAdapter(
            saveUserMessage = { sessionId, text, now, messageId ->
                messageRepository.sendUserMessage(sessionId, text, now, messageId) != null
            },
            checkSessionDeleted = { sessionId -> conversationRunRepository.isSessionDeleted(sessionId) },
            checkTokenCurrent = { true },
            checkTurnCompleted = { turnId ->
                conversationRunRepository.findLatestRun(turnId)?.isTerminal ?: false
            }
        )

    private val generationPort: ChatGenerationPortAdapter =
        ChatGenerationPortAdapter(
            generate = { input, now, isTokenCurrent, canPersistResult ->
                chatGenerationRepository.generateReply(input, now, isTokenCurrent, canPersistResult)
            },
            readAssistantText = { sessionId, assistantMessageId ->
                messageRepository.listMessages(sessionId)
                    .firstOrNull { it.id == assistantMessageId }
                    ?.text ?: ""
            }
        )

    /**
     * NEWMP-V1-017: early-history summarizer. Old-parity trigger — the legacy
     * engine compressed early dialogue at the start of every turn; the native
     * production path funnels each turn's context preparation through
     * [assembleContext], so the trigger lives on that single choke point.
     */
    private val sessionSummarizer: SessionSummarizer? = sessionSummaryStore?.let { store ->
        SessionSummarizer(
            generateSummary = chatGenerationRepository::generateSessionSummary,
            listMessages = messageRepository::listMessages,
            store = store
        )
    }

    /** 1g 轻量装配口径：与重装配的 messageLimit/textCap 保持一致。 */
    private val lightweightMessageLimit = 30
    private val lightweightTextCap = 200

    private val contextAssembler: ConversationContextAssembler = ConversationContextAssembler(
        messagePort = messageContextPort,
        memoryPort = MemoryContextPortAdapter(memoryRepository),
        errorPort = ErrorContextPortAdapter(memoryRepository),
        graphPort = GraphContextPortAdapter(
            graphRepository,
            learningLedgerRepository,
            nowEpochMillis
        ),
        sourcePort = SourceContextPortAdapter(
            sourceRepository,
            // NEWMP-V1-024: query embedding for semantic source ranking.
            chatGenerationRepository::embedQueryText
        ),
        masteryFactPort = learningLedgerRepository?.let { MasteryFactContextPortAdapter(it) },
        digestPort = sessionSummaryStore,
        windowMemoryPort = windowMemoryContextPort
    )

    private val coordinator: ConversationSessionCoordinator = ConversationSessionCoordinator(
        contextAssembler = contextAssembler,
        generationPort = generationPort,
        persistencePort = persistencePort,
        nowEpochMillis = nowEpochMillis
    )

    /**
     * Step-2 面板真数据接缝：学习台账读口，供进度/本周主线适配器做确定性聚合。
     * 未注入台账仓库时为 null，适配器保持诚实的空/默认契约。
     */
    private val listLearningFactsForOverview: (suspend (String) -> List<LearningFactReceipt>)? =
        learningLedgerRepository?.let { repo -> { spaceId -> repo.listLearningFacts(spaceId) } }

    /** V1 方向三「每日总结」：后台懒生成状态与生成器（绝不阻塞聊天轮次）。 */
    private val dailySummaryGenerationState = DailySummaryGenerationState()

    private val dailySummaryGenerationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dailySummaryGenerator: DailySummaryGenerator? = learningLedgerRepository?.let { ledger ->
        DailySummaryGenerator(
            generateSummary = chatGenerationRepository::generateSessionSummary,
            findStored = { spaceId, dayStart ->
                learningRepository.findLatestDailySummary(
                    spaceId,
                    dayStart,
                    DailySummaryGenerator.GeneratorVersion
                )
            },
            saveStored = { summary -> learningRepository.saveDailySummary(summary) },
            listLearningFacts = ledger::listLearningFacts,
            listPlanTasks = learningRepository::listTasks,
            listTokenUsage = learningRepository::listTokenUsage,
            pickSessionId = { spaceId ->
                sessionRepository.listSessions(spaceId).firstOrNull { !it.archived }?.id
            },
            state = dailySummaryGenerationState,
            generationScope = dailySummaryGenerationScope,
            nowEpochMillis = nowEpochMillis
        )
    }

    private val overviewCoordinator: LearningOverviewCoordinator = LearningOverviewCoordinator(
        sessionPort = LearningOverviewSessionPortAdapter(sessionRepository),
        progressPort = LearningOverviewProgressPortAdapter(
            learningRepository,
            nowEpochMillis,
            listLearningFacts = listLearningFactsForOverview
        ),
        planPort = LearningOverviewPlanPortAdapter(learningRepository),
        threadPort = LearningOverviewThreadPortAdapter(
            learningRepository,
            listLearningFacts = listLearningFactsForOverview,
            nowEpochMillis = nowEpochMillis
        ),
        weakPointPort = LearningOverviewWeakPointPortAdapter(memoryRepository),
        dailySummaryPort = LearningOverviewDailySummaryPortAdapter(
            learningRepository,
            listLearningFacts = listLearningFactsForOverview,
            generationState = dailySummaryGenerationState,
            nowEpochMillis = nowEpochMillis
        ),
        tokenPort = LearningOverviewTokenPortAdapter(
            listTokenUsage = learningRepository::listTokenUsage,
            sessionIdForTurn = { turnId ->
                conversationRunRepository.findLatestRun(turnId)?.sessionId
            },
            nowEpochMillis = nowEpochMillis
        ),
        nowEpochMillis = nowEpochMillis
    )

    private val facade: SessionConversationFacade = SessionConversationFacade()

    /**
     * Assemble bounded conversation context for the given session.
     * Delegates to the private [ConversationContextAssembler] without
     * running a generation turn.
     */
    suspend fun assembleContext(
        spaceId: String,
        sessionId: String,
        queryText: String = ""
    ): ConversationContextContract {
        // NEWMP-V1-017: compress early history first so this very turn's
        // assembled contract — and the evidence built from it — already
        // carries the fresh digest. Failures are swallowed inside and
        // retried on the next turn (old-parity behavior).
        sessionSummarizer?.maybeSummarize(sessionId)
        return contextAssembler.assemble(spaceId, sessionId, queryText)
    }

    /**
     * 1g 闲聊兼容：轻量上下文装配——只读最近消息，口径与重装配
     * （[ConversationContextAssembler] 的 messageLimit/textCap）一致；
     * RAG 语义检索（embedding 网络调用）、图谱缺口/复习点、掌握度、
     * 记忆与错误引用、窗口记忆注入全部跳过；早史压缩（LLM 调用）也不在
     * 闲聊回合触发——下一轮重装配会按旧口径自然补跑。失败安全口径同
     * 重装配：消息读失败 => 空列表 + warning，绝不让回合失败。
     */
    suspend fun assembleLightweightContext(
        spaceId: String,
        sessionId: String
    ): ConversationContextContract {
        val warnings = mutableListOf<ContextWarning>()
        val messages = try {
            messageContextPort.listRecentMessages(spaceId, sessionId, lightweightMessageLimit)
                .sortedWith(compareByDescending<ContextMessage> { it.timestampEpochMillis }
                    .thenBy { it.messageId })
                .map { it.copy(text = SessionTurnContracts.sanitizeContractText(it.text, lightweightTextCap)) }
        } catch (_: Exception) {
            warnings += ContextWarning("message", "source_unavailable")
            emptyList()
        }
        return ConversationContextContract(
            spaceId = spaceId,
            sessionId = sessionId,
            prerequisiteGaps = emptyList(),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = emptyList(),
            recentMessages = messages,
            warnings = warnings
        )
    }

    /**
     * Run a single conversation turn and return the immutable
     * [SessionConversationContract] for the UI. Pure wiring: no Compose type
     * is produced or referenced.
     */
    suspend fun runTurn(
        spaceId: String,
        sessionId: String,
        turnId: String,
        userMessageId: String,
        userText: String,
        token: String,
        policyInput: SessionPolicyInput
    ): SessionConversationContract {
        val result = coordinator.executeTurn(
            spaceId = spaceId,
            sessionId = sessionId,
            turnId = turnId,
            userMessageId = userMessageId,
            userText = userText,
            token = token,
            policyInput = policyInput
        )
        // V2-004 / decision #9: window-memory intake runs after the turn,
        // asynchronously, and can never block or fail the chat loop.
        windowIntakeDispatcher?.dispatch(sessionId)
        val messages = messageRepository.listMessages(sessionId).map {
            ConversationMessageContract(it.id, it.role.name.lowercase(), it.text, it.createdAtEpochMillis)
        }
        return facade.mapResult(result, sessionId, turnId, messages)
    }

    /**
     * Build the home learning overview read model for the given scope.
     */
    suspend fun overview(scope: LearningOverviewScope): LearningOverviewContract =
        overviewCoordinator.generate(scope)

    /** 每日总结懒生成入口：空数据日不烧额度，已有有效总结不重复生成。 */
    suspend fun requestDailySummaryGeneration(scope: LearningOverviewScope) {
        dailySummaryGenerator?.requestGeneration(scope.spaceId)
    }

    /** 后台生成结束事件（成功或失败），面板据此静默刷新。 */
    val dailySummaryUpdates: SharedFlow<Unit>
        get() = dailySummaryGenerationState.updates
}
