# Global Keyboard Hook Limits (Android)

> Severity: P2 - requested global hotkey behavior may be unsupported or constrained.

---

## Problem

Desktop-style global keyboard hooks are not a default app capability on Android. Cross-app key capture requires privileged patterns (e.g., accessibility/service-level integration) and strict user consent.

---

## Current Project Reality

This IPTV app currently handles input in-app via activity key events and router delegation, not global system-wide hooks.

References:
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

---

## Guidance

1. Prefer in-app key handling for product flows.
2. If global capture is proposed, define explicit:
   - permission/service requirements
   - user consent and UX
   - security/privacy implications
3. Keep fallback behavior when permission/service unavailable.

---

## Anti-Patterns

- promising global key capture without platform-permission design
- implementing hidden background input capture behavior
- conflating in-app remote routing with system-wide keyboard hooks
