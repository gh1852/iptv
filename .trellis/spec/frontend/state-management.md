# UI State Management (Android)

> How UI state is managed across Activity, coordinators, adapters, and playback state.

---

## Main Pattern: Orchestration + Coordinator Ownership

`MainActivity` should orchestrate high-level flow, while each coordinator owns a bounded state domain.

Examples:
- Activity orchestration: `app/src/main/java/com/jons/iptv/MainActivity.kt`
- Menu state: `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`
- Playback/update state: `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`, `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

---

## State Ownership Map

| State | Owner |
|---|---|
| Menu visible/focus restore | `MenuFocusCoordinator` |
| Current playing channel and stream index | `PlayerEngineCoordinator` + `PlaybackStore` |
| Back-press double-exit window | `MainKeyEventRouter` |
| Last watched channel persistence | `MainActivity` (SharedPreferences) |
| Dialog visibility/lifecycle | Corresponding dialog coordinator |

---

## Lifecycle Rules

- Initialize long-lived coordinators in `onCreate`.
- Delegate player lifecycle transitions to coordinator in `onStart/onPause/onStop/onDestroy`.
- Reset transient key routing state in `onPause`.

Example paths:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`

---

## Data-to-UI State Flow

1. `ChannelRepository.getChannels()` returns parsed channels.
2. Activity groups channels and submits to `GroupedChannelAdapter`.
3. Initial channel resolved from saved preference or first available.
4. Playback coordinator starts play flow.

Example paths:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`

---

## UI Persistence Rules

- Persist only lightweight user context (e.g., last channel name/category).
- Keep transient visual state in memory (overlay visibility, pending menu focus, current dialog).

Example:
- `MainActivity.saveLastChannel()` / `loadLastChannelRef()` in `app/src/main/java/com/jons/iptv/MainActivity.kt`

---

## Anti-Patterns

- Storing all UI feature state directly in `MainActivity` fields.
- Duplicating the same state across multiple coordinators without clear source of truth.
- Coupling adapter state directly to repository/network layer.
