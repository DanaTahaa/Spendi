# ADR 002: Java 21 financial API

Status: accepted for the portfolio backend milestone.

## Context

Spendi has an existing Java 17 Android application and an independent Java 17 financial core. The next milestone needs an authenticated API, PostgreSQL behavior demonstrated by integration tests, and clear concurrency/consistency rules.

## Decisions

- Add `backend` to the original Gradle project, with a Java 21 toolchain and `--release 21`. It depends on `core`; Android does not depend on the backend. `-PbackendOnly` excludes the Android module for server-only development and CI.
- Use the JDK HTTP server and virtual threads for this local milestone. SparkJava 2.9.4 is associated with an older Jetty dependency line; matching a recruitment stack is less valuable than understanding a small, testable service. This is not a claim that the JDK server supplies production ingress, timeouts, or rate limiting. A public deployment requires those controls and a separate server/framework review.
- Use PostgreSQL for persistence, Flyway for versioned migrations, and jOOQ generated types for parameterized access. Generate types with the DDL database from migrations, without requiring a running database at compile time. Its H2-based DDL interpretation is only a code-generation mechanism; correctness is checked on real PostgreSQL.
- Verify provider-issued RS256 access tokens with Nimbus, a configured HTTPS JWKS endpoint, exact issuer, API audience, required subject/expiry, and bounded clock tolerance. The API supplies no account registration or token issuer. Provider provisioning is external; tests use freshly generated RSA keys. Development token mode is explicit and loopback-only.
- Scope every financial query to the verified subject. Missing and inaccessible resources both return 404. Reject unknown JSON properties rather than accepting caller-supplied ownership.
- Keep USD-only storage as positive `BIGINT` minor units. JSON money is a string, not a floating-point number. PostgreSQL `SUM(bigint)` yields arbitrary-precision numeric totals. Budgets and totals are read within a repeatable-read transaction for a consistent summary.
- Reserve `(subject, Idempotency-Key)` inside the same database transaction as the expense insertion and original response snapshot. PostgreSQL unique-key contention serializes retries. Rollback removes both reservation and expense. Reusing a key with different validated fields returns 409. Replay returns the creation snapshot even after subsequent updates/deletion. Automatic expiration and retention/erasure policy are deferred and must be designed before real customer use.
- Require a version for transaction updates/deletes and existing budget updates. The SQL write predicate includes that version and increments it atomically; a stale write returns 409.
- Use bounded offset pagination with deterministic ordering. It is suitable for a small expense listing but is not an export snapshot under concurrent writes. Cursor/snapshot pagination is deferred until warranted.
- Use Spock for security, validation, PostgreSQL concurrency/rollback, and HTTP specifications. Provide both temporary native PostgreSQL tests and external PostgreSQL tests. The external test fixture requires a disposable database named `spendi_test` and truncates only its financial tables. CI must run this task explicitly.

## Consequences and limits

One service is sufficient to demonstrate the current domain. This milestone does not introduce microservices, Redis, production deployment, or Android synchronization. It does not claim a historical TDD cycle, benchmark results, or production readiness. The database process bundled for tests is an isolated real PostgreSQL server, not an in-memory substitute.

Migration execution currently shares the configured application database role. Separate migration/runtime roles and least-privilege grants, TLS ingress, HTTP request timeouts, rate limiting, operational metrics, and secure provider provisioning remain deployment requirements.
