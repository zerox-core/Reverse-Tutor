# Sync Push And WorldTree Blockers Design

Date: 2026-07-13

## Scope

This change resolves two blockers from the mobile-native V1 integration contract:

1. Make `POST /api/v1/sync/push` use the canonical
   `envelopeId + entityId + accepted` item result.
2. Add the WorldTree Room schema, DAO, repository, migration, and contract tests.

The change does not implement `/content/*`, activity authentication, standard online
errors, activity participation deletion, community APIs, Compose screens, or feature
module integration.

## Sync Push Contract

Each sync request item must include `envelopeId`. Each result must include:

```text
envelopeId
entityId
accepted
remoteRevision
errorCode
retryable
```

Accepted items use `accepted=true`. Rejected and temporarily failed items use
`accepted=false`. The FastAPI service and Android decoder will not accept or emit the
legacy `status` field. An old response is a protocol error rather than a compatibility
input.

Idempotency remains scoped by user and idempotency key. A repeated request returns the
complete first result, including its original envelope ID, without another write.
Unexpected failure of one item must not block the remaining batch items.

## WorldTree Domain

`core:model` owns the frozen WorldTree domain types:

- `WorldTreeDraft`, `WorldTreeMode`, and `WorldTreeDraftState`
- `WorldTreeSection` and `WorldTreeSectionType`
- A sealed payload type for student role, learning goal, study schedule, portrait
  system, story plot, source library, and custom sections
- Nested milestone, portrait dimension, and story stage value types

Wire values remain `snake_case` in JSON adapters while Kotlin domain enum names remain
stable. Unknown or invalid persisted payloads are surfaced as data errors and are not
silently converted to business defaults.

The existing `SessionCreationInput(role, goal, profileText)` remains untouched only for
legacy compatibility. New WorldTree persistence code does not depend on those fields.
Creating a session from a WorldTree and wiring Compose to the repository are outside
this change.

## Room Schema

Room advances from schema version 3 to 4 and adds only these tables:

```text
world_tree_drafts
world_tree_sections
world_tree_source_cross_ref
```

`world_tree_drafts.sessionId` is nullable and uniquely indexed. Sections reference a
draft and store common queryable fields plus a versioned `payloadJson`. Source cross
references use `(draftId, sourceId)` as their primary key and preserve order.

Foreign keys enforce draft ownership and source/session references. Deleting a draft
cascades to its sections and source cross references. No migration SQL updates,
rebuilds, or deletes existing session, message, source, or graph tables.

Payload encoding is owned by a versioned codec selected by `schemaVersion` and section
type. UI and feature modules never assemble persisted JSON directly.

## Repository Behavior

`WorldTreeRepository` exposes the operations frozen in
`docs/contracts/mobile-native-integration-v1.md`:

- observe and create drafts
- update a title
- upsert and reorder sections
- remove custom sections
- replace source links
- attach a draft to a session
- archive a draft

Compound writes run in Room transactions. Reordering rejects duplicate, unknown, or
missing section IDs. Only custom sections can be removed. Source replacement rejects
duplicate or unknown source IDs. Session attachment validates the target session and
the database unique index prevents two drafts from binding to one active session.

## Error Handling

Repository validation fails before mutation. Database constraint violations are not
reported as successful operations. Invalid persisted payload JSON produces a stable
data-layer failure so callers can show an error state without losing the stored value.

Sync item failures return `accepted=false`, a stable error code, and an accurate
`retryable` value. Batch processing continues after an individual item failure.

## Verification

Tests cover:

- FastAPI canonical success, rejection, failure isolation, and idempotent replay
- Android canonical decoding and rejection of legacy `status` responses
- WorldTree payload codec round trips for all seven section types
- Repository creation, observation, updates, ordering, source replacement, attachment,
  archival, and validation failures
- Room 3-to-4 migration with pre-existing session, message, source, graph node, and
  graph edge rows verified unchanged afterward
- Room schema export and policy checks

Verification uses focused Kotlin and Python tests followed by the existing module and
Python regression suites. Automated tests do not use real provider services, user
secrets, or private learning content.
