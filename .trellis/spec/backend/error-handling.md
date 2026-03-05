# Error Handling Guidelines

> Error-handling patterns derived from current Kotlin Android codebase.

---

## Core Strategy by Layer

| Layer | Primary Strategy | Typical Action |
|---|---|---|
| Data repository (`data/`) | Fail fast with exceptions | `throw IllegalStateException(...)` |
| Coordinator/UI orchestration | Contain failure at boundary | `runCatching { ... }.onFailure { ... }` |
| Playback state machine | Guard + explicit failure state | switch source or mark failed |

---

## Pattern 1: Repository failures should be explicit

When network/parse/checksum fails, repository methods throw with context-rich message.

Examples:
- Playlist fetch HTTP/parse/empty error: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- Update metadata fetch failure: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`
- APK checksum mismatch: `AppUpdateRepository.verifySha256()`

Why: caller can decide UX fallback without guessing if result is trustworthy.

---

## Pattern 2: UI boundary uses `runCatching`

UI-facing flows should not crash the app on expected operational failures.

Examples:
- Channel loading: `MainActivity.loadChannels()`
- Silent update check: `AppUpdateCoordinator.checkUpdateSilently()`
- Download/install flow: `AppUpdateCoordinator.downloadAndInstallUpdate()`

Typical behavior:
- Log warning/error
- Show user-facing toast/message
- Fall back to safe behavior (e.g., open update URL)

---

## Pattern 3: Playback fallback over immediate hard-fail

Playback errors should first attempt next source when possible.

Examples:
- Player error handling + source switch: `PlaybackController.onPlayerError()`
- Timeout-based source switch: `PlaybackController.scheduleFirstFrameTimeout()`
- Exhausted all sources callback: `PlaybackController.switchToNextOrFail()`

---

## Pattern 4: Guard clauses for invalid input/state

Use early return for invalid state to avoid cascading faults.

Examples:
- Invalid play request index/empty stream list: `PlaybackController.play()`
- Empty/blank values in parser and preferences methods:
  - `M3uParser.parsePlainEntry()`
  - `MainActivity.saveLastChannel()`

---

## User-Facing Error Messaging

- Do not expose raw stack traces to user.
- Map technical failures to stable string resources/toasts.

Examples:
- `R.string.load_failed` in `MainActivity`
- `R.string.update_download_failed` / `R.string.update_verification_failed` in `AppUpdateCoordinator`

---

## Anti-Patterns

- Swallowing errors silently without log or UX feedback
- Returning success with invalid/empty critical payload
- Throwing deep technical messages directly to end user
- Performing network/file operations on main thread then masking ANR as generic failure
