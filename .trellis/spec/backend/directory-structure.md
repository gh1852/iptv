# Directory Structure

> How backend-side logic is organized in this project.

---

## Overview

This repository is an Android app, not a standalone server backend. In this project, “backend” work maps to data access, parsing, domain coordination, and update/download workflows under `app/src/main/java/com/jons/iptv/`.

Current organization is feature/domain oriented, with clear package boundaries for data, playback, update, input routing, and UI coordination.

---

## Directory Layout

```text
app/src/main/java/com/jons/iptv/
├── MainActivity.kt                 # App entry and top-level orchestration
├── IPTVApplication.kt              # Application-level initialization
├── data/                           # Data fetch/parse/model layer
│   ├── ChannelRepository.kt
│   ├── AppUpdateRepository.kt
│   ├── M3uParser.kt
│   ├── Channel.kt
│   ├── CategoryChannels.kt
│   └── UpdateInfo.kt
├── playback/                       # Playback engine/state/recovery/logging
│   ├── PlayerEngineCoordinator.kt
│   ├── PlaybackController.kt
│   ├── PlaybackStore.kt
│   ├── ExoPlayerAdapter.kt
│   └── PlaybackLogger.kt
├── update/                         # App update orchestration
│   └── AppUpdateCoordinator.kt
├── input/                          # Key event routing
│   └── MainKeyEventRouter.kt
└── ui/                             # UI adapters and UI coordinators
    ├── GroupedChannelAdapter.kt
    ├── ChannelAdapter.kt
    ├── CategoryAdapter.kt
    ├── dialog/
    │   ├── PlaybackFailureDialogCoordinator.kt
    │   └── CctvStyleDialogAnimator.kt
    └── menu/
        └── MenuFocusCoordinator.kt
```

---

## Module Organization

- `data/`: Network requests, parsing, and data models.
  - Example: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
  - Example: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`
- `playback/`: Player orchestration, state transitions, playback error recovery.
  - Example: `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`
- `update/`: Update check/download/install flow coordination.
  - Example: `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`
- `input/`: Keyboard/remote event behavior.
  - Example: `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`
- `ui/`: Adapter and interaction coordination classes for views.
  - Example: `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`

---

## Naming Conventions

Observed naming patterns:

- `*Repository` for data-fetching/data-source classes.
- `*Coordinator` for orchestration across components.
- `*Adapter` for RecyclerView/UI adapter classes.
- `*Store` for in-memory playback state holder.
- `*Router` for input event routing.

---

## Examples

Good references for new code placement:

- Data/network + parsing: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- Multi-step orchestration: `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`
- Playback domain composition: `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`

Anti-patterns to avoid:

- Putting network/data parsing logic directly in `MainActivity`.
- Mixing playback state mutations into UI adapters.
- Adding unrelated utility classes at package root when a domain package exists.
