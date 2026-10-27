# Backend verification — Java 21 / PostgreSQL

Date: 2026-10-27 (Asia/Hebron).

Canonical local checkout: `C:/Users/Milani/AndroidStudioProjects/Spendi3`. The backend is `backend/`, included by this checkout's `settings.gradle.kts`. Android stays Java 17 and local-only; the backend uses Java 21 and shares `core`.

## Verified locally

```powershell
.\gradlew.bat --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' --gradle-user-home C:\Users\Milani\Documents\ChatGPT\Spendi\.gradle-review -PbackendOnly :core:test :backend:test :backend:embeddedIntegrationTest :backend:installDist --console=plain
```

**BUILD SUCCESSFUL in 2m 25s**. 13 actionable tasks: 7 executed, 6 up-to-date.

| Suite | Cases | Failures | Skipped |
| --- | ---: | ---: | ---: |
| Core financial rules (previously verified, up-to-date) | 36 | 0 | 0 |
| JWT and configuration security (AuthSpec) | 15 | 0 | 0 |
| Input / JSON validation (ModelsSpec) | 29 | 0 | 0 |
| PostgreSQL + HTTP integration (PostgresSpec) | 10 | 0 | 0 |

The integration fixture started a **real PostgreSQL 17.10 x64 process on Windows**, on a random loopback port, with its own disposable database. No existing application database or Windows service was modified. Its process was closed by fixture cleanup.

The 10 PostgreSQL specifications verify migrations/constraints; 16 concurrent retries producing one expense/key; conflicting request bodies and per-owner keys; rollback after an injected database failure; isolation of users across reads/writes/deletes/listing; stale versions and original idempotency snapshots after deletion; month/year boundaries and totals exceeding long; budget ownership/versioning; HTTP CRUD/budgets/summaries; and HTTP authentication/input/body-size/method errors.

OpenAPI and workflow YAML were parsed successfully. All 35 local OpenAPI references resolved; the contract contains 6 paths and includes the update version field. This was a syntax/reference check, not a claim of full third-party OpenAPI conformance certification.

jOOQ source generation and the runnable distribution were built successfully. Generated Java, runtime jars, test databases, reports, and local environment files are excluded from Git. Compilation has a non-fatal missing JAXB annotation warning from jOOQ's configuration types; this does not affect these tested paths. Existing Android lint warnings remain outside this milestone.

## Hosted verification

The workflow includes an independent `backend` job on Ubuntu 24.04 with a disposable PostgreSQL 17 service. It runs `:core:test :backend:test :backend:integrationTest :backend:installDist` and uploads test reports and the runnable distribution. The external PostgreSQL task fails if its required configuration is absent; it cannot silently skip integration tests.

The hosted result belongs to the actual GitHub commit/run, visible in Actions. The local results above are separate evidence and do not assert a hosted result in advance.

## Limits

A real external OIDC account/provider was not provisioned. Tests verify freshly generated RSA-signed access tokens and the same JWT verification path. Docker Compose was supplied for development PostgreSQL but was not run locally because Docker is not installed. There was no cloud deployment, performance benchmark, or Android-to-API synchronization test. Android synchronization is the next milestone, not part of this backend claim.
