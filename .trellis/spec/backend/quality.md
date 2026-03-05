# Code Quality Guidelines

> Quality baseline for this Kotlin Android project.

---

## Build & Toolchain Baseline

- Kotlin Android app module with JVM target 17
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 21`
- ViewBinding enabled

Reference:
- `app/build.gradle.kts`

---

## Architecture & Separation Rules

1. `MainActivity` orchestrates; domain logic lives in dedicated classes.
2. Keep networking/parsing in `data/` repositories.
3. Keep playback state/fallback logic in `playback/` package.
4. Keep key-event policy isolated in input router/coordinator classes.

Examples:
- Orchestration: `app/src/main/java/com/jons/iptv/MainActivity.kt`
- Repository boundary: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- Playback boundary: `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`
- Input boundary: `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`

---

## Concurrency & Threading Rules

- Network/file/hash operations must run on IO dispatcher.
- UI updates remain in Activity/coordinator on main-safe paths.
- Shared mutable preload/cache state must be synchronized or volatile guarded.

Examples:
- `withContext(Dispatchers.IO)` in repositories:
  - `ChannelRepository.fetchChannels()`
  - `AppUpdateRepository.fetchLatest()/downloadApk()/verifySha256()`
- Lock/volatile usage in `ChannelRepository` preload cache state

---

## Constants & Configuration

- Magic numbers should be promoted to `companion object` constants.
- Keep timeout/buffer/ui-delay values centralized in owning class.

Examples:
- `MainActivity` constants (`BACK_PRESS_EXIT_WINDOW_MS`, splash/menu timing)
- `PlayerEngineCoordinator` constants (buffer/network/overlay delays)
- `PlaybackController` constants (first-frame timeout/switch gap)

---

## Defensive Coding Expectations

- Prefer guard clauses and early return for invalid inputs.
- Keep fallback paths explicit and testable by reading code flow.
- Do not treat empty critical payload as success.

Examples:
- Guard patterns in `M3uParser` and `PlaybackController`
- `IllegalStateException` on invalid remote responses in repositories

---

## Review Checklist (Before Merge)

- [ ] `./gradlew assembleDebug` passes
- [ ] No network or file I/O on main thread
- [ ] New logic placed in correct package (not overloading `MainActivity`)
- [ ] Error paths provide either log context or user-facing feedback
- [ ] New constants extracted to companion object when reused/semantic

If AI environment lacks build tools/SDK, AI must provide the commands for manual execution and continue only after human reports the check results.

---

## Testing Reality (Current Project)

Current repository does not yet include active unit/instrumentation test suites under `app/src/test` or `app/src/androidTest`.

Current expectation:
- At minimum, ensure build passes and run manual validation for touched flows.
- If adding non-trivial logic (parsing/state machine/routing), prioritize introducing unit tests in follow-up work.

---

## Anti-Patterns

- Growing `MainActivity` with new network/parsing/business branches
- Duplicating proxy fallback logic across multiple classes instead of extracting shared behavior when repetition becomes clear
- Introducing hardcoded literals repeatedly instead of named constants
- Catching exceptions only to ignore them silently
