package com.reversetutor.core.domain

/**
 * Pure study/goal/companion turn decision policy migrated from
 * old `main:engine.py` `_normalize_turn_payload`.
 *
 * This object is pure Kotlin: it never calls a Repository, Android API,
 * LLM, or clock. It takes a [SessionPolicyInput] (raw proposal fields +
 * read-only mastery/error snapshot) and produces a [SessionPolicyOutput]
 * with normalized evaluation, action, and process summary.
 *
 * Rule application order mirrors engine.py:
 * 1. Merge defaults + normalize settings + normalize mode
 * 2. First-turn constraints (if [SessionPolicyInput.isFirstTurn])
 * 3. Derive entry status from user input keywords + mastery
 * 4. Detect understood claim
 * 5. Mode whitelist + fallback
 * 6. Study-specific rewrites (understood → examiner_verify, force_probe,
 *    no_entry → clue, has_entry → probe, high intensity, low correctness,
 *    summary_only correction)
 * 7. Derive student role from final action
 * 8. Normalize evidence (non-study → none)
 * 9. Clamp numeric fields
 * 10. Build safe process summary
 */
object SessionTurnPolicy {

    // ---- Keyword sets (mirrors engine.py) ----

    private val UNDERSTOOD_KEYWORDS = listOf("懂了", "明白了", "会了", "理解了", "知道了")

    private val GOAL_DONE_KEYWORDS = listOf("完成", "改完", "做完", "交付")
    private val GOAL_STUCK_KEYWORDS = listOf("卡住", "不会推进", "阻塞", "没法")

    private val COMPANION_NEGATIVE_KEYWORDS = listOf("烦", "难受", "累", "不想", "焦虑", "崩")

    private val RECALL_DECAY_KEYWORDS = listOf("忘了", "记不清", "又不会", "又不懂")
    private val NO_ENTRY_KEYWORDS = listOf("不知道", "没听过", "没学过", "不会", "这是什么", "完全没")

    private val EXECUTABLE_STEP_MARKERS = listOf(
        "先", "然后", "再", "代入", "求", "看", "找", "判断", "比较", "画", "设", "解", "算", "推导", "证明"
    )

    private const val MASTERY_THRESHOLD = 0.1f
    private const val LOW_CORRECTNESS_THRESHOLD = 0.5f

    private val CORRECTION_CAPABLE_ACTIONS = setOf(
        ActionTypeWire.ASK, ActionTypeWire.PROBE, ActionTypeWire.CHALLENGE,
        ActionTypeWire.CLUE, ActionTypeWire.SCAFFOLD_EXAMPLE,
        ActionTypeWire.SMALL_LECTURE, ActionTypeWire.EXAMINER_VERIFY
    )

    private val NO_ENTRY_REWRITE_ACTIONS = setOf(
        ActionTypeWire.ASK, ActionTypeWire.PROBE, ActionTypeWire.NEXT, ActionTypeWire.RECAP
    )

    private val HAS_ENTRY_REWRITE_ACTIONS = setOf(
        ActionTypeWire.CLUE, ActionTypeWire.SCAFFOLD_EXAMPLE
    )

    // ---- Public entry point ----

