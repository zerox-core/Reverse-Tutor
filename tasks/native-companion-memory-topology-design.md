# Native Companion Memory Topology Design

> Status: approved design
>
> Scope: `newmp` native Android production branch
>
> Purpose: make the old `main` teaching-turn algorithm, a Hermes-inspired companion memory runtime, window-tree isolation, and window-level initiative coexist without coupling the UI to storage or Provider details.

## 1. Decisions fixed by product direction

1. The app opens into a **companion root window**. It has no preset learning goal or mode; it can read global learning facts and global interaction-interface data, and it owns its own cross-session companion memory.
2. Every root window starts with heartbeat capability enabled. A child window has no heartbeat schedule unless an explicit enable command targets that child.
3. The companion root is the only root allowed to read and write personality, relationship, cadence, and shared-experience memory. Other roots and all children cannot read or contaminate that domain.
4. Every window may emit normalized learning facts to the global learning ledger. No window may emit raw transcript, personality inference, relationship inference, or cadence data into that ledger.
5. A child window receives a complete readable snapshot of its parent at fork time only. Parent changes after that time never flow into an existing child.
6. A child may merge only into its direct parent. Siblings never merge. A merge is a one-way, idempotent commit of a selected memory delta; later child changes, child deletion, and heartbeat state never alter the committed parent memory.
7. Deleting a branch deletes its local conversation and local memory only. Previously committed parent merges and normalized global learning facts remain.
8. Learning windows retain their declared learning intent. Related changes of subject, level, or study method are allowed. A soft scope guard may naturally re-anchor sustained unrelated drift, but never blocks a turn, stores raw monitoring text, or exposes a per-turn monitoring UI.
9. Personality and relationship evolution follow a Hermes-inspired background-curation model, not a learning-forgetting curve. Learning evidence may use review/retention algorithms separately.
10. Ordinary users do not receive a memory-admin, history, or rollback surface. The system retains the minimum internal provenance necessary for diagnostics and idempotence.

## 2. Topology and memory domains

```text
WindowTree
  root: CompanionRoot
    CompanionMemoryDomain (cross-session)
    GlobalLearningLedger (read-only projection)
    root heartbeat

  root: Learning / Import / Challenge / other task root
    Local task memory
    GlobalLearningLedger (read/write normalized facts)
    root heartbeat

  child (any root)
    inherited parent snapshot at fork time + local delta
    no heartbeat unless explicitly enabled
    merge only to direct parent
```

### 2.1 Companion memory domain

This domain is attached only to the companion root. It has five bounded partitions:

| Partition | Holds | May affect initiative |
| --- | --- | --- |
| Core setting | template-derived persona, interaction baseline, relationship baseline | yes |
| Stable preference | durable explanation, tone, pacing, or interaction preferences | yes |
| Shared experience | normalized commitments, durable shared topics, significant milestones | yes |
| Cadence | aggregate activity windows, conversation frequency, cooling state | yes |
| Short-lived situation | recent unfinished topic, short-term task or affect signal | bounded TTL only |

The companion reads global learning facts to make teaching or initiative contextually relevant, but task windows never read companion personality, relationship, cadence, or shared experience.

### 2.2 Global learning ledger

The ledger receives only structured learning facts with source provenance:

```text
knowledge point / evidence type / result / confidence / source window / source turn / occurred time
```

Examples are mastery evidence, recurring error category, completed exercise, plan progress, and review eligibility. It is intentionally not a raw-message archive and not a personality store.

### 2.3 Window-local memory

Each window owns:

- immutable fork snapshot reference;
- local summaries and task context;
- local observations not yet merged;
- local scope signals when the window has a learning intent;
- local heartbeat state only when explicitly enabled for a child.

The snapshot makes each branch a time slice. No live parent subscription, sibling read, sibling merge, or retroactive parent update exists.

## 3. Hermes-inspired memory curation

Hermes provides the reference pattern: a background review pass separately evaluates whether a conversation reveals durable persona, desire, preference, personal detail, or interaction expectation; it does not block the foreground turn. Reverse Tutor adopts the separation, but not Hermes's terminal-agent tools or direct data model.

```text
completed turn
  -> detached MemoryCurator
  -> MemoryObservation candidates
  -> MemoryEvolutionPolicy
  -> active companion-memory version or no-op
```

### 3.1 Observation contract

An observation carries a normalized fact, partition, source class, time, confidence, and a compact provenance handle. It does not persist the raw conversation text merely to support curation.

### 3.2 Evolution policy

The policy does not use a generic forgetting curve. It decides whether a candidate should be ignored, retained as short-lived context, reinforce an active memory, supersede it, or conflict with it.

An automatic supersession must require consistent independent evidence, temporal stability, and a material advantage over the active version. A template-derived or manually set value can be superseded, but only by this high-confidence path; one user instruction, joke, roleplay turn, or model guess is insufficient.

Old active values stop entering prompts after supersession. Minimal internal version/provenance records remain for diagnosis, merge idempotence, and repair; they are not a user-facing control panel.

### 3.3 Separation from learning retention

Learning knowledge, mastery evidence, and review scheduling may later use pedagogical retention or spaced-review algorithms. Those algorithms must not be reused to infer or erase personality, relationship, or interaction style.

## 4. Learning scope guard

Only a window with `LearningIntentEnvelope` runs `LearningScopeGuard`.

```text
declared intent + turn plan + normalized trajectory signal
  -> continuous | related evolution | ambiguous | sustained out-of-scope
```

