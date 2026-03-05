# Thinking Flows for Android IPTV Projects

> Purpose: systematic thinking guides to catch issues before coding.

---

## Why Thinking Flows?

Most regressions come from missed boundary assumptions:

- UI state vs playback state ownership confusion
- input routing conflicts (remote keys/menu/player)
- repository parsing assumptions not reflected in UI
- lifecycle timing mistakes during async operations

These guides help you ask the right questions first.

---

## Available Thinking Guides

| Guide | Purpose | When to Use |
|---|---|---|
| [Cross-Layer Thinking](./cross-layer-thinking-guide.md) | Think through data flow across app layers | Before features touching 3+ layers |
| [Pre-Implementation Checklist](./pre-implementation-checklist.md) | Verify readiness before coding | Before any non-trivial change |
| [Bug Root Cause Analysis](./bug-root-cause-thinking-guide.md) | Analyze preventability after fixes | After non-trivial bug fixes |
| [Code Reuse Thinking](./code-reuse-thinking-guide.md) | Reduce duplication and detect shared patterns | When repeated code appears |
| [DB Schema Change](./db-schema-change-guide.md) | Control schema/runtime drift | When introducing/changing local DB schema |
| [Semantic Change Checklist](./semantic-change-checklist.md) | Ensure meaning changes propagate everywhere | When data semantics change |
| [Transaction Consistency](./transaction-consistency-guide.md) | Ensure multi-write consistency | When implementing multi-step writes |

---

## Quick Reference

Use cross-layer guide when feature touches:

- UI (`MainActivity`, adapters, dialog/menu coordinators)
- Input routing (`MainKeyEventRouter`)
- Playback domain (`PlaybackController`, `PlayerEngineCoordinator`)
- Data/repository layer (`ChannelRepository`, `AppUpdateRepository`)
- Android platform contracts (Manifest permissions, FileProvider)

Reference paths:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`
- `app/src/main/AndroidManifest.xml`

---

## Core Principles

1. Search before write.
2. Think through boundaries before implementation.
3. Make assumptions explicit in code/spec.
4. Validate all affected layers, not only the edited file.
5. Feed lessons from bugs back into these guides.

---

**Language**: All documentation should be written in **English**.
