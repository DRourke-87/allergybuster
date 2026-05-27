# AllergyBuster — AI Assistant Reference

## What this project is

AllergyBuster is a Kotlin Multiplatform (KMP) allergy / pollen forecast app for Android and iOS.
It fetches hourly pollen counts from the Open-Meteo air-quality API, computes a daily risk level
using a per-user Bayesian sensitivity model, and presents the result with a morning notification,
a home-screen widget, and three Compose / SwiftUI screens.

Current version: `1.2.4` (versionCode `7`). Package: `com.tarnlabs.allergybuster`.

---

## Repository layout

```
AllergyBuster/
├── shared/              # KMP module — domain, data, use cases
│   └── src/
│       ├── commonMain/  # Pure Kotlin; compiles to Android + iOS
│       ├── androidMain/ # Android-specific expect/actual impls
│       └── iosMain/     # iOS-specific expect/actual impls
├── app/                 # Android application module (Compose + Hilt)
├── iosApp/              # iOS Xcode project (SwiftUI, consumes shared XCFramework)
├── docs/                # GitHub Pages site (privacy policy, screenshots)
├── playstore/           # Play Console copy and graphics
├── tools/               # Supporting scripts (play-console-dashboard)
├── codemagic.yaml       # iOS CI/CD (Codemagic)
├── RELEASING.md         # Android release runbook
└── gradle/libs.versions.toml  # Gradle version catalogue
```

---

## Architecture

The project follows a strict three-layer architecture inside `shared/`:

```
UI  ──▶  ViewModel  ──▶  UseCase  ──▶  Repository  ──▶  (SQLDelight | Ktor)
```

- **Domain layer** (`shared/…/domain/`): pure Kotlin, no platform imports.
  - `model/` — immutable data classes (`DailyPollen`, `UserWeights`, `Recommendation`, etc.)
  - `engine/` — stateless objects: `BayesianUpdater`, `RecommendationEngine`
  - `usecase/` — suspending/operator-fun wrappers: `ComputeRecommendationUseCase`,
    `ApplyDailyBayesianUseCase`, `SubmitFeedbackUseCase`
- **Data layer** (`shared/…/data/`):
  - `remote/` — `OpenMeteoApiClient` (Ktor), `AirQualityResponse` DTO + mapper
  - `local/db/` — SQLDelight generated database (`AllergyBusterDatabase`)
  - `repository/` — `PollenRepository`, `FeedbackRepository`, `RecommendationRepository`
- **Platform layers**:
  - Android: Jetpack Compose screens + Hilt ViewModels inside `app/`
  - iOS: SwiftUI screens + `@ObservableObject` view models inside `iosApp/`; dependency
    graph wired manually in `ServiceContainer`

---

## Key domain concepts

### Pollen types

Six types tracked by `PollenType` enum, each carrying API field name, display name,
low/moderate/high thresholds (grains/m³), emoji icon, seasonality and cross-reaction notes.

`PollenType.normalise(raw: Float)` performs piecewise-linear normalisation → 0.0–3.0.

### Recommendation levels

| Level | Meaning |
|-------|---------|
| 0 | Low pollen risk |
| 1 | Moderate pollen risk |
| 2 | High pollen risk |

`RecommendationEngine.computeScore()` returns `max(norm × weight)` across all six types.
`scoreToLevel()` maps: `<1.0 → 0`, `<2.0 → 1`, `≥2.0 → 2`.

### Bayesian weight update

`BayesianUpdater.updateWeights()` adjusts per-type sensitivity weights when the user
submits daily feedback (0=fine, 1=mild, 2=severe) vs. the model's predicted level.
Constants: `LEARNING_RATE=0.15`, weight range `[0.1, 5.0]`, only updates types with
`norm ≥ 0.5` (non-trivial pollen).  Applied retroactively once per day by
`ApplyDailyBayesianUseCase`, triggered from `PollenFetchWorker`.

### Learning progress

`LearningProgress` (Android) / `LearningProgressState` (iOS) — displayed as a growing
tree animation over the first 30 days.  Progress = average of (days elapsed / 30) and
(feedback submissions / 30).

---

## Data flow (daily fetch cycle)

```
PollenFetchWorker.doWork()
  1. Resolve GPS location (>10 km change → notify user)
  2. fetchAndStore(lat, lon) → OpenMeteoApiClient → AirQualityResponse.toDailyPollen()
  3. ComputeRecommendationUseCase → RecommendationEngine → Recommendation saved
  4. ApplyDailyBayesianUseCase → BayesianUpdater.updateWeights() for prior days
  5. pruneOldForecasts() (keeps 14 days of raw pollen)
  6. AllergyWidgetReceiver.updateWidget()
  7. NotificationHelper.postDailyNotification() + postPersistentNotification()
```

