# MainActivity ↔ Coordinator Boundaries (Android)

> Android replacement for Electron IPC guidance.

---

## Purpose

In this project, the critical frontend boundary is not browser IPC; it is the boundary between:

1. `MainActivity` (composition/orchestration)
2. Coordinators/controllers (feature logic)
3. Repositories (data and external I/O)

---

## Boundary Rules

### 1) MainActivity should orchestrate, not own deep feature logic

MainActivity responsibilities:
- initialize binding and coordinators
- wire callbacks between modules
- handle lifecycle delegation

Examples:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

### 2) Coordinators encapsulate feature-specific UI behaviors

Examples:
- Menu behavior: `MenuFocusCoordinator`
- Playback render/overlay: `PlayerEngineCoordinator`
- Update UX: `AppUpdateCoordinator`
- Failure dialogs: `PlaybackFailureDialogCoordinator`

Paths:
- `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`
- `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`
- `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`

### 3) Repositories encapsulate transport/parsing

UI layer should call repository APIs and consume typed results/errors.

Examples:
- `ChannelRepository`
- `AppUpdateRepository`

Paths:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`

---

## Interaction Pattern

```text
MainActivity
  -> calls coordinator/repository methods
  <- receives typed callback events
  -> updates top-level UI state or routes to another coordinator
```

Concrete example paths:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

---

## Anti-Patterns

- Directly invoking repository network operations from adapter click handlers.
- Duplicating playback fallback logic in both Activity and playback package.
- Passing raw mutable UI widgets deeply across domains without clear ownership.
