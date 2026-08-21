# Session Algorithm Parity Matrix

> Branch: `newmp`
> Old source: `main:engine.py` (`_normalize_turn_payload`, `_method_entry_from_user`, `_is_understood_claim`, `_student_role_for_action`, `_fallback_action_for_mode`, `_build_process_summary`)
> Native implementation: `mobile-native/core/domain`, `mobile-native/feature/chat`

## 1. Policy Normalization Rules

| # | Old Source Expression | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 1 | `normalize_mode(payload)` | `SessionTurnContracts.normalizeMode` | `invalidModeFallsBackToStudy` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidModeFallsBackToStudy"` |
| 2 | mode preservation | `SessionTurnContracts.normalizeMode` | `validModesArePreserved` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.validModesArePreserved"` |
| 3 | `normalize_entry_status` | `SessionTurnContracts.normalizeEntryStatus` | `invalidEntryStatusFallsBackToHasEntry` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidEntryStatusFallsBackToHasEntry"` |
| 4 | entry status preservation | `SessionTurnContracts.normalizeEntryStatus` | `validEntryStatusesArePreserved` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.validEntryStatusesArePreserved"` |
| 5 | `normalize_user_emotion` | `SessionTurnContracts.normalizeUserEmotion` | `invalidUserEmotionFallsBackToNeutral` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidUserEmotionFallsBackToNeutral"` |
| 6 | emotion preservation | `SessionTurnContracts.normalizeUserEmotion` | `validUserEmotionsArePreserved` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.validUserEmotionsArePreserved"` |
| 7 | `normalize_student_role` | `SessionTurnContracts.normalizeStudentRole` | `invalidStudentRoleFallsBackToProbingStudent` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidStudentRoleFallsBackToProbingStudent"` |
| 8 | `normalize_evidence_type` | `SessionTurnContracts.normalizeEvidenceType` | `invalidEvidenceTypeFallsBackToNone` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidEvidenceTypeFallsBackToNone"` |
| 9 | evidence type preservation | `SessionTurnContracts.normalizeEvidenceType` | `validEvidenceTypesArePreserved` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.validEvidenceTypesArePreserved"` |
| 10 | `normalize_evidence_status` | `SessionTurnContracts.normalizeEvidenceStatus` | `invalidEvidenceStatusFallsBackToNone` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidEvidenceStatusFallsBackToNone"` |
| 11 | `clamp(value, 0, 1)` correctness/depth | `SessionTurnContracts.clamp01` | `correctnessAndDepthAreClampedToZeroOne` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.correctnessAndDepthAreClampedToZeroOne"` |
| 12 | `knowledge_point = kp or "当前方法"` | `SessionTurnContracts.normalizeKnowledgePoint` | `blankKnowledgePointFallsBackToDefault` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.blankKnowledgePointFallsBackToDefault"` |
| 13 | `note = note or "normalized"` | `SessionTurnContracts.normalizeNote` | `blankNoteFallsBackToDefault` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.blankNoteFallsBackToDefault"` |
| 14 | `error_pattern[:12]` | `SessionTurnContracts.normalizeErrorPattern` | `errorPatternIsTruncatedToMax` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.errorPatternIsTruncatedToMax"` |
| 15 | `misconception[:20]` | `SessionTurnContracts.normalizeMisconception` | `misconceptionIsTruncatedToMax` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.misconceptionIsTruncatedToMax"` |
| 16 | null error/misconception → "" | `SessionTurnContracts.normalizeErrorPattern/Misconception` | `nullErrorPatternAndMisconceptionBecomeEmpty` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.nullErrorPatternAndMisconceptionBecomeEmpty"` |
| 17 | `probing_intensity` clamp 1-5 | `SessionTurnContracts.normalizeProbingIntensity` | `probingIntensityIsClampedToOneToFive` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.probingIntensityIsClampedToOneToFive"` |
| 18 | `correction_timing` normalize | `SessionTurnContracts.normalizeCorrectionTiming` | `invalidCorrectionTimingFallsBackToImmediate` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidCorrectionTimingFallsBackToImmediate"` |
| 19 | `correction_persistence` normalize | `SessionTurnContracts.normalizeCorrectionPersistence` | `invalidCorrectionPersistenceFallsBackToBalanced` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.invalidCorrectionPersistenceFallsBackToBalanced"` |
| 20 | `action_whitelist_for_mode` | `SessionTurnContracts.actionWhitelistForMode` | `actionWhitelistMatchesMode` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.actionWhitelistMatchesMode"` |
| 21 | `_student_role_for_action` | `SessionTurnContracts.studentRoleForAction` | `studentRoleForActionMapsCanonicalRoles` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.studentRoleForActionMapsCanonicalRoles"` |

## 2. Policy Decision Rules

