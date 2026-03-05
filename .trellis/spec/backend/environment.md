# Environment Guidelines (Android)

> Environment/config rules for current Android project.

---

## Build Variants and Runtime Context

Current project uses Android build types (`debug`/`release`) and runtime APIs from Android framework.

Reference:
- `app/build.gradle.kts`

---

## Configuration Principles

1. Keep environment-dependent behavior explicit and centralized.
2. Do not hardcode production-only endpoints into UI classes.
3. Keep install/update contracts aligned with manifest declarations.

Examples:
- Manifest permissions/provider contracts:
  `app/src/main/AndroidManifest.xml`
- Update flow consuming those contracts:
  `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`

---

## Network & Security Notes

- Current manifest sets `android:usesCleartextTraffic="true"`; treat as explicit project decision and review before production hardening.
- Keep endpoint/proxy fallback logic inside repositories.

Examples:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`

---

## Build/Runtime Checklist

- [ ] `./gradlew assembleDebug` passes
- [ ] New environment flags/constants are not duplicated
- [ ] Manifest contract changes are reflected in coordinator/repository logic

---

## Anti-Patterns

- Using UI-layer classes as config storage.
- Spreading the same endpoint/config literal in many files.
- Changing manifest-level behavior without updating affected runtime flow.