    fun normalize(input: SessionPolicyInput): SessionPolicyOutput {
        val warnings = mutableListOf<String>()

        // 1. Normalize mode + settings
        val mode = SessionTurnContracts.normalizeMode(input.mode)
        if (mode != input.mode.trim().lowercase()) {
            warnings.add("mode normalized to '$mode'")
        }

        val settings = SessionStrategySettings(
            probingIntensity = SessionTurnContracts.normalizeProbingIntensity(input.settings.probingIntensity),
            correctionTiming = SessionTurnContracts.normalizeCorrectionTiming(input.settings.correctionTiming),
            correctionPersistence = SessionTurnContracts.normalizeCorrectionPersistence(input.settings.correctionPersistence)
        )

        // 2. First-turn constraints
        if (input.isFirstTurn) {
            return firstTurnOutput(mode, input, settings, warnings)
        }

        // 3. Derive entry status
        val rawEntryStatus = SessionTurnContracts.normalizeEntryStatus(input.entryStatus)
        val entryStatus = deriveEntryStatus(input, mode, rawEntryStatus)
        if (entryStatus != rawEntryStatus) {
            warnings.add("entryStatus derived to '$entryStatus'")
        }

        // 4. Detect understood claim
        val understood = isUnderstoodClaim(input.userInput)

        // 5. Start with proposed action (trimmed, lowercase)
        var actionType = input.actionType.trim().lowercase()
        val allowed = SessionTurnContracts.actionWhitelistForMode(mode)

        // 5a. Non-study: if not in allowed → fallback
        if (mode != SessionModeWire.STUDY && actionType !in allowed) {
            actionType = fallbackActionForMode(mode, input.userInput, understood)
            warnings.add("action fallback for mode '$mode' to '$actionType'")
        }

        // 5b. Goal + understood → verify_done
        if (mode == SessionModeWire.GOAL && understood) {
            actionType = ActionTypeWire.VERIFY_DONE
        }

        // 5c. Understood claim (study mode) → examiner_verify
        if (mode == SessionModeWire.STUDY && understood) {
            actionType = ActionTypeWire.EXAMINER_VERIFY
        }

        // 5d. Force probe → probe
        if (input.forceProbe) {
            actionType = ActionTypeWire.PROBE
            warnings.add("action overridden by forceProbe to 'probe'")
        }

        // 5e. no_entry + ask/probe/next/recap → clue
        if (entryStatus == EntryStatusWire.NO_ENTRY && actionType in NO_ENTRY_REWRITE_ACTIONS) {
            actionType = ActionTypeWire.CLUE
        }

        // 5f. has_entry + clue/scaffold_example → probe
        if (entryStatus == EntryStatusWire.HAS_ENTRY && actionType in HAS_ENTRY_REWRITE_ACTIONS) {
            actionType = ActionTypeWire.PROBE
        }

        // 5g. If still not in allowed → fallback
        if (actionType !in allowed) {
            actionType = fallbackActionForMode(mode, input.userInput, understood)
            warnings.add("action not in whitelist, fallback to '$actionType'")
        }

        // 6. Not-understood secondary rewrites (study mode only)
        if (mode == SessionModeWire.STUDY && !understood) {
            // 6a. High probing intensity + has_entry + ask → probe
            if (settings.probingIntensity >= 5 && entryStatus == EntryStatusWire.HAS_ENTRY && actionType == ActionTypeWire.ASK) {
                actionType = ActionTypeWire.PROBE
            }
            // 6b. Low intensity + ask/probe + has_entry + active error + low correctness → small_lecture
            if (settings.probingIntensity < 5 && actionType in setOf(ActionTypeWire.ASK, ActionTypeWire.PROBE) &&
                entryStatus == EntryStatusWire.HAS_ENTRY && input.hasActiveError &&
                SessionTurnContracts.clamp01(input.correctness) < LOW_CORRECTNESS_THRESHOLD
            ) {
                actionType = ActionTypeWire.SMALL_LECTURE
            }
            // 6c. correction_timing == summary_only + evidence.type == correction → recap
            if (settings.correctionTiming == CorrectionTimingWire.SUMMARY_ONLY &&
                SessionTurnContracts.normalizeEvidenceType(input.evidenceType) == MasteryEvidenceTypeWire.CORRECTION &&
                actionType in CORRECTION_CAPABLE_ACTIONS
            ) {
                actionType = ActionTypeWire.RECAP
            }
        }

        // 7. Derive student role
        val proposedRole = SessionTurnContracts.normalizeStudentRole(input.actionStudentRole)
        val studentRole = if (actionType != input.actionType.trim().lowercase() || proposedRole !in StudentRoleWire.ALL) {
            SessionTurnContracts.studentRoleForAction(actionType)
        } else {
            proposedRole
        }

        // 8. Normalize evidence (non-study → none)
        val evidenceType: String
        val evidenceStatus: String
        val evidenceReason: String
        if (mode != SessionModeWire.STUDY) {
            evidenceType = MasteryEvidenceTypeWire.NONE
            evidenceStatus = MasteryEvidenceStatusWire.NONE
            evidenceReason = "$mode mode does not update mastery"
        } else {
            val requestedEvidenceType = SessionTurnContracts.normalizeEvidenceType(input.evidenceType)
            val requestedEvidenceStatus = SessionTurnContracts.normalizeEvidenceStatus(input.evidenceStatus)
            if (
                requestedEvidenceType == MasteryEvidenceTypeWire.NONE &&
                actionType == ActionTypeWire.PROBE &&
                SessionTurnContracts.clamp01(input.correctness) >= 0.35f &&
                SessionTurnContracts.clamp01(input.depth) >= 0.35f
            ) {
                evidenceType = MasteryEvidenceTypeWire.EXPLANATION
                evidenceStatus = if (SessionTurnContracts.clamp01(input.correctness) >= 0.45f) {
                    MasteryEvidenceStatusWire.PASSED
                } else {
                    MasteryEvidenceStatusWire.PARTIAL
                }
                evidenceReason = SessionTurnContracts.sanitizeContractText(
                    input.evidenceReason.ifBlank { "用户给出了可追问的解释" }
                )
            } else {
                evidenceType = requestedEvidenceType
                evidenceStatus = requestedEvidenceStatus
                evidenceReason = SessionTurnContracts.sanitizeContractText(input.evidenceReason)
            }
        }

        // 9. Clamp + bound remaining fields
        val correctness = SessionTurnContracts.clamp01(input.correctness)
        val depth = SessionTurnContracts.clamp01(input.depth)
        val difficulty = SessionTurnContracts.normalizeDifficulty(input.actionDifficulty)
        val knowledgePoint = SessionTurnContracts.normalizeKnowledgePoint(input.knowledgePoint)
        val note = SessionTurnContracts.normalizeNote(input.actionNote)
        val errorPattern = SessionTurnContracts.normalizeErrorPattern(input.errorPattern)
        val misconception = SessionTurnContracts.normalizeMisconception(input.misconception)
        val userEmotion = SessionTurnContracts.normalizeUserEmotion(input.userEmotion)

        // 10. Build contracts
        val evaluation = SessionEvaluationContract(
            correctness = correctness,
            depth = depth,
            entryStatus = entryStatus,
            evidence = MasteryEvidenceContract(
                type = evidenceType,
                status = evidenceStatus,
                errorType = SessionTurnContracts.sanitizeContractText(input.evidenceErrorType, maxLength = 80),
                reason = evidenceReason
            ),
            errorPattern = errorPattern,
            misconception = misconception,
            userEmotion = userEmotion,
            newRequirements = SessionTurnContracts.normalizeRequirements(input.newRequirements)
        )

        val action = SessionActionContract(
            type = actionType,
            studentRole = studentRole,
            knowledgePoint = knowledgePoint,
            difficulty = difficulty,
            note = note
        )

        val summary = buildProcessSummary(mode, actionType, knowledgePoint, entryStatus, understood)

        return SessionPolicyOutput(
            evaluation = evaluation,
            action = action,
            processSummary = summary,
            normalizationWarnings = warnings
        )
    }