| # | Old Source Expression | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 22 | study fallback to `ask` | `SessionTurnPolicy.normalize` | `studyInvalidActionFallsBackToAsk` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.studyInvalidActionFallsBackToAsk"` |
| 23 | `no_entry + ask → clue` | `SessionTurnPolicy.normalize` | `noEntryWithAskBecomesClue` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.noEntryWithAskBecomesClue"` |
| 24 | `no_entry + probe → clue` | `SessionTurnPolicy.normalize` | `noEntryWithProbeBecomesClue` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.noEntryWithProbeBecomesClue"` |
| 25 | `no_entry + next → clue` | `SessionTurnPolicy.normalize` | `noEntryWithNextBecomesClue` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.noEntryWithNextBecomesClue"` |
| 26 | `no_entry + recap → clue` | `SessionTurnPolicy.normalize` | `noEntryWithRecapBecomesClue` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.noEntryWithRecapBecomesClue"` |
| 27 | `has_entry + clue → probe` | `SessionTurnPolicy.normalize` | `hasEntryWithClueBecomesProbe` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.hasEntryWithClueBecomesProbe"` |
| 28 | `has_entry + scaffold_example → probe` | `SessionTurnPolicy.normalize` | `hasEntryWithScaffoldExampleBecomesProbe` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.hasEntryWithScaffoldExampleBecomesProbe"` |
| 29 | `_is_understood_claim` → `examiner_verify` | `SessionTurnPolicy.normalize` | `understoodClaimBecomesExaminerVerify` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.understoodClaimBecomesExaminerVerify"` |
| 30 | understood claim keyword variety | `SessionTurnPolicy.normalize` | `understoodClaimWithDifferentKeyword` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.understoodClaimWithDifferentKeyword"` |
| 31 | `force_probe → probe` | `SessionTurnPolicy.normalize` | `forceProbeOverridesActionToProbe` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.forceProbeOverridesActionToProbe"` |
| 32 | `probing_intensity>=5 + has_entry + ask → probe` | `SessionTurnPolicy.normalize` | `highProbingIntensityConvertsAskToProbe` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.highProbingIntensityConvertsAskToProbe"` |
| 33 | low intensity keeps ask | `SessionTurnPolicy.normalize` | `lowProbingIntensityKeepsAsk` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.lowProbingIntensityKeepsAsk"` |
| 34 | `has_active_error + correctness<0.5 → small_lecture` | `SessionTurnPolicy.normalize` | `activeErrorAndLowCorrectnessConvertsToSmallLecture` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.activeErrorAndLowCorrectnessConvertsToSmallLecture"` |
| 35 | no active error keeps ask | `SessionTurnPolicy.normalize` | `noActiveErrorKeepsAsk` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.noActiveErrorKeepsAsk"` |
| 36 | `correction_timing==summary_only + evidence==correction → recap` | `SessionTurnPolicy.normalize` | `summaryOnlyCorrectionConvertsToRecap` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.summaryOnlyCorrectionConvertsToRecap"` |
| 37 | immediate timing keeps action | `SessionTurnPolicy.normalize` | `immediateTimingDoesNotConvertToRecap` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.immediateTimingDoesNotConvertToRecap"` |

## 3. Mode-Specific Rules

| # | Old Source Expression | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 38 | goal evidence → none | `SessionTurnPolicy.normalize` | `goalModeResetsEvidenceToNone` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.goalModeResetsEvidenceToNone"` |
| 39 | companion evidence → none | `SessionTurnPolicy.normalize` | `companionModeResetsEvidenceToNone` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.companionModeResetsEvidenceToNone"` |
| 40 | goal invalid action → fallback | `SessionTurnPolicy.normalize` | `goalModeInvalidActionFallsBackToAdvance` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.goalModeInvalidActionFallsBackToAdvance"` |
| 41 | companion negative → empathize | `SessionTurnPolicy.normalize` | `companionModeNegativeInputBecomesEmpathize` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.companionModeNegativeInputBecomesEmpathize"` |

## 4. First-Turn Constraints

| # | Old Source Expression | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 42 | `OPENING_SUFFIX` study → ask, eval=0 | `SessionTurnPolicy.normalize` | `firstTurnStudyIsAsk` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.firstTurnStudyIsAsk"` |
| 43 | first-turn goal → decompose | `SessionTurnPolicy.normalize` | `firstTurnGoalIsDecompose` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.firstTurnGoalIsDecompose"` |
| 44 | first-turn companion → observe | `SessionTurnPolicy.normalize` | `firstTurnCompanionIsObserve` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.firstTurnCompanionIsObserve"` |

## 5. Entry Status Derivation

| # | Old Source Expression | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 45 | `_method_entry_from_user` recall_decay keywords | `SessionTurnPolicy.deriveEntryStatus` | `recallDecayKeywordSetsEntryStatus` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.recallDecayKeywordSetsEntryStatus"` |
| 46 | `_method_entry_from_user` no_entry keywords | `SessionTurnPolicy.deriveEntryStatus` | `noEntryKeywordSetsEntryStatus` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.noEntryKeywordSetsEntryStatus"` |

