# Permission & Install Constraints (Android)

> Android permission guidance. (File keeps legacy name for compatibility.)

---

## Scope in Current Project

This app currently depends on:

- `android.permission.INTERNET`
- `android.permission.REQUEST_INSTALL_PACKAGES`
- `FileProvider` authority `${applicationId}.fileprovider`

Reference:
- `app/src/main/AndroidManifest.xml`

---

## Update/Install Permission Flow

### Unknown sources install permission

On Android O+:

- check with `packageManager.canRequestPackageInstalls()`
- if false, route user to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`

Example:
- `AppUpdateCoordinator.canRequestPackageInstallsCompat()`
- `AppUpdateCoordinator.openUnknownSourcesSettings()`

Path:
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

### APK install via FileProvider URI

- use `FileProvider.getUriForFile(...)`
- grant read permission flag for install intent

Example:
- `AppUpdateCoordinator.installDownloadedApk()`

Path:
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

---

## Validation Rules

1. Manifest provider authority must match runtime authority construction.
2. Install flow must handle failure paths with user-visible feedback.
3. Do not use raw file `file://` URIs for package install.

---

## Anti-Patterns

- launching installer without `FLAG_GRANT_READ_URI_PERMISSION`
- assuming unknown-sources permission is always granted
- swallowing install launch errors silently
