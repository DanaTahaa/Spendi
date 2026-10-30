# Android and Java 21 backend connection

The Android dashboard and transaction editor support two explicit data sources: local Room storage or the HTTP API. Use **Server connection** on the dashboard to select a server and provide its bearer access token. Selecting local mode returns to the existing on-device records. Changing mode does not upload, delete, or merge those records.

Cloud mode reads transactions, monthly summaries, and budgets from PostgreSQL through the Java 17 `api-client` module. Creates reuse an idempotency key when retried; edits and deletes send the record version so stale changes are rejected. Connection failures are shown to the user. The app does not silently substitute local records.

## Windows local development through CLI

Run from the repository root with JDK 21 installed:

```powershell
.\scripts\start-local.ps1 -JavaHome 'C:\Program Files\Java\jdk-21'
```

This builds and starts a separate Java process and native PostgreSQL 17.10. Docker is not required. The API listens on `127.0.0.1:8080`, PostgreSQL on `127.0.0.1:15432`, and authenticated lifecycle control on `127.0.0.1:18080`. These fixed ports must be available. The database uses development trust authentication and is restricted to loopback; this launcher is for local development.

The database and a generated private bearer token live under `.local-spendi/`, which Git ignores. Do not share `connection.json`. Database files persist across normal stops and restarts. Keep this directory if you need the cloud-mode development records.

Build the debug app and test APK, start an Android emulator, and find its serial with `adb devices`:

```powershell
.\gradlew.bat :api-client:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
.\scripts\test-android-local.ps1 -Adb $adb -Serial emulator-5554
.\scripts\connect-android-local.ps1 -Adb $adb -Serial emulator-5554
```

The integration script installs the debug APKs without clearing application data. It tests the real Android repository against the running API, including credential encryption, transaction create/retry/read/edit/delete, stale-edit rejection, monthly summary, connection failure, and unchanged Room transaction count. It restores the prior connection preferences after the test. The connection script then selects local cloud mode and opens the app. Both scripts keep the token out of their console output. Test output is stored in the ignored `.local-spendi/android-test.log`.

The emulator reaches the host API through `http://10.0.2.2:8080`. Debug builds permit HTTP only for the specified emulator/loopback hosts. Release builds require HTTPS and omit the debug configuration receiver. That receiver requires the system `DUMP` permission, available to adb shell, and is absent from the release source set.

Stop the local service gracefully:

```powershell
.\scripts\stop-local.ps1
```

An optional `-GradleUserHome` argument on the start script selects a Gradle cache. Startup logs are inside `.local-spendi/`. If startup times out, inspect these logs before retrying; do not delete database files to resolve a port conflict.

## Authentication and current limits

Access tokens are encrypted using Android Keystore AES/GCM. Their preference file is excluded from Android backup and device transfer. Outside the local launcher, use the backend's configured issuer, audience, and HTTPS JWKS authentication settings described in [the backend guide](../backend/README.md).

This milestone provides online cloud CRUD with manual bearer-token configuration. OAuth sign-in, token refresh, offline cloud writes, an outbox, background synchronization, and importing local Room records are future work. Local mode remains usable offline. Lists load pages of 100 records and fail explicitly above the supported listing bound instead of silently displaying incomplete totals.
