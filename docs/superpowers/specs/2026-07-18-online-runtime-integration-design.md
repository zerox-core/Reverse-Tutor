# Online Runtime Integration Design

## Goal

Turn the existing PostgreSQL-first catalog, FastAPI HTTP contracts, and Android online adapters into a runnable end-to-end slice without changing local-first ownership of sessions, chat, sources, world trees, graphs, or model credentials.

## Approaches Considered

1. Keep challenge UI state local and use the server only for background refresh. This preserves the current screen quickly but creates two sources of truth and can show a successful join that the server never accepted.
2. Automatically seed or rewrite catalog rows during every FastAPI startup. This makes demos easy but gives application startup hidden production write behavior and makes operator changes unsafe.
3. Use an explicit catalog seed command, a read-only online readiness endpoint, and an Android runtime coordinator backed by the existing repositories. This is the selected approach because writes remain deliberate, PostgreSQL remains authoritative, and the app has one challenge state machine.

## Architecture

### PostgreSQL Catalog Bootstrap

- Add an explicit, repeatable CLI that creates the initial public-interest article and active challenge only when their slugs are absent.
- The command uses the SQLAlchemy stores and requires the Alembic schema to be at head before writing.
- Re-running the same seed is a no-op. It never overwrites operator-managed rows and never touches auth secrets or local learning data.
- Provide a PostgreSQL 16 Compose service for development and migration drills. SQLite remains a compatibility-test target, not a production writer.

### FastAPI Readiness

- Add a public `/api/v1/health` endpoint for online-service readiness.
- The response exposes service status, Alembic head, catalog availability, and UTC server time. It does not expose database URLs, tokens, account data, or local learning statistics.
- A configured PostgreSQL schema mismatch remains fail-closed at startup. The endpoint reports the already-configured runtime; it does not run migrations or seed data.

### Android Challenge Runtime

- Add a challenge coordinator that loads active activity data and leaderboard rows through `ActivityRepository`.
- The coordinator obtains the authenticated account/device identity from the same anonymous auth session manager used by `HttpOnlineApi`.
- Join requests use a deterministic idempotency key derived from the authenticated account, activity, and revision. The UI changes to joined only after server confirmation.
- Local-only mode keeps the formal reference presentation but does not fabricate an online join. Network failures retain the last confirmed state and expose retryable failure state without changing local learning records.
- Existing Compose layout and spatial paging remain unchanged; only the challenge data source and loading/error states change.

## Data Flow

1. Operator runs Alembic upgrade and the explicit seed command.
2. FastAPI validates the database head and wires one SQLAlchemy-backed content/activity port set.
3. Android receives a configured base URL, lazily bootstraps anonymous auth, and loads `/activities` plus the selected leaderboard.
4. Join uses bearer ownership and the authenticated device ID; PostgreSQL persists the participation and idempotency event.
5. The coordinator renders only the confirmed response. No production dual-write or SQLite fallback is introduced.

## Error Handling

- Missing catalog rows produce an empty/offline challenge state, not hardcoded success.
- Schema mismatch, missing production database configuration, and invalid secrets remain startup failures.
- Authentication refresh failure follows the existing bootstrap policy; challenge requests surface retryability and do not mutate UI confirmation state.
- Revision and idempotency conflicts keep the last confirmed participation and require a reload before retry.

## Verification

- Seed command: idempotent rerun, schema-head enforcement, no secret columns, expected catalog summaries.
- FastAPI: readiness response, no sensitive fields, empty and seeded catalog behavior.
- Android JVM: initial load, local-only fallback, authenticated join, retryable failure, conflict reload, deterministic idempotency.
- Integration: PostgreSQL migration and seed, FastAPI HTTP smoke, Android repository contract. Real-device HTTP/Keystore smoke requires a reachable server URL.

