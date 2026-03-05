# Complex Native Dependency Risk (Android)

> Severity: P0/P1 depending on feature criticality.

---

## Problem

Some Android features depend on native-heavy SDKs with deep transitive dependencies. Integration may compile but fail at runtime under specific devices/ABIs.

---

## Typical Risks

- ABI incompatibility across devices
- unexpected runtime initialization requirements
- larger APK and startup overhead
- hard-to-diagnose crashes from transitive native code

---

## Decision Matrix

| Option | When to Use |
|---|---|
| Full integration | Feature is core and tested across target devices |
| Isolated optional path | Feature is non-core and can degrade gracefully |
| Deferred rollout | Risk too high for current release window |

---

## Recommended Integration Strategy

1. Add dependency in isolation branch.
2. Validate startup + core flow on representative devices.
3. Add clear failure fallback path.
4. Ship only after build + runtime matrix passes.

---

## Current Project Anchors

- Build config reference: `app/build.gradle.kts`
- Playback/runtime orchestration: `app/src/main/java/com/jons/iptv/playback/`

---

## Anti-Patterns

- introducing deep native stack without rollout plan
- making optional native feature block app startup
- lacking logs around initialization failures
