# Code Quality Guidelines (Android/Kotlin)

> Mandatory quality rules for this project.

---

## Core Requirements

1. Keep `MainActivity` orchestration-focused.
2. Keep network/file/hash operations in repository layer and IO dispatcher.
3. Prefer clear guard clauses over risky assumptions.
4. Do not silently swallow errors.

---

## Null and Type Safety

- Avoid `!!` unless absolutely unavoidable.
- Prefer non-null type design and early returns.

Example pattern:

```kotlin
val user = repo.findUser(id) ?: return
use(user)
```

---

## Threading Rules

- Blocking work must use `withContext(Dispatchers.IO)`.
- UI updates must stay on Activity/UI-safe paths.

---

## Naming & Structure

- Domain-oriented packages (`data`, `playback`, `update`, `input`, `ui`).
- Avoid generic dump folders for domain logic.
- Promote repeated literals to `companion object` constants.

---

## Validation Checklist

Before merge:

- [ ] `./gradlew assembleDebug` succeeds
- [ ] No main-thread blocking I/O added
- [ ] New logic placed in correct package
- [ ] Error paths log and/or provide user feedback

---

## Anti-Patterns

- Growing `MainActivity` with parsing/network branches
- Swallowing exceptions with empty `catch`
- Duplicating fallback logic in multiple locations
