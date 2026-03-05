# Directory Structure

> Project-specific directory conventions for this Android IPTV app (Kotlin + XML + Media3).

---

## High-Level Layout

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/jons/iptv/
│       │   ├── MainActivity.kt
│       │   ├── IPTVApplication.kt
│       │   ├── data/
│       │   ├── playback/
│       │   ├── update/
│       │   ├── input/
│       │   └── ui/
│       └── res/
│           ├── layout/
│           ├── drawable/
│           ├── values/
│           └── xml/
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Package Responsibilities

### `com.jons.iptv` (app entry/orchestration)

- `MainActivity.kt`: screen orchestration and lifecycle wiring
- `IPTVApplication.kt`: app-level image loader and cache setup

Examples:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/IPTVApplication.kt`

### `com.jons.iptv.data` (data source + parsing + models)

- Data models: `Channel`, `CategoryChannels`, `UpdateInfo`
- Remote fetch and transformation: `ChannelRepository`, `AppUpdateRepository`
- M3U parsing: `M3uParser`

Examples:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/data/M3uParser.kt`
- `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`

### `com.jons.iptv.playback` (player domain)

- Playback state machine and session: `PlaybackStore`, `PlaybackController`
- Media3 bridge: `ExoPlayerAdapter`, `PlayerEngineCoordinator`
- Logging wrapper for playback: `PlaybackLogger`

Examples:
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`
- `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`

### `com.jons.iptv.update` (update workflow)

- Update check, dialog interaction, APK install flow

Example:
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

### `com.jons.iptv.ui` and subpackages (view-layer helpers)

- RecyclerView adapters and UI-specific coordinators
- Dialog and menu behavior separated from `MainActivity`

Examples:
- `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`
- `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`
- `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`

### `com.jons.iptv.input` (input routing)

- Dedicated key-event routing logic

Example:
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`

---

## Resource Conventions

- `res/layout/`: activity and item layouts
- `res/drawable/`: selectors, dialog backgrounds, icons/placeholders
- `res/values/`: strings, dimensions, themes, colors
- `res/xml/file_paths.xml`: FileProvider paths for APK install flow

Examples:
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/item_channel.xml`
- `app/src/main/res/xml/file_paths.xml`

---

## Practical Rules

1. Keep `MainActivity` focused on orchestration; move domain logic into package-specific coordinators/repositories.
2. Put stream/update/network parsing code in `data/`, not in UI classes.
3. Keep playback behavior in `playback/` package; UI should trigger playback actions, not own playback internals.
4. Prefer adding a focused coordinator/router before growing `MainActivity` further.

---

## Anti-Patterns

- Putting HTTP parsing/downloading directly in `MainActivity`
- Mixing key-event routing and playback fallback logic in the same UI class
- Creating generic `utils/` dumping ground instead of domain packages (`data`, `playback`, `update`, etc.)
- Adding new feature files into root package when a domain package already exists
