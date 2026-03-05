# Logging Guidelines

> Logging conventions for current Android IPTV project.

---

## Current Logging Stack

- Primary logger: `android.util.Log`
- Playback domain wrapper: `PlaybackLogger`
- Tag injection from orchestrator/coordinator constructors (e.g., `logTag`)

Examples:
- Wrapper: `app/src/main/java/com/jons/iptv/playback/PlaybackLogger.kt`
- Activity/coordinator tag usage:
  - `app/src/main/java/com/jons/iptv/MainActivity.kt`
  - `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`
  - `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

---

## Log Level Semantics

| Level | Use case |
|---|---|
| `Log.e` | operation failed and needs attention |
| `Log.w` | degraded path/fallback happened |
| `Log.i` | meaningful state transitions |
| `Log.d` | detailed debug traces |

Observed examples:
- Warning on update check failure: `AppUpdateCoordinator.checkUpdateSilently()`
- Info for playback state transitions: `PlayerEngineCoordinator` listener callbacks
- Debug for play requests/media item creation: `PlayerEngineCoordinator.playChannel()`

---

## Message Style

Use key-value style strings to simplify search/filtering:

- Good: `"Playback state changed state=$playbackState, channel=${currentChannel?.name}, index=$currentStreamIndex"`
- Good: `"Switch source channel=${channel.name}, from=$currentIndex, to=$nextIndex, reason=$reason"`

This style is used in:
- `PlayerEngineCoordinator.kt`
- `PlaybackController.kt`

---

## Practical Rules

1. Include channel name/index/reason for playback-related logs.
2. Include throwable in error/warn logs when possible.
3. Keep high-frequency logs at `d`, important lifecycle/status at `i`.
4. Keep consistent tag ownership per module/component.

---

## Anti-Patterns

- Mixing unrelated module logs under one ambiguous tag
- Logging only generic text like `"failed"` without context fields
- Using `Log.e` for expected fallback paths where `Log.w` is enough
- Exposing sensitive payloads or full remote content in logs
