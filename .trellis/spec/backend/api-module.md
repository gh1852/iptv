# API Module Pattern (Kotlin Android)

> Module organization and boundaries for backend/service logic in this project.

---

## Core Principles

1. **Domain ownership**: each domain package owns its logic (`data`, `playback`, `update`, `input`).
2. **Thin orchestration**: `MainActivity` wires modules; deep logic belongs to module classes.
3. **Typed contracts**: method signatures and callback interfaces should be explicit.
4. **I/O isolation**: network/file/hash work stays inside repositories on IO dispatcher.
5. **Failure explicitness**: repository layer throws; UI boundary converts to feedback/fallback.

---

## Current Module Layout (Real)

```text
app/src/main/java/com/jons/iptv/
├── data/
│   ├── ChannelRepository.kt
│   ├── AppUpdateRepository.kt
│   ├── M3uParser.kt
│   └── models (Channel, CategoryChannels, UpdateInfo)
├── playback/
│   ├── PlaybackController.kt
│   ├── PlayerEngineCoordinator.kt
│   └── PlaybackStore.kt
├── update/
│   └── AppUpdateCoordinator.kt
├── input/
│   └── MainKeyEventRouter.kt
└── MainActivity.kt
```

---

## Responsibilities by Module

### `data/`

- fetch remote resources (playlist/update metadata)
- parse and transform payloads
- own fallback source candidate logic

Examples:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`

### `playback/`

- playback state machine/session validation
- stream fallback and retry/switch behavior
- player callback adaptation

Examples:
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`
- `app/src/main/java/com/jons/iptv/playback/PlaybackStore.kt`

### `update/`

- update check orchestration
- dialog and install flow handling
- FileProvider install intent dispatch

Example:
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

### `input/`

- central key routing policy for remote/device keys

Example:
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`

---

## Wiring Pattern

- Activity constructs module objects once.
- Activity passes only required callbacks/dependencies.
- Module APIs remain cohesive and domain-specific.

Primary orchestration path:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

---

## Anti-Patterns

- putting network + parse logic in `MainActivity`
- adapter directly touching repository/network layer
- generic `utils` bucket for domain-specific logic
- callback signatures using weakly typed maps/strings when explicit Kotlin types are possible
