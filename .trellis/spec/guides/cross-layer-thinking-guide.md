# Cross-Layer Thinking Guide (Android)

> Purpose: pre-implementation checklist for features spanning multiple app layers.

---

## When to Use

Use this guide when a change touches 3+ layers, such as:

- UI + input routing + playback
- repository fetch/parse + UI presentation
- update/install flow + manifest/platform contracts

---

## Layer Model (Current Project)

```text
UI Layer
  (MainActivity, adapters, dialog/menu coordinators, XML)
        |
State/Control Layer
  (PlaybackStore, coordinators, key router)
        |
Data/Service Layer
  (ChannelRepository, AppUpdateRepository, parser)
        |
Platform Boundary
  (AndroidManifest permissions, FileProvider, Intents)
        |
Persistence
  (SharedPreferences + cache files)
```

Representative files:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/AndroidManifest.xml`

---

## Pre-Implementation Checklist

### 1) Layer Identification

- [ ] UI layer changes
- [ ] state/control layer changes
- [ ] data/service layer changes
- [ ] platform contract changes (permissions/intents/provider)
- [ ] persistence impact

### 2) Data Flow Direction

- [ ] Read flow (source -> UI)
- [ ] Write flow (UI/action -> state/storage)
- [ ] Event flow (callbacks/listeners)

### 3) Boundary Contracts

For each boundary, define:

- expected input/output type
- failure behavior
- owner of conversion/validation

Examples:
- Repository returns valid non-empty channels or throws
- Coordinator catches and maps failure to UX feedback
- Install flow must use FileProvider URI + read grant

### 4) Lifecycle & Async Safety

- [ ] Any coroutine launched from lifecycleScope?
- [ ] Any delayed runnable canceled on new state?
- [ ] Any dialog action gated by `isFinishing/isDestroyed`?

### 5) Input/Focus Safety

- [ ] Key routing centralized in router/coordinator
- [ ] Menu show/hide preserves focus context
- [ ] Playback shortcut keys do not conflict with menu mode

---

## Common Failure Modes

| Failure | Root Cause | Prevention |
|---|---|---|
| UI shows stale channel state | old callback/session wins race | validate session/state before transition |
| Menu focus jumps unexpectedly | missing save/restore path | centralize focus restoration in menu coordinator |
| Install flow fails on some devices | manifest/runtime contract mismatch | validate permission + FileProvider authority path |
| Startup slow/freezes | blocking I/O on main thread | confine heavy work to `Dispatchers.IO` |

---

## Lightweight Template

```markdown
## Feature: <name>

### Layers
- UI:
- State/Control:
- Data/Service:
- Platform:
- Persistence:

### Boundaries
- Boundary A: input/output/owner
- Boundary B: input/output/owner

### Failure Matrix
- Good:
- Base:
- Bad:
```
