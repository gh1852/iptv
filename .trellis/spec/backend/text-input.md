# Text Input and User Input Boundaries (Android)

> Android-focused replacement for legacy desktop text-insert guidance.

---

## Current Project Reality

The current IPTV project does not implement global cross-app text insertion.

Therefore, this guideline defines boundaries for future text-input-related features in Android context.

---

## In-App Text Input Rules

1. Prefer standard Android input widgets and IME flow.
2. Avoid clipboard-dependent hacks for internal feature flows when direct APIs exist.
3. Keep text handling logic near its owning feature module.

---

## External Insertion Caveat

Cross-app text insertion on Android is permission- and context-sensitive. Any future implementation must explicitly define:

- required Android permissions/services
- user consent flow
- fallback and failure behavior

Before adding such feature, update this spec with concrete implementation contracts.

---

## Logging & Failure Handling

If text processing/transformation is added:

- use clear error categorization
- avoid silent drops
- provide user-facing feedback when input action fails

Reference patterns:
- `app/src/main/java/com/jons/iptv/update/AppUpdateCoordinator.kt`
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

---

## Anti-Patterns

- introducing hidden clipboard side effects without user awareness
- embedding heavy text processing directly in UI callbacks on main thread
- adding platform-specific assumptions without runtime checks
