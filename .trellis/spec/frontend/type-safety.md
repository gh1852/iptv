# Type Safety Guidelines (Kotlin Android)

> Type and null-safety rules for UI/frontend layer.

---

## Core Rules

1. Prefer non-null types by default.
2. Avoid `!!`; use guard clauses and nullable handling.
3. Keep domain models immutable (`val`) unless mutability is essential.
4. Use sealed classes/interfaces for UI row polymorphism.

Examples:
- Sealed row model usage: `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`
- Data model definitions: `app/src/main/java/com/jons/iptv/data/Channel.kt`

---

## Null Handling Pattern

Use early return when lifecycle/context can be invalid.

Examples:
- `if (activity.isFinishing || activity.isDestroyed) return` in dialog coordinator:
  `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`
- Guard invalid indices/streams:
  `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

---

## Constant & Enum-like Safety

Prefer `companion object` constants for repeated semantic values.

Examples:
- `MainActivity` timing constants
- `PlayerEngineCoordinator` buffer/timeout constants

Paths:
- `app/src/main/java/com/jons/iptv/MainActivity.kt`
- `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`

---

## Callback Type Contracts

Callbacks should be typed and focused.

Example:
- `PlaybackController.Callback` explicit events instead of generic map/object payloads.

Path:
- `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

---

## Anti-Patterns

- Using `Any`/platform raw objects for UI communication.
- Adding nullable fields without clear null semantics.
- Using stringly-typed event routing where sealed/typed callbacks are possible.
