# Android IPTV Common Pitfalls

> Historical pitfalls and troubleshooting notes adapted to current stack.

## Severity Levels

| Level | Description |
|---|---|
| P0 | App crash or startup failure |
| P1 | Core feature broken |
| P2 | Degraded UX with workaround |

---

## By Category

### Build / Packaging

| Document | Severity | Summary |
|---|---|---|
| [native-library-packaging.md](./native-library-packaging.md) | P0 | Native library packaging/runtime pitfalls |
| [complex-native-dependencies.md](./complex-native-dependencies.md) | P0 | Deep native dependency risk and rollout strategy |

### Architecture Boundaries

| Document | Severity | Summary |
|---|---|---|
| [registration-chain-issues.md](./registration-chain-issues.md) | P1 | Wiring/registration chain breakages in coordinator architecture |

### Network / Data

| Document | Severity | Summary |
|---|---|---|
| [android-network-environment-differences.md](./android-network-environment-differences.md) | P1 | Network environment differences can break fetch paths |
| [transaction-silent-failure.md](./transaction-silent-failure.md) | P1 | Silent failure patterns still relevant for data consistency |
| [timestamp-precision.md](./timestamp-precision.md) | P1 | Timestamp unit mismatch causes ordering/display bugs |

### UI / Interaction

| Document | Severity | Summary |
|---|---|---|
| [react-usestate-function.md](./react-usestate-function.md) | P2 | Legacy title; concept maps to state ownership pitfalls |
| [css-flex-centering.md](./css-flex-centering.md) | P2 | Layout alignment pitfalls still applicable in XML layouts |

### Input / Device

| Document | Severity | Summary |
|---|---|---|
| [bluetooth-hid-remote-integration.md](./bluetooth-hid-remote-integration.md) | P2 | Remote/HID input may alter expected key events |
| [global-keyboard-hook-limits.md](./global-keyboard-hook-limits.md) | P2 | Input event capture limits and conflicts |

---

## Quick Debugging Checklist

### App Fails to Start (P0)

1. Check recent manifest/config changes.
2. Verify `./gradlew assembleDebug` output.
3. Re-check initialization order in `MainActivity` and coordinators.

### Core Flow Broken (P1)

1. Verify data fetch path and fallback candidates.
2. Check error logs for repository exceptions.
3. Confirm coordinator callback wiring.

### Input/Focus Issues (P2)

1. Check key routing in `MainKeyEventRouter`.
2. Verify menu focus restore flow.
3. Validate lifecycle timing around dialog/show/hide.

---

## Current Stack Reference

- Android Views (XML) + ViewBinding
- Kotlin + Coroutines
- Media3 ExoPlayer
- OkHttp networking

Some document filenames in this folder are legacy names retained for compatibility with existing links.
