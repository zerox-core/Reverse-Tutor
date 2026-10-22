package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.domain.ChapterTransitionPolicy
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.GuidedLearningIntentClassifier
import com.reversetutor.core.domain.RecentTurnSignals
import com.reversetutor.core.domain.SessionTurnPolicy
import com.reversetutor.core.domain.TurnNoteAssembler
import com.reversetutor.core.domain.TurnNoteInput
import com.reversetutor.core.domain.UserIntent
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.feature.chat.BackgroundTurnPreparationPort
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.ConversationMessageContract
import com.reversetutor.feature.chat.SessionConversationFacade
import kotlinx.coroutines.CancellationException

/**
 * Implements [BackgroundTurnPreparationPort] for production wiring.
 *
 * Strict execution order:
 * 1. Blank check \u2192 [BackgroundTurnPreparationResult.BlankInput]
 * 2. Session deleted check \u2192 [BackgroundTurnPreparationResult.SessionUnavailable]
 * 3. Assemble bounded context via [assembleContext]
 * 4. Normalize study policy via [SessionTurnPolicy.normalize]
 * 5. Enqueue exactly one background job via [enqueueJob]
 * 6. Project queued contract via [SessionConversationFacade.mapQueued]
 *
 * Never calls ChatGenerationRepository, Worker, or any message-write API.
 * [CancellationException] is rethrown; all other exceptions map to [BackgroundTurnPreparationResult.Failed].
 */
