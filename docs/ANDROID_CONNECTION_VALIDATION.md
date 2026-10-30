# Android connection verification

Date: 2026-10-30 (Asia/Hebron). Canonical checkout: `C:/Users/Milani/AndroidStudioProjects/Spendi3`.

## Scope

Android uses the shared Java 17 HTTP client and the existing Java 21/PostgreSQL API. Local Room storage and online server storage are selected explicitly. Existing local records are not automatically uploaded or migrated.

## Build and automated checks

```powershell
.\gradlew.bat :core:test :api-client:test :backend:test :backend:embeddedIntegrationTest :backend:prepareLocalServer :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:processReleaseMainManifest
```

The complete command finished with **BUILD SUCCESSFUL** (103 actionable tasks). All 104 JVM/PostgreSQL tests passed with zero failures, errors, or skips. The local runs use JDK 21 and a dedicated Gradle cache. PostgreSQL integration uses a real native PostgreSQL process and a disposable database; it does not use mocks or H2. The HTTP client is also tested against the real API in the PostgreSQL specification.

| Suite | Cases |
| --- | ---: |
| Core financial rules | 36 |
| Shared HTTP client | 8 |
| Backend JWT/configuration | 15 |
| Backend input/JSON validation | 29 |
| PostgreSQL and API integration | 11 |
| Android JVM tests | 5 |

## Persistent local service

The CLI launcher started the API and a separate, persistent PostgreSQL 17.10 database. An authenticated HTTP request created a temporary transaction. After graceful shutdown and a fresh server/database startup, the same transaction was retrieved with the expected amount. The temporary transaction was then deleted. The local database directory was preserved throughout.

See [connection commands and limits](ANDROID_BACKEND_CONNECTION.md). Hosted Actions results belong to the individual commit and must be checked separately from local results. CI compiles Android instrumentation tests; running them requires a device or emulator and the local API configuration.

## Android emulator and release checks

The CLI integration script passed on the existing Pixel_8_Pro emulator (Android 16 / API 36): **OK (1 test)**. It exercised Android Keystore encryption, real HTTP create/retry/list/read/edit/delete, stale-version rejection, the monthly summary, unavailable-server errors, and unchanged local Room transaction count. The test restored prior connection preferences and removed its server records.

The first attempt completed its test assertions but the instrumentation process crashed while the cold-booted emulator was reporting system-wide application-not-responding errors. After boot stabilized, a fresh run completed successfully. The CLI scripts now wait for both boot completion and the package service before installing APKs.

The generated release manifest was inspected: cleartext traffic is disabled, and the debug receiver and debug network-security configuration are absent. All five PowerShell scripts parsed successfully. The Git diff passed whitespace checks; generated files, the persistent development database, and private connection credentials are excluded from the staged change.

The CLI connection script was executed successfully: it opened the debug application and received result 1 from the protected configuration receiver. The script starts the app before sending a foreground-priority broadcast, avoiding the emulator's delayed background cold-start queue. The API remained ready after setup, with PostgreSQL data preserved.
