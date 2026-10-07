# Native Hybrid Production Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hybrid architecture's test-only runtime and fake frontend ports with injectable production transports, repositories, coordinators, and application wiring while preserving offline operation.

**Architecture:** Provider calls remain direct from Android by default and use an injected HTTP transport plus SecretStore resolver. Python remains optional through `OnlineApi`; local chat and local data never require it. App-level factories adapt suspend domain/data repositories into lifecycle-aware frontend ports.

**Tech Stack:** Kotlin, Coroutines, HttpURLConnection or an isolated injected HTTP adapter, WorkManager, Room, DataStore, Android Keystore, FastAPI, JUnit4, pytest.

---

## Task 1: Production provider runtime

**Owner:** Backend/runtime Agent

**Files:**
- Modify: `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationLifecycle.kt`
- Create: `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/ProviderHttpTransport.kt`
- Create: `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/ProductionLlmGenerationRuntime.kt`
- Test: `mobile-native/core/llm/src/test/java/com/reversetutor/core/llm/ProductionLlmGenerationRuntimeTest.kt`

- [ ] Add an injected transport that accepts URL, headers, JSON body, timeout, and streaming mode.
- [ ] Add OpenAI-compatible, Anthropic-compatible, and Gemini-native response parsers.
- [ ] Resolve API keys through an injected `secretRef -> secret` function; never place keys in models, logs, errors, or tests.
- [ ] Map 401/403/404/429/5xx/timeout/protocol failures to stable retryable results.
- [ ] Add a composite runtime that routes using the saved provider/protocol and keeps `FakeLlmGenerationRuntime` available only for tests/previews.
- [ ] Verify payloads, headers, SSE chunks, malformed responses, missing secret, and timeout using a fake transport.

## Task 2: Android online transport and repositories

**Owner:** Remote/data Agent

**Files:**
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineApi.kt`
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/HttpOnlineApi.kt`
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineHttpTransport.kt`
- Create: `mobile-native/core/data/src/main/java/com/reversetutor/core/data/online/OnlineRepositoryAdapters.kt`
- Create: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/HttpOnlineApiTest.kt`
- Create: `mobile-native/core/data/src/test/java/com/reversetutor/core/data/online/OnlineRepositoryAdaptersTest.kt`

- [ ] Make `OnlineApi` suspendable.
- [ ] Implement configurable base URL and injected auth-token provider.
- [ ] Encode/decode activities, progress, sync, weekly insight, and release metadata without exposing HTTP DTOs to frontend/domain.
- [ ] Implement `ActivityRepository`, `SyncTransport`, and `UpdateRepository` adapters.
- [ ] Preserve local Outbox data on retryable network failure.
- [ ] Ensure sync only transmits allowed shared entity types.
- [ ] Verify all behavior with fake HTTP responses and no real network.

## Task 3: Application graph and real frontend ports

**Owner:** Frontend/application Agent

**Files:**
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/HybridAppGraph.kt`
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/HybridFrontendPorts.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/MainActivity.kt`
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- Test: `mobile-native/app/src/test/java/com/reversetutor/preview/HybridAppGraphTest.kt`
- Test: `mobile-native/app/src/test/java/com/reversetutor/preview/HybridFrontendPortsTest.kt`

- [ ] Build one application graph that owns Repository and Coordinator instances.
- [ ] Adapt SessionRepository, ConversationRunRepository, ModelConnectionRepository, Learning repositories, and SyncCoordinator into the async frontend ports.
- [ ] Replace default Fake ports in production factories while retaining explicit fakes in tests/previews.
- [ ] Persist session-level model selection through `SessionRepository.setModelBinding`.
- [ ] Connect Workspace locks to composer focus, widget drag, and fullscreen graph state.
- [ ] Avoid global coroutine scopes; scopes must be lifecycle-owned or explicitly closed.

## Controller integration

- [ ] Register required module dependencies and Android INTERNET permission.
- [ ] Configure production runtime in DataModule without removing deterministic test constructors.
- [ ] Connect background generation to the production runtime and per-Run model snapshot.
- [ ] Run native tests, lint, debug build, Python regression, static layer checks, and review.
- [ ] Record that no real provider call was made during automated verification.
