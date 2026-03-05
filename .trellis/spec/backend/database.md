# Data & Persistence Guidelines

> Current project reality: no Room/SQLite schema yet. Data is primarily remote + in-memory + lightweight local storage.

---

## Current Persistence Model (As-Is)

This app currently uses:

1. **Remote playlist/update endpoints** as source of truth
2. **In-memory cache** for channel data during app process lifetime
3. **SharedPreferences** for small user state (last played channel)
4. **File cache** for downloaded APK and image cache

Examples:
- In-memory preload/cache: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- SharedPreferences state: `app/src/main/java/com/jons/iptv/MainActivity.kt`
- APK file persistence: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`
- Coil disk cache setup: `app/src/main/java/com/jons/iptv/IPTVApplication.kt`

---

## Remote Data Access Patterns

### 1) Repository owns network calls

- Use repository class to encapsulate OkHttp calls.
- UI/coordinator layers should call repository APIs, not issue HTTP requests directly.

Examples:
- `ChannelRepository.fetchChannels()`
- `AppUpdateRepository.fetchLatest()`

### 2) Proxy candidate fallback list

- For remote URLs, build candidate URLs and try sequentially.
- Track `lastError` and throw once all candidates fail.

Examples:
- `ChannelRepository.buildProxyCandidates()`
- `AppUpdateRepository.buildProxyCandidates()`

### 3) Parse + validate before returning data

- Parse raw response in repository, and fail fast when critical data is empty/invalid.

Examples:
- M3U parse failure converted to `IllegalStateException`: `ChannelRepository.fetchChannels()`
- Update metadata parsing: `AppUpdateRepository.fetchLatest()`

---

## Lightweight Local State Rules

### SharedPreferences

- Keep only compact state (e.g., last channel reference).
- Trim and validate before writing.

Example:
- `MainActivity.saveLastChannel()` / `MainActivity.loadLastChannelRef()`

### File-based APK flow

- Use app cache directory under a dedicated subfolder.
- Delete stale target file before redownload.
- Delete file when checksum verification fails.

Examples:
- `AppUpdateRepository.downloadApk()`
- `AppUpdateRepository.verifySha256()`

### In-memory channel cache

- Cache only non-empty successful channel list.
- Use synchronized lock around shared preload deferred state.

Example:
- `ChannelRepository.preloadChannels()` / `getChannels()`

---

## If Introducing Local Database Later

When adding Room/SQLite in the future, update this file and enforce:

- Single data owner per entity (remote sync vs local source of truth)
- Migration strategy documented before schema changes
- Threading policy for DB operations (no main-thread I/O)

---

## Anti-Patterns

- Storing large playlist payload directly in SharedPreferences
- Repeating URL fallback logic in UI classes
- Returning partially parsed/empty critical data as success
- Writing files outside app-scoped directories
