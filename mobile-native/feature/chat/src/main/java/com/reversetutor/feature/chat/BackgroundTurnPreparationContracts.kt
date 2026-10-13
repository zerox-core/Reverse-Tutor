package com.reversetutor.feature.chat

import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.core.domain.TurnPlan

/**
 * Compose-free port for preparing a background turn after one user message is persisted.
 * The port assembles bounded context, produces a study-policy snapshot, enqueues exactly
 * one background generation job, and returns a queued contract projection. It never
 * calls ChatGenerationRepository, Worker, or any message-write API directly.
 */
fun interface BackgroundTurnPreparationPort {
    suspend fun prepareAndEnqueue(request: BackgroundTurnPreparationRequest): BackgroundTurnPreparationResult

    data object Unavailable : BackgroundTurnPreparationPort {
        override suspend fun prepareAndEnqueue(request: BackgroundTurnPreparationRequest) =
            BackgroundTurnPreparationResult.Unavailable
    }
}

/**
 * Input to background turn preparation. Only [MessageAttachment] is imported from core:model;
 * no Repository, DAO, Entity, Database, protocol DTO, or Compose type.
 */
data class BackgroundTurnPreparationRequest(
    val spaceId: String,
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val token: String,
    val quoteExcerpt: String?,
    val imageAttachments: List<MessageAttachment>,
    val sessionSnapshot: NewSessionConfiguration?,
    val turnPlan: TurnPlan? = null
)

/**
 * Result of background turn preparation.
 * - [Queued]: job enqueued, contract projected as LOADING.
 * - [BlankInput]: user text was blank.
 * - [SessionUnavailable]: session was deleted.
 * - [Unavailable]: port not wired (e.g. missing repository).
 * - [Failed]: unexpected error during preparation.
 */
sealed interface BackgroundTurnPreparationResult {
    data class Queued(val jobId: String, val contract: SessionConversationContract) : BackgroundTurnPreparationResult
    data object BlankInput : BackgroundTurnPreparationResult
    data object SessionUnavailable : BackgroundTurnPreparationResult
    data object Unavailable : BackgroundTurnPreparationResult
    data object Failed : BackgroundTurnPreparationResult
}

/**
 * 可选契约状态源接口。当协调器支持在回合完成后提供最新诊断与动作契约时实现本接口。
 */
fun interface SessionContractProvider {
    fun getLatestContract(sessionId: String): SessionConversationContract?
}

/**
 * 可选思考流读接口。LOADING 期间思考抽屉优先从实现本接口的端口读实时预览与独白
 * （反转家教协调器写自己的 GenerationPartialStore，与 DataModule 私有单例不同源），
 * 未实现时回退 backgroundGenerationRepository 读路径，行为完全不变。
 */
interface TurnMonologuePort {
    /** 实时思考流（老师侧独白），对应 repository.getGenerationMonologue。 */
    fun getMonologue(jobId: String, token: String): String?

    /** 实时回复预览（学生台词草稿），对应 repository.getGenerationPreview；默认不接管。 */
    fun getPreview(jobId: String, token: String): String? = null
}