    // ---- First-turn constraints ----

    private fun firstTurnOutput(
        mode: String,
        input: SessionPolicyInput,
        settings: SessionStrategySettings,
        warnings: MutableList<String>
    ): SessionPolicyOutput {
        warnings.add("first-turn constraint applied")

        val (actionType, studentRole) = when (mode) {
            SessionModeWire.GOAL -> ActionTypeWire.DECOMPOSE to StudentRoleWire.GOAL_PARTNER
            SessionModeWire.COMPANION -> ActionTypeWire.OBSERVE to StudentRoleWire.COMPANION
            else -> ActionTypeWire.ASK to StudentRoleWire.PROBING_STUDENT
        }

        val knowledgePoint = SessionTurnContracts.normalizeKnowledgePoint(input.knowledgePoint)
        val evaluation = SessionEvaluationContract(
            correctness = 0f,
            depth = 0f,
            entryStatus = if (mode == SessionModeWire.STUDY) EntryStatusWire.HAS_ENTRY else SessionTurnContracts.normalizeEntryStatus(input.entryStatus),
            evidence = MasteryEvidenceContract(
                type = if (mode == SessionModeWire.STUDY) MasteryEvidenceTypeWire.NONE else MasteryEvidenceTypeWire.NONE,
                status = MasteryEvidenceStatusWire.NONE,
                errorType = "",
                reason = if (mode != SessionModeWire.STUDY) "$mode mode does not update mastery" else ""
            ),
            errorPattern = "",
            misconception = "",
            userEmotion = SessionTurnContracts.normalizeUserEmotion(input.userEmotion),
            newRequirements = emptyList()
        )

        val action = SessionActionContract(
            type = actionType,
            studentRole = studentRole,
            knowledgePoint = knowledgePoint,
            difficulty = 0.5f,
            note = SessionTurnContracts.DEFAULT_NOTE
        )

        val summary = buildProcessSummary(mode, actionType, knowledgePoint, evaluation.entryStatus, false)

        return SessionPolicyOutput(
            evaluation = evaluation,
            action = action,
            processSummary = summary,
            normalizationWarnings = warnings
        )
    }

