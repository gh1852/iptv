# Android Platform/UI Restrictions

> Android-specific UI constraints replacing old Electron browser API restrictions.

---

## 1) Do not block UI thread for network/file/hash

Blocking calls on main thread will cause jank/ANR.

Use:
- `withContext(Dispatchers.IO)` in repositories.

Examples:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`

---

## 2) Activity lifecycle must gate UI side effects

Dialogs and focus operations must check lifecycle validity.

Example:
- `if (activity.isFinishing || activity.isDestroyed) return`
  in `PlaybackFailureDialogCoordinator`

Path:
- `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`

---

## 3) File install flow must use FileProvider URI

For APK installation, do not expose raw file paths directly.

Example:
- Install intent via `FileProvider.getUriForFile` in update flow.

Path:
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`
- `app/src/main/res/xml/file_paths.xml`

---

## 4) TV key navigation should be centralized

Do not scatter key policy across unrelated views.

Use routing coordinator/router:
- `MainKeyEventRouter`

Path:
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`

---

## 5) Dialog animation and dismiss should be idempotent

Repeated dismiss/show events can race without guard flags.

Example:
- animated dismiss guard in failure dialog coordinator.

Path:
- `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`

---

## Anti-Patterns

- Calling blocking I/O in click listeners directly.
- Showing dialogs during/after Activity teardown.
- Triggering install flow without URI permission grants.
- Managing key behavior ad hoc per view instead of centralized router.
