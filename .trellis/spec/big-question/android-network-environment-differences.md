# Network Environment Differences (Android)

> Severity: P1 - network operations fail on specific devices/environments.

---

## Problem

A URL may work in browser/dev tools but fail in app runtime due to device network environment differences.

Possible factors:

- proxy/VPN behavior on target device
- DNS differences
- cleartext/http policy differences
- timeout assumptions too aggressive for environment

---

## Current Project Context

Network calls are performed in repositories using OkHttp.

References:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt`
- `app/src/main/AndroidManifest.xml` (`usesCleartextTraffic`)

---

## Practical Diagnostics

1. Confirm failing URL from same device network context.
2. Compare behavior with/without proxy/VPN.
3. Inspect timeout values and retries/fallback candidate list.
4. Ensure manifest/network policy aligns with URL scheme.

---

## Resilience Rules

- Keep candidate/fallback source logic in repositories.
- Preserve last failure reason when all candidates fail.
- Surface user-friendly error at UI boundary.

---

## Anti-Patterns

- hardcoding single fragile endpoint without fallback
- hiding root error details entirely from logs
- assuming desktop/browser success equals Android runtime success