Stale fallback: if network fails, use cached today → cached most recent (isStale=true) →
retry up to 3 times before `Result.failure()`.

---

## Local database (SQLDelight)

Database name: `AllergyBusterDatabase`. Four tables defined in `.sq` files under
`shared/src/commonMain/sqldelight/…/data/local/db/`:

| Table | Key | Notes |
|-------|-----|-------|
| `pollen_forecast` | `date TEXT PK` | Pruned to 14 days |
| `recommendation` | `date TEXT PK` | Kept 90 days; `topContributors` is JSON array |
| `daily_feedback` | `date TEXT PK` | `bayesianApplied INTEGER` flag |
| `user_weights` | `id=1` singleton | All six pollen type weights |

Platform drivers: `AndroidSqliteDriver` (Android), `NativeSqliteDriver` (iOS).
SKIE (`co.touchlab.skie`) bridges Kotlin coroutine flows to Swift `AsyncSequence`.

### Migration note

The app shipped v1 with Room and migrated to SQLDelight in v1.2.x.
`RoomToSqlDelightMigrator` runs once on first launch after upgrade, copying the old
`allergy.db` tables into SQLDelight then deleting the old file. The `room_migration_done`
DataStore flag guards the run-once behaviour. Do not remove this migrator — users on
old versions still need it.

---

## Android module (`app/`)

- **DI**: Hilt, single `SingletonComponent`. All shared repositories/use cases provided
  in `SharedModule`. `WorkManager` uses `HiltWorkerFactory` (default initialiser removed
  from manifest).
- **Navigation**: `NavHost` with string routes `"home"`, `"history"`, `"settings"`.
  Bottom nav bar in `MainActivity`.
- **Widget**: Glance AppWidget (`AllergyWidget`). Updated by `AllergyWidgetReceiver.updateWidget()`
  after each fetch. Reads directly from `recommendationRepository` via the application object.
- **Notifications**: Two channels — `daily_pollen` (default importance, dismissable, with
  Fine/Mild/Severe action buttons) and `pollen_status` (low importance, ongoing, silent).
  `FeedbackActionReceiver` handles notification action intents and saves feedback.
- **Settings**: `AppSettingsDataStore` (Jetpack DataStore Preferences) for notification time,
  onboarding flag, location, persistent notification toggle, migration flags.
- **Background scheduling**: `PollenFetchWorker` is a `CoroutineWorker`; enqueued as periodic
  work (name `"pollen_fetch"`) and also triggered immediately on demand via
  `enqueueImmediatePollenFetch()`.

### AppCompat exclusion

`androidx.appcompat` is excluded globally via `configurations.all { exclude ... }` in
`app/build.gradle.kts`. This app uses `ComponentActivity` + Material3 and has zero
AppCompat API usage. Do not add AppCompat back.

---

## iOS module (`iosApp/`)

- `ServiceContainer` is the single dependency wiring point (manual DI, no Hilt).
- View models conform to `ObservableObject`, observe shared repos via Swift `AsyncSequence`
  (bridged by SKIE).
- `BackgroundRefreshScheduler` handles `BGAppRefreshTask`.
- `AllergyBusterWidget` extension shares the same App Group (`group.com.tarnlabs.allergybuster`)
  for UserDefaults (learning started-at timestamp).
- iOS builds do **not** run Gradle; they consume `shared.xcframework` generated by
  `./gradlew :shared:assembleXCFramework`.

---

## Build commands

```sh
# Android debug APK
./gradlew :app:assembleDebug

# Android release AAB (requires keystore env vars or keystore.properties)
./gradlew :app:bundleRelease

# All unit tests
./gradlew :app:test

# Specific test class
./gradlew :app:testDebugUnitTest --tests "com.tarnlabs.allergybuster.engine.BayesianUpdaterTest"

# iOS XCFramework (needed before Xcode build)
./gradlew :shared:assembleXCFramework

# Lint
./gradlew :app:lint
```

Java/Kotlin target: **JVM 17**. `minSdk = 26`, `compileSdk = 35`.

---

## Versioning policy

### Scheme

```
MAJOR.MINOR.PATCH   e.g. 1.2.4
```

