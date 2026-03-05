# Quality Guidelines

> Code quality standards for backend-side development in this project.

---

## Overview

This project’s baseline quality gate is successful Gradle build in CI and local verification.

Primary evidence:

- Debug build in CI: `.github/workflows/android-build.yml:47`
- Release build in CI: `.github/workflows/android-release.yml:102`
- Local build docs: `README.md:83`

---

## Forbidden Patterns

- Putting network/data parsing logic directly in Activity/UI classes.
- Ignoring exceptions from IO/network/parse operations.
- Skipping integrity checks for downloaded release artifacts.
- Introducing DB-specific abstractions before actual DB usage exists.

Evidence for preferred alternatives:

- Data logic in repository classes: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- SHA256 validation for downloaded APK: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:124`

---

## Required Patterns

- Keep domain responsibilities separated by package (`data`, `playback`, `update`, `input`, `ui`).
- Use `runCatching` + explicit failure propagation for risky operations.
- For multi-source remote requests, keep retry candidates and report final error with cause.
- Use descriptive exception messages that identify failure stage.

Examples:

- Retry + `lastError`: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt:95`
- Failure wrapping with cause: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:81`

---

## Testing Requirements

Current repository evidence shows build validation is mandatory; test suite coverage is limited.

- Must pass: `./gradlew assembleDebug`
- Release path must pass in CI: `./gradlew :app:assembleRelease --stacktrace`

When modifying backend-side logic:

- Verify success path and at least one failure path manually.
- Validate retry/fallback behavior when primary remote source fails.

---

## Code Review Checklist

- Is logic placed in the correct domain package?
- Are network/IO failures handled with `runCatching` and clear error messages?
- Is root cause preserved when rethrowing?
- Are logging messages contextual and not leaking sensitive data?
- Do local and CI build commands still pass?
