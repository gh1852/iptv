# Logging Guidelines

> How logging is done in this project.

---

## Overview

Logging uses Android `Log` APIs (`Log.d/i/w/e`) with explicit tags.

There is a small wrapper class used in playback domain, while other modules may call `Log` directly.

---

## Log Levels

Observed usage:

- `Log.d`: diagnostic flow/state details
  - `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt:123`
- `Log.i`: notable non-error runtime information
  - `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt:64`
- `Log.w`: recoverable or expected failures
  - `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt:48`
- `Log.e`: unrecoverable/critical failure with throwable
  - `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt:290`

---

## Structured Logging

Current pattern is message-based logging (plain text with contextual fields in message body).

- Playback logger wrapper: `app/src/main/java/com/jons/iptv/playback/PlaybackLogger.kt`
- Main activity tag constant pattern: `app/src/main/java/com/jons/iptv/MainActivity.kt:34`

Recommended consistency based on existing code:

- Include runtime context in message (`channel`, `index`, `reason`, URL/phase)
- Always pass throwable for `Log.e` and warning paths that include exceptions

---

## What to Log

- Playback state transitions and recovery reasons
- Data loading failures and update flow failures
- Decoder/player recreation outcomes

Examples:

- Decoder recovery warning: `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt:282`
- Update flow warning: `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt:142`

---

## What NOT to Log

- Sensitive tokens, credentials, or private user data.
- Full remote payload bodies unless strictly needed for troubleshooting.
- Repeated noisy logs inside hot loops without clear diagnostic value.
