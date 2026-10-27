# Java 17 / JDK 21 verification

Verified on 2026-10-27, Asia/Hebron.

## Changes verified

- Android Java source and target compatibility changed from 11 to 17.
- Gradle daemon requirement changed from JDK 25 to JDK 21.
- New pure Java `core` module uses `--release 17` and is consumed by Android.
- Amount entry for both transactions and budgets uses the shared parser.
- Monthly dashboard and budget calculations use the shared monthly accumulator.

## Command and result

From the repository root (`source/` in the enclosing review workspace), with JDK 21 and the existing Android SDK configured:

```powershell
.\gradlew.bat --no-daemon --gradle-user-home ..\.gradle-review :core:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Result: **BUILD SUCCESSFUL in 1m 52s**, 54 actionable tasks: 32 executed, 22 up-to-date.

The initial restricted-process attempt could not access the installed SDK. The successful run used a fresh Gradle process with SDK access. `ANDROID_USER_HOME` was redirected to an untracked directory in the enclosing workspace; no SDK permissions or project SDK versions were changed.

| Suite | Tests | Failures | Errors |
| --- | ---: | ---: | ---: |
| AmountParserTest | 10 | 0 | 0 |
| InvalidAmountTest | 19 | 0 | 0 |
| MonthlyTotalsTest | 7 | 0 | 0 |

The app's existing arithmetic example test also passed; it is not counted among the 36 domain tests and is not evidence of UI or persistence correctness.

Lint completed successfully. The SARIF report contains 35 findings across 7 warning rule types; this is not a warning-free result.

Outputs, generated locally and excluded from Git:

- `core/build/reports/tests/test/index.html`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.sarif`
- `app/build/outputs/apk/debug/app-debug.apk`

`git diff --check` passed. The GitHub Actions workflow has been added but has not been executed on GitHub. No changes were pushed.

## Limits

No emulator/device UI testing, instrumentation tests, Room migration tests, rotation/process-death tests, benchmark, backend API, PostgreSQL integration, Redis, Docker, Kubernetes, or GCP deployment was performed. This milestone establishes Java compatibility and testable financial rules; subsequent milestones are described in [the roadmap](REVOLUT_2027_AR.md).
