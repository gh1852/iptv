# Coroutine & Callback Interaction Guidelines (Android)

> Android equivalent of frontend "hooks" patterns.

---

## Pattern 1: Suspend + runCatching for async UI actions

Use `lifecycleScope.launch` in Activity and wrap repository calls with `runCatching`.

Example:
- `app/src/main/java/com/jons/iptv/MainActivity.kt` (`loadChannels()`)
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

Rule:
1. start UI loading state
2. run suspend work
3. update UI on success
4. report + recover on failure
5. clear loading state in `finally` equivalent

---

## Pattern 2: Keep network/file/hash on IO dispatcher

Heavy operations must use `withContext(Dispatchers.IO)` in repository layer.

Examples:
- `ChannelRepository.fetchChannels()`
- `AppUpdateRepository.fetchLatest()`
- `AppUpdateRepository.downloadApk()`
- `AppUpdateRepository.verifySha256()`

---

## Pattern 3: Callback boundaries should be thin

Coordinator callbacks should pass typed events/actions, not raw transport details.

Examples:
- Playback events callback interface: `PlaybackController.Callback`
- Wiring point in `MainActivity`

File paths:
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

---

## Pattern 4: Single-owner delayed tasks

For delayed UI transitions, keep one runnable per concern and cancel before re-post.

Example:
- Overlay hide runnable in `PlayerEngineCoordinator`
  (`cancelPendingOverlayHide()` + `scheduleOverlayHide()`)

---

## Anti-Patterns

- Launching coroutines from adapters for business logic.
- Running blocking hash/download directly on main thread.
- Nested callback pyramids when suspend + coordinator abstraction is available.
