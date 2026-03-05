# Kotlin Type-Safety Guidelines

> Kotlin type-safety baseline for shared conventions.

---

## Core Rules

1. Prefer non-null types.
2. Avoid `!!`; use guard clauses.
3. Keep method signatures explicit and typed.
4. Use sealed classes/interfaces for variant state models.

---

## Guard-First Pattern

```kotlin
if (activity.isFinishing || activity.isDestroyed) return
```

```kotlin
if (streams.isEmpty() || index !in streams.indices) return
```

---

## Callback Contract Quality

- Use explicit callback interfaces and typed parameters.
- Avoid weakly typed string-map payloads when typed contracts are possible.

---

## Constant Safety

- Replace repeated literals with named constants in companion objects.
- Keep timing/timeout values centralized in owner class.

---

## Anti-Patterns

- `!!` in lifecycle-sensitive code
- nullable fields without clear semantic meaning
- untyped `Any`-based domain events
