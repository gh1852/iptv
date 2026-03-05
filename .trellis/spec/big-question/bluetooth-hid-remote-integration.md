# Bluetooth HID Remote Integration (Android TV)

> Severity: P2 - remote actions behave unexpectedly.

---

## Problem

Bluetooth remotes may emit key events that conflict with expected navigation/playback behavior.

Common symptoms:

- channel up/down keys not routed correctly
- back/menu behavior inconsistent
- focus jumps when remote input arrives during overlay/dialog transitions

---

## Current Project Pattern

Centralize remote key policy in key router, then delegate to menu/playback actions.

References:
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

---

## Recommended Rules

1. Single input policy owner (`MainKeyEventRouter`).
2. Distinguish menu-visible vs player-focused behavior.
3. Keep back-press window logic centralized.
4. Ensure key handling uses explicit consume/propagate semantics.

---

## Device Validation Checklist

- [ ] DPAD up/down in player mode
- [ ] DPAD up/down in menu mode
- [ ] channel up/down keys
- [ ] back key double-press exit behavior
- [ ] confirm/enter key action in current focus context

---

## Anti-Patterns

- scattering key logic across activity + adapters + dialogs
- mixing menu and playback key policy in unrelated classes
- not resetting transient key state on lifecycle pause
