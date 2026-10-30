# Spendi API — Java 21

An authenticated expense and budget API backed by PostgreSQL. This module is inside the original Spendi project; Android supports explicit local Room mode or online API mode. See the [CLI local launcher and Android connection guide](../docs/ANDROID_BACKEND_CONNECTION.md) for the persistent native PostgreSQL development setup without Docker.

## Implemented

- CRUD transactions; paginated monthly listings; versioned monthly budgets and summaries.
- PostgreSQL persistence and Flyway migrations; generated jOOQ table/record types.
- RS256 JWT verification against configured HTTPS JWKS, issuer, audience, expiration, and subject.
- Ownership enforced in every database query using the verified subject. Client-supplied owner fields are rejected.
- Atomic idempotency reservations and original response snapshots for transaction creation.
- Optimistic locking for updates/deletes; stale versions return `409`.
- Exact USD calculations, including SQL sums exceeding a signed 64-bit total.
- Spock specifications covering validation, authentication, real PostgreSQL behavior, and HTTP requests.
- Java 21 virtual threads with bounded active request admission and a bounded connection pool.

The [OpenAPI contract](openapi.yaml) defines request/response fields and errors. [The design decision](../docs/ADR_002_BACKEND.md) explains the HTTP server, authentication, and consistency choices.

## Requirements

- JDK 21, available to Gradle's toolchain detection.
- PostgreSQL 17 for the running service, or Docker Compose to start the development database.
- An OIDC provider that issues RS256 **API access tokens** for the configured API audience. Local development also supports an explicit token mode, restricted to a loopback bind address.

No Android SDK is needed for backend commands when `-PbackendOnly` is supplied. Run all commands from the repository root (the folder containing `gradlew.bat` and `settings.gradle.kts`).

## Local database and application (Windows PowerShell)

Install Docker Desktop separately if using Compose, or use an existing PostgreSQL 17 installation with a dedicated `spendi` database and role. Compose is optional and was not installed by this project.

```powershell
Copy-Item backend/.env.example backend/.env
# Set SPENDI_DB_PASSWORD in backend/.env to a randomly generated password.
docker compose --env-file backend/.env -f backend/compose.yaml up -d

$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:SPENDI_DB_URL='jdbc:postgresql://127.0.0.1:5432/spendi'
$env:SPENDI_DB_USER='spendi'
$env:SPENDI_DB_PASSWORD='<the same password from backend/.env>'
$env:SPENDI_DEV_AUTH='true'
$tokenBytes=New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($tokenBytes)
$env:SPENDI_DEV_TOKEN=[BitConverter]::ToString($tokenBytes).Replace('-','')
.\gradlew.bat -PbackendOnly :backend:run
```

The process binds to `127.0.0.1:8080` by default. Flyway migrates the schema on startup without a destructive clean. Keep the development token private and retain it in the same shell when running requests. Stop the API with Ctrl+C; the shutdown hook closes the server and connection pool. `docker compose ... stop` preserves the development database volume.

The development token represents one fixed subject (`local-demo`). It is for local exercises; tests of multiple users use independently signed JWTs. Development token mode refuses `0.0.0.0` or any other non-loopback bind address.

## OIDC configuration

Remove `SPENDI_DEV_AUTH` and `SPENDI_DEV_TOKEN`. Set:

```text
SPENDI_OIDC_ISSUER=https://your-provider.example/your-issuer
SPENDI_OIDC_AUDIENCE=spendi-api
SPENDI_JWKS_URI=https://your-provider.example/your-issuer/jwks
SPENDI_DB_URL=jdbc:postgresql://your-db-host:5432/spendi?sslmode=verify-full
SPENDI_DB_USER=spendi
SPENDI_DB_PASSWORD=<injected secret>
SPENDI_HOST=127.0.0.1
SPENDI_PORT=8080
```

The configured issuer must match exactly, including a trailing slash if the provider uses one. Signing keys come only from the configured JWKS endpoint, with bounded retrieval time/size and Nimbus caching. `alg=none`, unsupported signing algorithms, missing required claims, wrong audience/issuer, expired tokens, and future `nbf` are rejected. A 30-second clock tolerance is allowed. This API does not register users, issue tokens, or implement an OAuth login screen. A real provider/account must be configured separately before deployment.

