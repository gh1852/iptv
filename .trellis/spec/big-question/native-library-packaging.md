# Native Library Packaging (Android)

> Severity: P0 - app may fail to start or feature fails at runtime.

---

## Problem

After adding native dependencies (JNI/AAR/ABI-specific binaries), debug may work on one device but fail on others or in release builds.

Typical symptoms:

- `UnsatisfiedLinkError`
- ABI mismatch failures
- feature works in debug but fails after packaging/minify

---

## Root Causes

1. ABI coverage mismatch (`arm64-v8a`, `armeabi-v7a`, etc.)
2. Native libs stripped/excluded by packaging config
3. Runtime initialization order issues
4. Optional native module assumptions not guarded

---

## Current Project Context

This project currently relies on Media3 + ffmpeg decoder dependency and Android packaging.

Reference:
- `app/build.gradle.kts`

---

## Practical Rules

1. Verify supported ABIs before shipping.
2. Test install/run on at least one real target device class.
3. Keep native-feature initialization failure non-silent.
4. Do not couple critical startup path to optional native capability.

---

## Verification Checklist

- [ ] `./gradlew assembleDebug` succeeds
- [ ] app launches on target device ABI
- [ ] playback core path still works with expected decoder stack
- [ ] failure path has clear log + user-facing fallback if applicable

---

## Anti-Patterns

- introducing native dependency without device-level validation
- swallowing native init errors
- assuming emulator behavior equals production device behavior
