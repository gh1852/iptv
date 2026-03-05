# Backend Development Guidelines Index

> Backend/service guidelines for the current **Android IPTV** project.

## Tech Stack (Current Reality)

- **Runtime**: Android app process (no Electron main process)
- **Language**: Kotlin
- **Networking**: OkHttp
- **Async**: Kotlin Coroutines
- **Playback domain**: Media3 + coordinator/controller pattern
- **Persistence**: Lightweight local state (SharedPreferences + file cache), no Room schema yet

---

## Documentation Files

| File | Description | When to Read |
|---|---|---|
| [directory-structure.md](./directory-structure.md) | Actual package/domain layout | Starting a new feature |
| [api-module.md](./api-module.md) | Kotlin module/service boundaries | Creating/modifying service modules |
| [api-patterns.md](./api-patterns.md) | Common backend/service patterns in this app | Implementing fetch/parse/fallback logic |
| [error-handling.md](./error-handling.md) | Exception strategy and UI boundary handling | Handling failures |
| [database.md](./database.md) | Current data/persistence reality | Data and storage changes |
| [environment.md](./environment.md) | Build/debug/release environment concerns | Environment/config work |
| [type-safety.md](./type-safety.md) | Kotlin null/type safety patterns | Type-related decisions |
| [logging.md](./logging.md) | Logging strategy and log semantics | Debugging and observability |
| [pagination.md](./pagination.md) | List growth/pagination strategy for Android data sources | Large list APIs/features |
| [quality.md](./quality.md) | Build and code quality checklist | Before merge |
| [android-permissions.md](./android-permissions.md) | Android permission/install constraints | Permission-sensitive features |
| [text-input.md](./text-input.md) | Android text input and insertion constraints (legacy filename) | Text insertion features |

---

## Quick Navigation

### Service/Data Modules

| Task | File |
|---|---|
| Organize domain packages | [directory-structure.md](./directory-structure.md) |
| Add/modify repository/coordinator module | [api-module.md](./api-module.md) |
| Follow existing service patterns | [api-patterns.md](./api-patterns.md) |

### Type & Error Safety

| Task | File |
|---|---|
| Kotlin null/type safety | [type-safety.md](./type-safety.md) |
| Runtime failure strategy | [error-handling.md](./error-handling.md) |

### Data & Platform

| Task | File |
|---|---|
| Remote + local cache behavior | [database.md](./database.md) |
| Permission/install constraints | [android-permissions.md](./android-permissions.md) |
| Build/runtime environment | [environment.md](./environment.md) |

---

## Core Rules Summary

| Rule | Reference |
|---|---|
| Keep `MainActivity` orchestration-only | [quality.md](./quality.md) |
| Keep network/parse in repository layer | [api-module.md](./api-module.md) |
| Run heavy I/O in `Dispatchers.IO` | [api-patterns.md](./api-patterns.md) |
| Repository failures must be explicit | [error-handling.md](./error-handling.md) |
| Avoid `!!` and nullable abuse | [type-safety.md](./type-safety.md) |
| Validate install flow with `FileProvider` | [android-permissions.md](./android-permissions.md) |
| Build check uses `./gradlew assembleDebug` | [quality.md](./quality.md) |

---

## Reference Paths

| Feature | Typical Location |
|---|---|
| Activity orchestration | `app/src/main/java/com/jons/iptv/MainActivity.kt` |
| Data repositories | `app/src/main/java/com/jons/iptv/data/` |
| Playback domain | `app/src/main/java/com/jons/iptv/playback/` |
| Update flow | `app/src/main/java/com/jons/iptv/update/` |
| Key input routing | `app/src/main/java/com/jons/iptv/input/` |
| Manifest + permissions | `app/src/main/AndroidManifest.xml` |

---

**Language**: All documentation should be written in **English**.