Public deployment additionally needs TLS termination, request read/write timeouts, rate limiting, secret management, database least-privilege roles, and an operational review. The included HTTP server is intended for the portfolio/local service at this milestone, not an internet-facing production deployment.

## Example API requests

In another shell, securely set `SPENDI_DEV_TOKEN` to the same local token (or use a valid access token from the configured issuer):

```powershell
$headers=@{Authorization="Bearer $env:SPENDI_DEV_TOKEN"; 'Idempotency-Key'='demo-lunch-1'}
$body=@{amount='25.50'; type='EXPENSE'; category='Food'; date='2027-07-15'; note='Lunch'} | ConvertTo-Json
$created=Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/v1/transactions -Headers $headers -ContentType application/json -Body $body
# Repeating the same request and key returns the original create response.
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/v1/transactions?month=2027-07&limit=20&offset=0' -Headers $headers
Invoke-RestMethod -Uri http://127.0.0.1:8080/v1/summaries/2027-07 -Headers $headers
```

For `PUT /v1/transactions/{id}`, send all editable fields and the current `version`. Delete with `DELETE /v1/transactions/{id}?version=1`. Create a budget with `PUT /v1/budgets/2027-07` and `{"amount":"100.00"}`; subsequent edits require the returned `version`.

Amounts in the API are JSON **strings** such as `"25.50"`; versions and pagination parameters are integers. Financial storage uses positive `BIGINT` minor units. The current currency is USD only. Monthly summaries use the full month/year and ignore list pagination. Money is never represented by `double` or silently rounded.

`Idempotency-Key` is scoped to the authenticated subject. Canonical validated fields determine request equality, so equivalent amount formatting/trimmed text can replay the same operation. A different validated body for an existing key returns `409`. Reservations and financial records commit together. Retries after an edit or delete still return the original creation snapshot, even if a subsequent GET is `404`. Keys/snapshots are retained indefinitely in this milestone; expiry, erasure, and data-retention policies are future work.

Listing uses offset pagination with deterministic ordering (`date DESC, id DESC`), bounded to 100 rows and offset 10000. Concurrent inserts/edits can shift pages; it is not a consistent export snapshot. Summary queries use one repeatable-read PostgreSQL snapshot for totals and budget.

## Tests

Unit/security specifications:

```powershell
.\gradlew.bat -PbackendOnly :backend:test
```

Self-contained integration tests using a **temporary real PostgreSQL 17.10** process (x64 Windows/Linux/macOS; native binaries download as test dependencies):

```powershell
.\gradlew.bat -PbackendOnly :backend:embeddedIntegrationTest
```

This process does not install a system service or use your application database. The fixture creates its own disposable `spendi_test` database on a random loopback port and closes it afterward. On Windows, the PostgreSQL binary may require Microsoft Visual C++ runtime libraries. On Linux, run as a normal user rather than root.

Integration tests against an existing **disposable database named `spendi_test`**:

```powershell
$env:SPENDI_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/spendi_test'
$env:SPENDI_TEST_DB_USER='spendi_test'
$env:SPENDI_TEST_DB_PASSWORD='<test-only password>'
.\gradlew.bat -PbackendOnly :backend:test :backend:integrationTest :backend:installDist
```

The integration fixture truncates its three financial tables between specifications. The external integration task fails if the database configuration is absent; it does not silently report skipped database tests as success. GitHub Actions runs this task against an isolated PostgreSQL service.

Reports: `backend/build/reports/tests/test/`, `backend/build/reports/tests/embeddedIntegrationTest/`, and `backend/build/reports/tests/integrationTest/`. Distribution: `backend/build/install/backend/`. jOOQ generated Java is under `backend/build/generated-src/jooq/`, excluded from Git and regenerated from migrations on compilation. H2 is used only by jOOQ's DDL parser for code generation, not for integration tests or runtime financial storage.

## Remaining scope

Android HTTP integration/outbox/conflict handling, production identity-provider provisioning, Redis, containerizing the API, cloud deployment, Kubernetes, performance benchmarks, and operational hardening are later milestones. This implementation contains tested behavior; it does not claim a historical TDD process or production readiness.
