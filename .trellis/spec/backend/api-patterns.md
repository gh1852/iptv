# API & Service Patterns (Kotlin Android)

> Common implementation patterns based on the current codebase.

---

## Pattern 1: Multi-candidate fetch fallback

Use candidate URL lists and iterate until success.

Example:
- `ChannelRepository.buildProxyCandidates()` + `fetchChannels()`
  (`app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`)

Key points:
- normalize source URL
- keep ordered candidate list
- preserve last error and throw once exhausted

---

## Pattern 2: Parse + validate before returning

Repository should reject empty/invalid critical payload.

Examples:
- playlist parse + non-empty check in `ChannelRepository.fetchChannels()`
- update metadata validation in `AppUpdateRepository.fetchLatest()`

Paths:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`

---

## Pattern 3: IO dispatcher confinement

Run network/file/hash operations in `withContext(Dispatchers.IO)`.

Examples:
- `ChannelRepository.fetchChannels()`
- `AppUpdateRepository.fetchLatest()/downloadApk()/verifySha256()`

---

## Pattern 4: UI boundary `runCatching`

At coordinator/activity boundary, contain failures and convert to UX feedback.

Examples:
- `MainActivity.loadChannels()`
- `AppUpdateCoordinator.checkUpdateSilently()`
- `AppUpdateCoordinator.downloadAndInstallUpdate()`

---

## Pattern 5: Playback failover instead of hard stop

Playback errors should attempt source switch first, then fail explicitly.

Examples:
- `PlaybackController.onPlayerError()`
- `PlaybackController.switchToNextOrFail()`

Path:
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

---

## Pattern 6: Manifest-declared install contracts

Install/update behavior depends on manifest + provider contracts.

Examples:
- `REQUEST_INSTALL_PACKAGES` and FileProvider in manifest
- coordinator `installDownloadedApk()` FileProvider URI usage

Paths:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

---

## Anti-Patterns

- silent catch without log or user feedback
- duplicating proxy fallback logic across unrelated classes
- returning success on empty critical data
- main-thread blocking I/O hidden in UI callbacks
