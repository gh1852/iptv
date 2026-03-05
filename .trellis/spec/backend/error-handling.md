# Error Handling

> How errors are handled in this project.

---

## Overview

Error handling is based on Kotlin `runCatching` and explicit failure propagation.

Core style in data layer:

- Wrap IO/network/parse operations in `runCatching`
- Use `onSuccess`/`onFailure` to control retry flow
- Throw `IllegalStateException` with clear message when all attempts fail
- Preserve root cause by passing `Throwable` as `cause`

---

## Error Types

Current dominant error type is `IllegalStateException` for business/flow failures:

- Preload fetch failure wrapping cause: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt:81`
- All proxy candidates failed (playlist): `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt:126`
- All proxy candidates failed (update metadata): `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:81`
- SHA256 verify failure: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:143`

---

## Error Handling Patterns

### 1) `runCatching` around risky operations

- Playlist fetch/parse: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt:104`
- Update metadata fetch/parse: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:56`
- APK download: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:98`

### 2) Retry across candidate URLs and keep `lastError`

- Playlist retries: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt:95`
- Metadata retries: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:47`
- APK retries: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:90`

### 3) Handle failures at orchestration/UI boundary

- Channel load failure -> user toast: `app/src/main/java/com/jons/iptv/MainActivity.kt:211`
- Update check/install failure logs: `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt:48`

---

## API Error Responses

This app is a client application and does not expose server APIs in this repository.

No HTTP API response schema is defined here.

---

## Common Mistakes

- Swallowing exceptions without preserving cause.
- Throwing vague messages that hide which stage failed (fetch/parse/verify).
- Handling recoverable data-source failures only in UI instead of repository retry logic.
