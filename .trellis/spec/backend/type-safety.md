# Type Safety Guidelines (Kotlin)

> Type and null-safety conventions for Android backend/service code.

---

## Core Rules

1. Default to non-null types.
2. Prefer guard clauses over `!!`.
3. Keep domain models immutable unless mutation is required.
4. Use sealed classes/interfaces for variant row/state models.

---

## Null Safety Pattern

Use early returns for invalid state/lifecycle.

Examples:
- `if (activity.isFinishing || activity.isDestroyed) return`
  in `PlaybackFailureDialogCoordinator.showPlaybackFailureDialog()`
- invalid stream/index guards in `PlaybackController.play()`

Paths:
- `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

---

## Typed Contracts

- callback interfaces should encode intent and payload type explicitly
- avoid `Any`-style contracts for domain events

Examples:
- `PlaybackController.Callback`
- `MainKeyEventRouter` constructor function contracts

Paths:
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`
- `app/src/main/java/com/jons/iptv/input/MainKeyEventRouter.kt`

---

## Constant Safety

Promote repeated literals into companion constants with semantic names.

Examples:
- timing constants in `MainActivity`
- timeout/buffer constants in playback classes

Paths:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`

---

## Anti-Patterns

- `!!` for objects influenced by lifecycle timing
- stringly-typed event identifiers when typed callback can express state
- nullable fields with unclear null semantics in domain models