| Change type | Which part increments | Example |
|-------------|----------------------|---------|
| Bug fix / crash fix / small tweak | PATCH (`x.x.Y`) | `1.2.4 → 1.2.5` |
| New user-visible feature | MINOR, reset PATCH to 0 (`x.Y.0`) | `1.2.4 → 1.3.0` |
| Breaking / major redesign | MAJOR (rare, discuss first) | `1.2.4 → 2.0.0` |

### When to bump

**Bump the version on the feature/fix branch before merging**, not after.
The version in `main` should always reflect what is (or is about to be) shipped.

### Files to update

Two files must be kept in sync:

1. **`app/build.gradle.kts`** — `versionName` field (Android):
   ```kotlin
   versionName = "1.2.5"   // ← update this
   ```
   Leave `versionCode` alone — CI sets it automatically to the GitHub Actions run number.

2. **`iosApp/AllergyBuster.xcodeproj/project.pbxproj`** — `MARKETING_VERSION` field (iOS).
   It appears four times (Debug + Release × app target + widget target); update all four:
   ```
   MARKETING_VERSION = 1.2.5;
   ```
   Leave `CURRENT_PROJECT_VERSION` alone — Codemagic manages it.

### Checklist for every branch

Before pushing a branch that is ready to merge:

- [ ] Decide: bug fix (patch) or new feature (minor)?
- [ ] Update `versionName` in `app/build.gradle.kts`
- [ ] Update all four `MARKETING_VERSION` entries in `project.pbxproj`
- [ ] Commit the version bump with message `Bump version to X.Y.Z`

---

## Release workflow

### Android

1. Merge the branch (version already bumped on the branch).
2. Tag from `main`: `git tag vX.Y.Z && git push origin vX.Y.Z`
3. GitHub Actions `Release AAB` workflow fires, produces a signed AAB artifact.
4. Upload AAB manually to Play Console (no Play API automation yet).

Signing vars (CI secrets or `keystore.properties`):
`ALLERGYBUSTER_KEYSTORE_PATH`, `ALLERGYBUSTER_KEYSTORE_PASSWORD`,
`ALLERGYBUSTER_KEY_ALIAS`, `ALLERGYBUSTER_KEY_PASSWORD`.

### iOS

CI: Codemagic (`codemagic.yaml`).
- `ios-simulator` workflow: triggers on push to `main` or `claude/*` branches.
- `ios-release` workflow: triggers on `v*.*.*` tags, archives and submits to TestFlight.

---

## Testing

Unit tests live in `app/src/test/`. Key test files:

| File | What it tests |
|------|--------------|
| `BayesianUpdaterTest.kt` | Weight update direction, clamping, trivial-pollen guard |
| `RecommendationEngineTest.kt` | Score computation, level thresholds, contribution ordering |
| `AppUpgradeManagerTest.kt` | Transition detection, version bumping |
| `RoomToSqlDelightMigratorJsonTest.kt` | `sanitizeContributorsJson` edge cases |

Tests use JUnit4 + MockK + Turbine + `kotlinx-coroutines-test`. No instrumented tests.

When writing new tests follow the existing backtick naming convention:
```kotlin
@Test fun `description of behaviour under test`() { ... }
```

---

## Code conventions

- **Kotlin**: standard style, no trailing blank lines in functions, no unnecessary comments.
- **Repositories**: all public query methods are `suspend fun` or return `Flow<…>`.
  All `Flow` operators use `.catch { emit(emptyList()) }` so the UI never gets stuck.
- **SQLDelight**: use `REAL` (not `FLOAT`) for pollen values; use `INTEGER` for booleans
  (0/1). Generated type aliases are `Long`/`Double` — always `.toFloat()` / `.toInt()` when
  mapping to domain types.
- **DI**: add new singletons to `SharedModule`; do not use field injection in non-Android
  classes.
- **No AppCompat**: do not import or depend on `androidx.appcompat`.
- **No data deletion on upgrade**: repositories have flow-level resilience (`runCatching`
  per row). Future `AppUpgradeManager` migrations must only delete data as a last resort
  and must be guarded by a precise `(from, to)` version range check.
- **Pollen score**: always use `max(norm × weight)` across types, not an average.
  (Averaging was a bug fixed in v1.2.3.)
- **iOS Swift**: view models are `@MainActor final class` conforming to `ObservableObject`;
  async observation via `for await` over SKIE-bridged flows.

---

## CI branch patterns

Codemagic `ios-simulator` workflow triggers on:
- `main`
- `claude/*`

Feature branches should follow `claude/<description>` naming to get automatic iOS CI.
