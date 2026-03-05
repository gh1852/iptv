# Frontend Directory Structure (Android)

> Actual UI/frontend structure for this Android IPTV app.

---

## Directory Layout

```text
app/src/main/
├── java/com/jons/iptv/
│   ├── MainActivity.kt
│   ├── ui/
│   │   ├── GroupedChannelAdapter.kt
│   │   ├── ChannelAdapter.kt
│   │   ├── CategoryAdapter.kt
│   │   ├── menu/
│   │   │   └── MenuFocusCoordinator.kt
│   │   └── dialog/
│   │       ├── PlaybackFailureDialogCoordinator.kt
│   │       └── CctvStyleDialogAnimator.kt
│   ├── input/
│   │   └── MainKeyEventRouter.kt
│   ├── playback/
│   │   └── PlayerEngineCoordinator.kt
│   └── data/
│       ├── Channel.kt
│       └── CategoryChannels.kt
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── item_channel.xml
    │   ├── item_group_header.xml
    │   └── dialog_playback_failure_cctv.xml
    ├── drawable/
    ├── values/
    │   ├── themes.xml
    │   ├── colors.xml
    │   ├── dimens.xml
    │   └── strings.xml
    └── xml/
        └── file_paths.xml
```

---

## Responsibility Boundaries

- `MainActivity`: wire coordinators, lifecycle, and top-level user flow.
- `ui/*Adapter`: list rendering and row interactions.
- `ui/menu/*`: menu visibility, focus restore, focused item centering.
- `ui/dialog/*`: dialog lifecycle + enter/exit animation behavior.
- `input/*`: key event policy routing.
- `res/layout/*`: view hierarchy.
- `res/drawable/*`: visual state/background selectors.
- `res/values/*`: design tokens for dimensions/colors/theme strings.

Examples:
- `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`
- `app/src/main/res/layout/item_channel.xml`

---

## Rules

1. New UI behavior should be added to an existing coordinator first, not directly expanded in `MainActivity` when avoidable.
2. Reusable row UI belongs in adapter + layout item pair (`*.kt` + `item_*.xml`).
3. Keep package names domain-oriented (`ui/menu`, `ui/dialog`, `input`) rather than a generic utils bucket.

---

## Anti-Patterns

- Adding new dialog behavior directly inside `MainActivity` click handlers.
- Mixing key routing logic inside adapters.
- Hardcoding dimensions/colors in Kotlin instead of `res/values/*`.
