# Spendi — Java Expense Tracker

Spendi is an Android application for tracking income, expenses, and monthly budgets. It stores data locally with Room and extracts financial rules into a standalone Java module, and includes a separate Java 21/PostgreSQL backend. Android-to-server synchronization is the next milestone.

## Implemented

- Create, edit, and delete income and expense transactions.
- Browse by month; filter by transaction type and search category or notes.
- Set a monthly budget and display remaining or exceeded budget.
- Summarize monthly expenses by category; retain the original category analysis tests.
- Store USD amounts as integer minor units; calculate totals with `BigDecimal`.
- Share amount validation and monthly calculations through `core`, independent of Android.
- Validate English, Arabic-Indic, and Persian digits, two-decimal precision, and integer overflow.
- Run domain tests and an Android build/lint workflow through GitHub Actions.

## Java versions

| Component | Version | Purpose |
| --- | --- | --- |
| Gradle runtime | JDK 21 | Pinned by `gradle/gradle-daemon-jvm.properties`; install locally before building |
| Android application | Java 17 source/target | Compiled and desugared for Android; minimum Android API remains 26 |
| Shared `core` | Java 17 (`--release 17`) | Language, bytecode, and Java API compatibility enforced by the compiler |
| Backend | Java 21 | PostgreSQL API, generated jOOQ types, JWT authentication, and Spock specifications |

The build uses Gradle 9.6.0, Android Gradle Plugin 9.4.1, compile/target SDK 37, and Room 2.8.4. Java 17 language support does not make every Java SE API available on Android.

## Build and verify

Install JDK 21, Android Studio / Android command-line tools, and Android SDK 37. Configure `ANDROID_HOME` or an untracked `local.properties` with `sdk.dir`. The initial build needs internet access for Gradle, plugins, and dependencies. Open this repository root in Android Studio.

Windows PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Linux / macOS:

```sh
export JAVA_HOME=/path/to/jdk-21
bash ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Domain tests only:

```sh
bash ./gradlew :core:test
```

Results:

- Domain test report: `core/build/reports/tests/test/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

The workflow in `.github/workflows/verify.yml` runs on pushes, pull requests, and manual dispatch. A local run does not prove a hosted CI run passed. Android instrumentation tests require an emulator/device and are not included in this workflow.

## Design and money contract

```text
app (Android UI + Room) ---> core (amount parsing + monthly totals)
Java 21 API + PostgreSQL ---> core
```

`AmountParser` accepts positive USD amounts with at most two decimals and converts them exactly to a signed 64-bit integer in minor units. Decimal separators are `.`, `,`, and Arabic `٫`; grouping separators and scientific notation are unsupported. For example, `25,50` means 25.50 and `1,000` is rejected for excess precision. Inputs are never silently rounded.

`MonthlyTotals` uses the full calendar month, including the year, independently of UI search/filter state. Invalid legacy dates are excluded. Income and expenses accumulate as `BigDecimal`, so adding multiple valid `long` amounts does not overflow a `long` total. Negative budget remainder means overspending.

This is an expense tracker, not a bank ledger or payment processor. The independent backend now implements authenticated APIs with PostgreSQL persistence; see [backend setup, API examples, and integration tests](backend/README.md). Android remains local-only. Multi-currency support, synchronization, Redis, API container deployment, and cloud deployment are future work. Room lifecycle and migration instrumentation coverage remains future work. See [the implementation plan](docs/REVOLUT_2027_AR.md) for the next milestones and acceptance checks.

## Author

Dana Taha — [@DanaTahaa](https://github.com/DanaTahaa)
