# Mobile Online API Facade Design

## Goal

Prepare the native Android frontend for the complete online V1 contract without waiting for the backend implementation. The work in this phase is a thin, typed integration skeleton. It must let feature screens use stable repositories and contract mocks while real endpoints are implemented in parallel.

## Scope

- Split the current online client by capability: activities, public-interest content, sync, weekly insight, and release metadata.
- Add typed request and response models matching `docs/contracts/openapi-online-v1.yaml`.
- Keep shared HTTP transport, authentication, error decoding, idempotency, and base URL configuration in one infrastructure layer.
- Add repository facades that expose domain models and UI-ready results rather than HTTP DTOs.
- Add contract-backed fake implementations using `mock-online-v1.json` so design work can proceed without a live backend.
- Wire the facades into `HybridAppGraph` without making online services mandatory for local sessions, messages, sources, world trees, graphs, or secrets.

## Non-Goals

- Do not implement backend behavior.
- Do not add community APIs; the community contract remains outside online V1 until its page is finalized.
- Do not move local-first data into online sync.
- Do not connect Compose screens directly to DTOs or HTTP clients.
- Do not redesign feature pages in this task.

## Architecture

`core:remote` owns capability APIs and wire DTOs:

- `ActivityApi`
- `ContentApi`
- `SyncApi`
- `InsightApi`
- `ReleaseApi`
- shared `OnlineHttpTransport`, token provider, request headers, and canonical error decoding

`core:data` owns repository adapters. Each adapter maps wire DTOs and failures to domain models. Feature modules receive only repository or port interfaces and convert repository results into `loading`, `content`, `empty`, `offline`, and `error` UI states.

`app` owns runtime configuration. The base URL is injected through build configuration and authentication through `OnlineAuthTokenProvider`. A missing online configuration leaves all local-first features operational.

## Runtime Selection

- Production and integration builds use HTTP implementations when an online base URL is configured.
- Preview and unfinished-backend flows use contract fakes with deterministic fixtures.
- Switching implementations occurs in the app graph, not inside screens.

## Error Policy

- Decode the V1 error envelope centrally.
- Preserve stable backend error codes and retryability in the data layer.
- Map error codes to Chinese UI copy only in feature/UI code.
- Treat malformed canonical responses as non-retryable contract failures.
- Treat timeouts and transient transport failures as retryable while preserving cached or local content.
- Never expose tokens, secrets, or raw response bodies in UI errors or logs.

## Verification

- Path and method tests for every V1 endpoint.
- Request and response serialization tests against contract fixtures.
- Authentication, idempotency, pagination, and canonical error tests.
- Repository mapping tests for success, empty, offline, retryable, and contract-failure states.
- App graph tests for HTTP, mock, and local-only configurations.
- Existing sync and world-tree tests must remain green.

## Acceptance Criteria

- All non-community online V1 capabilities have typed frontend interfaces.
- Feature code can consume deterministic mock repositories without a running backend.
- The backend base URL and token provider are configured once at the app boundary.
- Replacing a mock with the real backend requires no Compose layout changes.
- Local sessions and world-tree creation remain available when online services are absent or failing.
