# Frontend Quality Guidelines (Android)

> Frontend/UI quality baseline for this project.

---

## Build & Verification

Before merge, ensure:

- `./gradlew assembleDebug` passes
- TV remote key navigation is manually validated for changed flows
- Dialog/overlay transitions are visually verified

If AI environment lacks build tools/SDK, AI should output the required commands for human execution and wait for reported results before continuing.

---

## UI Behavior Checklist

- [ ] Focus can enter and leave menu/player correctly
- [ ] Menu show/hide preserves expected focused item
- [ ] Channel switch still updates overlay and playing state
- [ ] Error paths display user feedback (toast/dialog), not silent failure
- [ ] No main-thread network/file/blocking work introduced

Example references:
- `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`
- `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

---

## Adapter Quality Rules

1. Keep `DiffUtil` meaningful for list identity/content change.
2. Avoid unnecessary full refresh when localized updates are feasible.
3. Keep `bind()` side-effect free except UI binding and click wiring.

Examples:
- `ChannelAdapter.Diff`
- `CategoryAdapter.Diff`

---

## Lifecycle Safety

- Avoid showing dialogs when Activity is finishing/destroyed.
- Release/cleanup player and pending callbacks in lifecycle boundaries.

Examples:
- Dialog show guard in `PlaybackFailureDialogCoordinator`
- Lifecycle delegation in `MainActivity`

---

## Anti-Patterns

- UI regressions accepted without remote-navigation testing.
- Adding UI constants in multiple files instead of centralized constants/resources.
- Mixing networking/parsing into adapters or direct view callbacks.