## 6. Process Summary

| # | Old Source Expression | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 47 | `_build_process_summary` non-empty | `SessionTurnPolicy.buildProcessSummary` | `processSummaryIsNonEmpty` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.processSummaryIsNonEmpty"` |
| 48 | blank knowledge point default | `SessionTurnPolicy.normalize` | `blankKnowledgePointUsesDefault` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.SessionTurnPolicyTest.blankKnowledgePointUsesDefault"` |

## 7. Conversation Context Assembly

| # | Old Source Behavior | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 49 | empty context | `ConversationContextContract.empty` | `emptyContractHasCorrectIdentity` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.emptyContractHasCorrectIdentity"` |
| 50 | session isolation | `ConversationContextAssembler.assemble` | `sessionIsolationInMessagePort` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.sessionIsolationInMessagePort"` |
| 51 | per-category limits | `ConversationContextAssembler.assemble` | `perCategoryLimitsAreEnforced` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.perCategoryLimitsAreEnforced"` |
| 52 | stable sort messages | `ConversationContextAssembler.assemble` | `messagesAreSortedByTimestampDescending` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.messagesAreSortedByTimestampDescending"` |
| 53 | stable sort sources | `ConversationContextAssembler.assemble` | `sourceEvidenceSortedByRelevanceDescending` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.sourceEvidenceSortedByRelevanceDescending"` |
| 54 | text length caps | `ConversationContextAssembler.assemble` | `textLengthIsCapped` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.textLengthIsCapped"` |
| 55 | partial failure degradation (message) | `ConversationContextAssembler.assemble` | `messageSourceFailureDegradesOnlyMessages` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.messageSourceFailureDegradesOnlyMessages"` |
| 56 | partial failure degradation (graph) | `ConversationContextAssembler.assemble` | `graphFailureDegradesOnlyGraph` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.graphFailureDegradesOnlyGraph"` |
| 57 | no secret-like fields | `ConversationContextAssembler.assemble` | `noSecretLikeFieldsExist` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationContextAssemblerTest.noSecretLikeFieldsExist"` |

## 8. Session Turn Coordinator

| # | Old Source Behavior | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 58 | success persistence | `ConversationSessionCoordinator.executeTurn` | `successPersistsUserAndAssistantMessages` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.successPersistsUserAndAssistantMessages"` |
| 59 | policy output flows | `ConversationSessionCoordinator.executeTurn` | `policyOutputFlowsIntoResult` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.policyOutputFlowsIntoResult"` |
| 60 | user msg before generation | `ConversationSessionCoordinator.executeTurn` | `userMessageAcceptedBeforeGeneration` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.userMessageAcceptedBeforeGeneration"` |
| 61 | stale token → no persist | `ConversationSessionCoordinator.executeTurn` | `staleTokenDoesNotPersistAssistantResult` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.staleTokenDoesNotPersistAssistantResult"` |
| 62 | stale outcome → no persist | `ConversationSessionCoordinator.executeTurn` | `staleOutcomeFromGenerationDoesNotPersist` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.staleOutcomeFromGenerationDoesNotPersist"` |
| 63 | deleted session | `ConversationSessionCoordinator.executeTurn` | `deletedSessionReturnsSafeResult` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.deletedSessionReturnsSafeResult"` |
| 64 | no model | `ConversationSessionCoordinator.executeTurn` | `noModelProducesSafeTerminal` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.noModelProducesSafeTerminal"` |
| 65 | provider failure | `ConversationSessionCoordinator.executeTurn` | `providerFailureRecordsTerminalFailure` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.providerFailureRecordsTerminalFailure"` |
| 66 | provider error no secrets | `ConversationSessionCoordinator.executeTurn` | `providerFailureDoesNotContainSecrets` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.providerFailureDoesNotContainSecrets"` |
| 67 | duplicate idempotent | `ConversationSessionCoordinator.executeTurn` | `duplicateTurnIsIdempotent` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.duplicateTurnIsIdempotent"` |
| 68 | unsupported vision | `ConversationSessionCoordinator.executeTurn` | `unsupportedVisionReturnsSafeResult` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.unsupportedVisionReturnsSafeResult"` |
| 69 | blank prompt | `ConversationSessionCoordinator.executeTurn` | `blankPromptReturnsSafeResult` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.blankPromptReturnsSafeResult"` |
| 70 | context evidence flows | `ConversationSessionCoordinator.executeTurn` | `contextEvidenceFlowsIntoGenerationRequest` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.ConversationSessionCoordinatorTest.contextEvidenceFlowsIntoGenerationRequest"` |

## 9. Session Conversation Contract (feature:chat)