Related evolution includes subject progression (for example high-school to university mathematics), goal progression, and learning-method/style changes. The guard stores only a minimal structured signal and never a transcript copy.

For sustained high-confidence drift, the first release has a soft response only: it adds a natural re-anchoring constraint to the next turn plan. It never blocks the user, displays a warning, or silently changes the declared learning goal. Guard thresholds and operational monitoring are future policy configuration, not part of the first persistence migration.

The companion root has no learning intent and therefore does not run this guard.

## 5. Heartbeat and initiative

Heartbeat is a window-level capability, not a companion-only feature and not a timer that directly calls an LLM.

```text
HeartbeatSchedulePort
  -> InitiativeEligibilityCoordinator
  -> InitiativePlan
  -> BackgroundGenerationPort
  -> message written to the eligible target window
```

### 5.1 Eligibility

Each eligible window evaluates its own topology snapshot, local context, normalized global learning facts, current generation state, unread state, recent user activity, and cooldown. The companion root additionally evaluates companion memory and global interaction-interface data.

If no scenario policy is configured, an enabled heartbeat remains silent. The initial chat-window toggle controls eligibility for that window; richer scenario, frequency, and notification controls are future UI work.

### 5.2 Root and child rules

- Every root window has heartbeat capability enabled at creation.
- A child window starts without a schedule, even if its parent has one.
- Only an explicit `EnableWindowHeartbeatCommand` creates or enables a child schedule.
- A heartbeat plan always targets the exact eligible window. It never redirects into a sibling, parent, or root.
- A branch merge never transfers schedules, cooldown state, initiative plans, or delivery history.
- Branch deletion cancels only pending work owned by that branch.

### 5.3 Initiative plans

`InitiativePlan` is structured: intent, related topology nodes, evidence handles, tone constraints, expiry, minimum cooldown, target window, and delivery policy. It is not a fixed reminder string. The generation algorithm turns the plan into a contextually grounded opening only after eligibility succeeds.

Notification delivery is independent from initiative eligibility. The first implementation may write into the target window without notifying; notification settings must not change the decision to create an initiative plan.

## 6. One real turn path

```text
user turn or eligible heartbeat
  -> WindowRuntimeResolver
  -> ContextTopologyAssembler
  -> LearningScopeGuard (learning windows only)
  -> TurnPlanner (old-main teaching parity)
  -> immutable BackgroundGenerationJob snapshot
  -> BackgroundGenerationWorker (single Provider call and assistant write)
  -> StructuredTurnOutcome
  -> PostTurnProjector
       -> local delta / global learning ledger / memory observations / heartbeat eligibility
```

`BackgroundGenerationWorker` must consume the immutable job snapshot rather than re-querying the newest memories during retries. This preserves token/session safety, deterministic recovery, and testability.

The old `main` teaching behavior must be migrated through `TurnPlanner`: mode/role selection, action selection, contextual retrieval, prompt recipe, structured evaluation, mastery evidence, and post-turn projection. A contract that is not consumed by this real worker path is not considered migrated.

## 7. Contract ownership and frozen-layer gates

### 7.1 Non-frozen first

`core:domain`, `feature:chat`, and app wiring first define and test pure contracts:

- `WindowTopologyContract`, `WindowSnapshotContract`, `MemoryDeltaContract`, `MergeCommitContract`;
- `MemoryObservation`, `CompanionMemorySnapshot`, `MemoryEvolutionPolicy`;
- `LearningIntentEnvelope`, `ScopeSignal`, `LearningScopeGuard`;
- `HeartbeatSchedule`, `InitiativeEligibility`, `InitiativePlan`;
- `TurnPlan`, `StructuredTurnOutcome`, and their Facade/Coordinator ports.

UI receives published contracts only. It cannot query Room, DAO, entities, secrets, or protocol DTOs.

### 7.2 Frozen changes require separate requests

The following are frozen and need independently approved change requests before implementation:

| Gate | Needed reason |
| --- | --- |
| P3 generation protocol | persist immutable topology/context/plan snapshot and return validated structured turn outcome |
| P6 persistence | Room schema, DAOs, repositories, migration, branch snapshot/delta/merge data, ledger facts, scope signals, heartbeat jobs |
| P1/P2 boundary | publish only stable cross-layer models and Facade/Coordinator I/O after domain behavior is tested |

No UI task may alter those layers. No implementation may claim that a generated reply updated memory, mastery, or initiative state unless the approved persistence path actually committed it.

## 8. Acceptance invariants

1. A child never reads parent changes made after its fork timestamp.
2. Only child-to-direct-parent merge is accepted; repeated merge of the same delta is idempotent.
3. Child changes and deletion cannot mutate an already committed parent merge.
4. Sibling memories cannot be read, merged, or scheduled across each other.
5. Global learning receives normalized learning facts only; companion memory remains exclusive to the companion root.
6. Root heartbeat is enabled by default; child heartbeat requires explicit enablement.
7. A heartbeat plan never calls the Provider directly and never targets a different window.
8. Learning scope guard is soft, transcript-minimizing, and absent from the companion root.
9. The Worker consumes an immutable job snapshot and remains the sole assistant-generation writer.
10. Personality evolution never relies on a teaching-memory curve; learning retention never becomes personality inference.

## 9. Deferred UI work

The backend design intentionally does not prescribe visual composition. Future UI may implement a root-window launcher, branch creation, child heartbeat command, merge/delete actions, and a temporary window heartbeat switch in any presentation style. Its only obligation is to use the published contracts and commands above.