    // ---- Entry status derivation (mirrors _method_entry_from_user) ----

    private fun deriveEntryStatus(
        input: SessionPolicyInput,
        mode: String,
        rawValue: String
    ): String {
        // If raw value is already valid, start from it
        var status = rawValue

        // Keyword overrides from current user input
        val userInput = input.userInput
        if (RECALL_DECAY_KEYWORDS.any { it in userInput }) {
            status = EntryStatusWire.RECALL_DECAY
        } else if (NO_ENTRY_KEYWORDS.any { it in userInput }) {
            status = EntryStatusWire.NO_ENTRY
        } else if (input.topMasteryLevel >= MASTERY_THRESHOLD) {
            status = EntryStatusWire.HAS_ENTRY
        }

        // If no_entry but has matching mastery and recent user inputs contain executable steps → has_entry
        if (status == EntryStatusWire.NO_ENTRY && input.hasMatchingMastery) {
            val recentHasSteps = input.recentUserInputs.any { recent ->
                EXECUTABLE_STEP_MARKERS.any { it in recent }
            }
            if (recentHasSteps) {
                status = EntryStatusWire.HAS_ENTRY
            }
        }

        // Force probe → has_entry
        if (input.forceProbe) {
            status = EntryStatusWire.HAS_ENTRY
        }

        return status
    }

    // ---- Understood claim detection (mirrors _is_understood_claim) ----

    private fun isUnderstoodClaim(userInput: String): Boolean =
        UNDERSTOOD_KEYWORDS.any { it in userInput }

    // ---- Fallback action for mode (mirrors _fallback_action_for_mode) ----

    private fun fallbackActionForMode(mode: String, userInput: String, understood: Boolean): String =
        when (mode) {
            SessionModeWire.GOAL -> {
                when {
                    understood || GOAL_DONE_KEYWORDS.any { it in userInput } -> ActionTypeWire.VERIFY_DONE
                    GOAL_STUCK_KEYWORDS.any { it in userInput } -> ActionTypeWire.UNBLOCK
                    else -> ActionTypeWire.ADVANCE
                }
            }
            SessionModeWire.COMPANION -> {
                if (COMPANION_NEGATIVE_KEYWORDS.any { it in userInput }) ActionTypeWire.EMPATHIZE
                else ActionTypeWire.OBSERVE
            }
            else -> ActionTypeWire.ASK
        }

    // ---- Process summary (mirrors _build_process_summary) ----

    private fun buildProcessSummary(
        mode: String,
        actionType: String,
        knowledgePoint: String,
        entryStatus: String,
        understood: Boolean
    ): String {
        val kp = if (knowledgePoint.isBlank()) SessionTurnContracts.DEFAULT_KNOWLEDGE_POINT else knowledgePoint

        return when (mode) {
            SessionModeWire.GOAL -> when (actionType) {
                ActionTypeWire.VERIFY_DONE -> "目标验证：$kp"
                ActionTypeWire.UNBLOCK -> "目标阻塞排查：$kp"
                ActionTypeWire.DECOMPOSE -> "目标分解：$kp"
                else -> "目标推进：$kp"
            }
            SessionModeWire.COMPANION -> when (actionType) {
                ActionTypeWire.EMPATHIZE -> "情绪关注"
                ActionTypeWire.SOFT_GUIDE -> "柔性引导"
                else -> "陪伴观察"
            }
            else -> when (actionType) {
                ActionTypeWire.ASK -> "提问引导：$kp"
                ActionTypeWire.PROBE -> "探测理解：$kp"
                ActionTypeWire.CHALLENGE -> "挑战辨析：$kp"
                ActionTypeWire.CLUE -> "线索提示：$kp"
                ActionTypeWire.SCAFFOLD_EXAMPLE -> "脚手架示例：$kp"
                ActionTypeWire.SMALL_LECTURE -> "微课讲解：$kp"
                ActionTypeWire.EXAMINER_VERIFY -> "考核验证：$kp"
                ActionTypeWire.RECAP -> "回顾总结：$kp"
                ActionTypeWire.NEXT -> "进入下一步：$kp"
                ActionTypeWire.EMOTE -> "情感回应"
                ActionTypeWire.PERSUADE -> "引导说服：$kp"
                else -> "学习推进：$kp"
            }
        }
    }
}
