# Build & Packaging Notes (Android)

> Build and packaging notes for Android project delivery.

---

## Current Build Toolchain

- Gradle + Android Gradle Plugin
- Kotlin Android plugin
- JVM target 17

Reference:
- `app/build.gradle.kts`

---

## Typical Build Commands

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
```

---

## Packaging-Relevant Contracts

When changing update/install behavior, verify:

1. Manifest permissions are correct
2. FileProvider authority matches runtime usage
3. Install intent grants URI read permission

References:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

---

## Build Change Checklist

- [ ] Build still passes with `assembleDebug`
- [ ] Manifest and runtime contracts stay consistent
- [ ] No hardcoded environment-sensitive config scattered in UI

## Constrained Environment Rule

If build tools are unavailable in the AI execution environment:

- Do not run fake/partial checks and do not mark build as passed.
- Provide command list for manual execution:
  - `./gradlew assembleDebug`
  - `./gradlew test` (when tests exist)
- Wait for human-reported results before proceeding.