internal class BackgroundTurnPreparationCoordinator(
    private val isSessionDeleted: suspend (String) -> Boolean,
    private val assembleContext: suspend (String, String, String) -> ConversationContextContract,
    /**
     * 1g 闲聊兼容：OffTopic 回合的轻量装配入口——只读最近消息，跳过 RAG
     * embedding / 图谱 / 掌握度 / 记忆端口与早史压缩。null = 调用方未接线
     * （旧测试构造 / 预览壳），OffTopic 回落全装配，行为不变。
     */
    private val assembleLightweightContext: (suspend (String, String) -> ConversationContextContract)? = null,
    private val enqueueJob: suspend (BackgroundGenerationInput, Long) -> BackgroundGenerationJob,
    private val loadRecentTurnSignals: suspend (String, String) -> RecentTurnSignals = { _, _ -> RecentTurnSignals() },
    private val loadLastTurnStyleHint: suspend (String) -> String = { "" },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val loadMessages: (String) -> List<ConversationMessageContract> = { emptyList() },
    /** R88：章节切换提案的会话级一次性暂存（默认 NoOp，测试/预览无感）。 */
    private val chapterTransitionStore: ChapterTransitionProposalStore = NoOpChapterTransitionProposalStore,
    /** R88：用户认可切换后，把章节卡片文本作为一条 System 消息写入时间线。 */
    private val insertTimelineNotice: suspend (String, String, String) -> Unit = { _, _, _ -> },
    private val facade: SessionConversationFacade = SessionConversationFacade()
) : BackgroundTurnPreparationPort {

    override suspend fun prepareAndEnqueue(
        request: BackgroundTurnPreparationRequest
    ): BackgroundTurnPreparationResult {
        try {
            // 尾部重申是发给模型的内部格式要求，不属于用户原话：失败重试会把
            // job.userText（含重申）原样送回来（feature/chat 的孤儿重试），
            // 这里先剥掉，保证意图分类/检索查询/策略与便签派生始终只见用户原话。
            val userText = request.userText.withoutMonologueTailRequirement()
            if (userText.isBlank() && request.imageAttachments.isEmpty()) {
                return BackgroundTurnPreparationResult.BlankInput
            }
            if (isSessionDeleted(request.sessionId)) {
                return BackgroundTurnPreparationResult.SessionUnavailable
            }

            // 1g 闲聊兼容（意图分流）：确定性分类器提前到装配之前——OffTopic
            // 闲聊不进重装配管线（RAG embedding 网络调用、图谱、掌握度、
            // 记忆与早史压缩全部跳过），只读最近消息，保证随意对话不被检索/
            // 记忆装配拖慢或污染。保守边界：带图片附件的回合一律重装配（图片
            // 需要完整证据面）；其余意图（含 GoalChange——换目标可能需要新主题
            // 检索）维持全装配；轻量入口未接线时 OffTopic 回落全装配。分类器
            // 与 toGuidedLearningTurnInput 内部同源（纯内存规则，双跑成本可
            // 忽略），判定标准唯一。
            val earlyIntent = GuidedLearningIntentClassifier.classify(userText)
            val lightweightAssemblyTurn = earlyIntent == UserIntent.OffTopic &&
                request.imageAttachments.isEmpty()

            val context = if (lightweightAssemblyTurn) {
                assembleLightweightContext?.invoke(request.spaceId, request.sessionId)
                    ?: assembleContext(request.spaceId, request.sessionId, userText)
            } else {
                assembleContext(request.spaceId, request.sessionId, userText)
            }
            val baseGuidedInput = request.sessionSnapshot.toGuidedLearningTurnInput(
                spaceId = request.spaceId,
                sessionId = request.sessionId,
                context = context,
                // userText is passed only so the mapper's deterministic intent
                // classifier can categorize this turn; the guided input stores
                // just the categorized enum, never the raw text.
                userText = userText
            )
            val recentSignals = try {
                loadRecentTurnSignals(request.sessionId, baseGuidedInput.conceptKey)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A read-only signal lookup can improve the next plan, but it
                // must never prevent a valid user turn from being queued.
                RecentTurnSignals()
            }
            val guidedInput = baseGuidedInput.copy(recentSignals = recentSignals).normalized()
            val turnPlan = request.turnPlan ?: guidedInput.selectGuidedLearningPlan()

            // R88 章节切换卡片（两段式）：上一轮 AI 学生以学生口吻提出切换时，
            // 提案已暂存在 store；本轮用户认可（纯文本确定性判定，不调模型）
            // 才把卡片写进时间线。提案一次性消费——卡片是仪式，不是状态机。
            // 卡片属装饰层：任何失败都不得阻断学习回合本身。
            try {
                val pendingTransition = chapterTransitionStore.load(request.sessionId)
                chapterTransitionStore.clear(request.sessionId)
                if (pendingTransition != null &&
                    ChapterTransitionPolicy.isTransitionAffirmation(userText)
                ) {
                    insertTimelineNotice(
                        request.spaceId,
                        request.sessionId,
                        ChapterTransitionPolicy.cardText(pendingTransition)
                    )
                }
                ChapterTransitionPolicy.proposalFor(turnPlan, guidedInput.learningPath)?.let {
                    chapterTransitionStore.save(request.sessionId, it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 装饰性卡片失败静默降级，绝不把学习回合打成 Failed。
            }
            val policy = SessionTurnPolicy.normalize(
                request.sessionSnapshot.toSessionPolicyInput(userText)
            )

            // Expression-loop slice 3: the previous turn's validator style
            // flags replay as a one-line correction hint (never blocks a turn).
            val lastTurnStyleHint = try {
                loadLastTurnStyleHint(request.sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ""
            }

            // Expression-loop slice 2: assemble the deterministic turn note.
            val turnNote = TurnNoteAssembler.assemble(
                TurnNoteInput(
                    userText = userText,
                    recentUserTexts = context.recentMessages
                        .asSequence()
                        .filter { it.role == "user" }
                        .map { it.text }
                        .toList()
                        .let { texts ->
                            // The just-sent message may already be persisted;
                            // pacing signals only look at earlier turns.
                            if (texts.firstOrNull() == userText.trim()) texts.drop(1) else texts
                        },
                    turnPlan = turnPlan,
                    knowledgePoint = policy.action.knowledgePoint,
                    masteryScore = context.masteryProjections
                        .firstOrNull { it.knowledgePoint == turnPlan.conceptKey }
                        ?.score,
                    lastStuckPoint = context.historicalErrors.firstOrNull()?.description.orEmpty(),
                    userEmotion = policy.evaluation.userEmotion,
                    styleHint = lastTurnStyleHint
                )
            ).normalized()

            // Expression-loop speed pass (2026-09-20 拍板): casual small talk
            // and goal changes don't need the learning evidence pack — the
            // template persona plus the two freshest messages keep these
            // prompts near ~1.5k chars so provider prefill stops dominating
            // casual-turn latency.
            val lightweightEvidenceTurn = guidedInput.userIntentHint == UserIntent.OffTopic ||
                guidedInput.userIntentHint == UserIntent.GoalChange

            val llmSessionPolicy = policy.toLlmSessionPolicyContext()
            val job = enqueueJob(
                BackgroundGenerationInput(
                    spaceId = request.spaceId,
                    sessionId = request.sessionId,
                    userMessageId = request.userMessageId,
                    // 契约块固定在提示词最前面（core/llm 的 cache-friendly 布局），
                    // 但实测模型经常忽略「最开头用 <thinking> 包内心独白」那一条：
                    // 同一条提示词直连网关，契约只说一次时独白遵从率 0/6-1/6，
                    // 把这条格式要求重申在提示词最尾部（用户话之后）后 6/6。
                    // 因此这里在发给模型的用户话尾部重申一次（只影响本次生成的
                    // 提示词，不改用户消息本身与其展示）。
                    userText = if (llmSessionPolicy == null) {
                        userText
                    } else {
                        userText.withMonologueTailRequirement()
                    },
                    token = LlmGenerationToken(request.token),
                    quoteExcerpt = request.quoteExcerpt,
                    imageAttachments = request.imageAttachments,
                    contextEvidence = listOfNotNull(request.sessionSnapshot.toLlmTemplateEvidence()) +
                        if (lightweightEvidenceTurn) {
                            context.toLlmLightweightContextEvidence()
                        } else {
                            context.toLlmContextEvidence()
                        },
                    sessionPolicy = llmSessionPolicy,
                    turnPlan = turnPlan,
                    turnNoteBlock = turnNote.render()
                ),
                nowEpochMillis()
            )

            return BackgroundTurnPreparationResult.Queued(
                jobId = job.id,
                contract = facade.mapQueued(
                    sessionId = request.sessionId,
                    turnId = request.userMessageId,
                    policy = policy,
                    context = context,
                    messages = loadMessages(request.sessionId)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return BackgroundTurnPreparationResult.Failed
        }
    }
}

/**
 * 独白格式的尾部重申。
 *
 * 背景：学生表达契约（含「回复最开头先用 <thinking>…</thinking> 包住内心独白」）
 * 由 core/llm 固定放在单条 user 消息的最前面；证据块、决策块与便签跟在后面，
 * 提示词长度约 1.4k-2k 字符。实测（真机同一条提示词直连网关，同一模型）
 * 契约只在开头说一次时，模型是否吐 <thinking> 信封极不稳定：某轮提示词
 * 0/6-1/6、另一轮 5/6-6/6；而把这条格式要求重申在提示词最尾部（用户话之后）
 * 后，原本 0/6 的提示词变成 6/6。
 *
 * 这条重申不改契约语义、不新增模型可见的其它信息，只把同一条格式要求放到
 * 模型最后读到的位置；RT（算法内核）线不经过本协调器，行为不变。
 */
internal const val MONOLOGUE_TAIL_REQUIREMENT: String =
    "格式要求（最高优先级，先做这一步）：这条回复的第一个字符必须是 <thinking>，" +
        "用两三句中文写下你此刻的第一人称内心独白，再以 </thinking> 结束，然后空一行写正文。"

/** 把 [MONOLOGUE_TAIL_REQUIREMENT] 追加到提示词尾部；幂等，不改用户消息本身。 */
internal fun String.withMonologueTailRequirement(): String =
    if (isBlank() || contains(MONOLOGUE_TAIL_REQUIREMENT)) {
        this
    } else {
        trimEnd() + "\n\n" + MONOLOGUE_TAIL_REQUIREMENT
    }

/**
 * 还原用户原话：失败重试会把 `job.userText`（含尾部重申）当作请求文本送回来，
 * 派生路径（意图分类、检索查询、策略、便签、章节切换判定）必须先剥掉重申。
 */
internal fun String.withoutMonologueTailRequirement(): String {
    val index = indexOf(MONOLOGUE_TAIL_REQUIREMENT)
    if (index < 0) return this
    return substring(0, index).trimEnd()
}
