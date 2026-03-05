# Registration Chain Issues (Coordinator/Router)

> Severity: P1 - feature appears implemented but does not execute.

---

## Problem

Code exists, compiles, but behavior never triggers at runtime because wiring/registration chain is incomplete.

In this project, common examples include:

- key routing not delegated to `MainKeyEventRouter`
- coordinator callback not connected in `MainActivity`
- menu/dialog/playback handlers created but never invoked

---

## Registration Chain (Current Project)

```text
Activity lifecycle/setup
   -> create coordinator/router instances
   -> wire callbacks between modules
   -> delegate events (key, playback, dialog)
   -> runtime behavior executes
```

Primary file:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

Related modules:
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`
- `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

---

## Debug Checklist

1. Is module constructed during activity setup?
2. Are required callbacks passed and non-empty?
3. Is event delegation actually calling the module?
4. Is lifecycle path enabling/disabling the module correctly?

---

## Anti-Patterns

- defining new coordinator methods without wiring call sites
- splitting one domain across duplicate files with unclear ownership
- assuming file existence means runtime registration is complete
