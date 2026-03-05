# Android Frontend Development Guidelines

> Frontend/UI guidelines for the current **Android IPTV** project (Kotlin + XML + ViewBinding + Media3).

## Tech Stack (Current Reality)

- **UI Framework**: Android View system (XML layouts)
- **Language**: Kotlin
- **Binding**: ViewBinding
- **Lists**: RecyclerView + Adapter/ViewHolder
- **Image Loading**: Coil
- **Playback UI**: Media3 `PlayerView`

---

## Documentation Files

| File | Description | Priority |
|---|---|---|
| [directory-structure.md](./directory-structure.md) | Android UI package/layout structure | **Must Read** |
| [components.md](./components.md) | RecyclerView rows, focusable items, overlay/dialog UI | **Must Read** |
| [state-management.md](./state-management.md) | Activity orchestration and coordinator state patterns | **Must Read** |
| [android-ui-pitfalls.md](./android-ui-pitfalls.md) | Kotlin/Android UI pitfalls for this project | Reference |
| [hooks.md](./hooks.md) | Coroutine/callback interaction patterns (Android equivalent) | Reference |
| [type-safety.md](./type-safety.md) | Kotlin type/null-safety conventions | Reference |
| [xml-design.md](./xml-design.md) | XML drawable/theme/dimens design system | Reference |
| [quality.md](./quality.md) | UI quality checklist before merge | Reference |
| [activity-coordinator-boundaries.md](./activity-coordinator-boundaries.md) | MainActivity ↔ coordinator interaction boundaries | Reference |
| [android-platform-restrictions.md](./android-platform-restrictions.md) | Android platform/UI limitations & replacements | Reference |

---

## Quick Navigation by Task

### Before UI Development

| Task | Document |
|---|---|
| Understand app UI layout layers | [directory-structure.md](./directory-structure.md) |
| Understand focus/menu behavior | [state-management.md](./state-management.md) |
| Understand component construction | [components.md](./components.md) |

### During Development

| Task | Document |
|---|---|
| Add/modify list rows | [components.md](./components.md) |
| Add UI state transitions | [state-management.md](./state-management.md) |
| Add coroutine-driven loading flow | [hooks.md](./hooks.md) |
| Add typed models/events | [type-safety.md](./type-safety.md) |

### Before Commit

| Task | Document |
|---|---|
| Theme/drawable consistency check | [xml-design.md](./xml-design.md) |
| Build + runtime quality check | [quality.md](./quality.md) |

---

## Core Rules Summary

| Rule | Reference |
|---|---|
| Keep `MainActivity` as orchestration layer | [state-management.md](./state-management.md) |
| Put playback fallback logic in `playback/` | [activity-coordinator-boundaries.md](./activity-coordinator-boundaries.md) |
| Use ViewBinding instead of `findViewById` scattering | [components.md](./components.md) |
| UI list rows must be focusable/selectable for TV flow | [components.md](./components.md) |
| Network/file work must run in IO coroutine context | [hooks.md](./hooks.md) |
| Avoid nullable abuse and `!!` | [type-safety.md](./type-safety.md) |

---

## Architecture Overview (Current Project)

```text
MainActivity (screen orchestration)
    |
    +-- MenuFocusCoordinator (menu show/hide + focus restore)
    +-- PlayerEngineCoordinator (player lifecycle + overlay)
    +-- AppUpdateCoordinator (update dialog/install flow)
    +-- PlaybackFailureDialogCoordinator (failure dialog UX)
    |
RecyclerView adapters (GroupedChannelAdapter/ChannelAdapter)
    |
XML layouts + drawables + themes
```

Examples:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`
- `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`
- `app/src/main/res/layout/activity_main.xml`

---

**Language**: All documentation should be written in **English**.