| # | Specification Requirement | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 71 | contract has all fields | `SessionConversationContract` | `successContractContainsAllFields` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.successContractContainsAllFields"` |
| 72 | no model state | `SessionConversationFacade.mapResult` | `noModelContractHasCorrectState` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.noModelContractHasCorrectState"` |
| 73 | provider error safe | `SessionConversationFacade.mapResult` | `providerErrorContractHasSafeError` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.providerErrorContractHasSafeError"` |
| 74 | stale token safe | `SessionConversationFacade.mapResult` | `staleTokenContractHasSafeError` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.staleTokenContractHasSafeError"` |
| 75 | session deleted safe | `SessionConversationFacade.mapResult` | `sessionDeletedContractHasSafeError` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.sessionDeletedContractHasSafeError"` |
| 76 | discarded has reason | `SessionConversationFacade.mapResult` | `discardedContractHasReason` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.discardedContractHasReason"` |
| 77 | stable event IDs | `SessionConversationFacade.mapResult` | `eventsHaveStableIds` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.eventsHaveStableIds"` |
| 78 | empty contract defaults | `SessionConversationContract.empty` | `emptyContractHasCorrectDefaults` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.emptyContractHasCorrectDefaults"` |
| 79 | no secrets in fields | `SessionConversationFacade.mapResult` | `contractFieldsDoNotContainSecrets` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.contractFieldsDoNotContainSecrets"` |
| 80 | no raw exception leak | `SessionConversationFacade.mapResult` | `providerErrorDoesNotLeakRawException` | migrated | `.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.SessionConversationContractTest.providerErrorDoesNotLeakRawException"` |

## 10. Learning Overview

| # | Specification Requirement | Native Contract / Function | Test Method | Status | Verify Command |
|---|---|---|---|---|---|
| 81 | all-session scope | `LearningOverviewCoordinator.generate` | `allSessionScopeReturnsAggregatedData` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.allSessionScopeReturnsAggregatedData"` |
| 82 | selected-session scope | `LearningOverviewCoordinator.generate` | `selectedSessionScopePassesSessionIds` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.selectedSessionScopePassesSessionIds"` |
| 83 | empty state no fabrication | `LearningOverviewCoordinator.generate` | `emptyStateHasNoFabricatedData` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.emptyStateHasNoFabricatedData"` |
| 84 | weak point stable ranking | `LearningOverviewCoordinator.generate` | `weakPointsSortedBySeverityThenErrorCountThenId` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.weakPointsSortedBySeverityThenErrorCountThenId"` |
| 85 | mainline stable sort | `LearningOverviewCoordinator.generate` | `weeklyMainlineSortedByUpdatedAtDescending` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.weeklyMainlineSortedByUpdatedAtDescending"` |
| 86 | token estimated flag | `LearningOverviewCoordinator.generate` | `tokenUsageHasEstimatedFlag` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.tokenUsageHasEstimatedFlag"` |
| 87 | session failure warning | `LearningOverviewCoordinator.generate` | `sessionFailureProducesWarning` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.sessionFailureProducesWarning"` |
| 88 | weak point failure warning | `LearningOverviewCoordinator.generate` | `weakPointFailureProducesWarning` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.weakPointFailureProducesWarning"` |
| 89 | mainline limit | `LearningOverviewCoordinator.generate` | `mainlineLimitIsEnforced` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.mainlineLimitIsEnforced"` |
| 90 | weak point limit | `LearningOverviewCoordinator.generate` | `weakPointLimitIsEnforced` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.weakPointLimitIsEnforced"` |
| 91 | token no secrets | `LearningOverviewCoordinator.generate` | `tokenUsageDoesNotContainSecrets` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.tokenUsageDoesNotContainSecrets"` |
| 92 | partial failure non-blocking | `LearningOverviewCoordinator.generate` | `partialFailureDoesNotBlockOtherSources` | migrated | `.\gradlew.bat :core:domain:testDebugUnitTest --tests "*.LearningOverviewCoordinatorTest.partialFailureDoesNotBlockOtherSources"` |

## Summary

- **Total rules mapped:** 92
- **All status:** migrated (code written; awaiting gradle verification in Codex environment)
- **Frozen layer diff:** expected empty (no modifications to core:model, core:protocol, core:llm, core:data/*Repository, Room, SecretStore)
- **New files:** 14 Kotlin source/test files + 1 build.gradle.kts modification + this parity matrix
- **Test commands:**
  - `.\gradlew.bat :core:domain:testDebugUnitTest` (Tasks 1-4, 6)
  - `.\gradlew.bat :feature:chat:testDebugUnitTest` (Task 5)
  - `.\gradlew.bat test :app:lint :app:assembleDebug` (broader regression)
  - `git diff --check` (whitespace hygiene)
  - `git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data` (frozen layer check)
