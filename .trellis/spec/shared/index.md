# Shared Development Guidelines

> Cross-cutting rules for the current Android/Kotlin IPTV project.

---

## Documentation Files

| File | Description | When to Read |
|---|---|---|
| [code-quality.md](./code-quality.md) | Mandatory quality rules | Always |
| [kotlin-type-safety.md](./kotlin-type-safety.md) | Kotlin type-safety baseline | Type-related decisions |
| [git-conventions.md](./git-conventions.md) | Commit/branch conventions | Before committing |
| [timestamp.md](./timestamp.md) | Timestamp consistency rules | Date/time handling |
| [android-build-packaging.md](./android-build-packaging.md) | Gradle build/packaging notes | Build/tooling changes |

---

## Core Rules (MANDATORY)

| Rule | File |
|---|---|
| No nullable abuse and avoid `!!` | [kotlin-type-safety.md](./kotlin-type-safety.md) |
| Heavy work off main thread | [code-quality.md](./code-quality.md) |
| Follow commit message format | [git-conventions.md](./git-conventions.md) |
| Keep timestamp unit consistent | [timestamp.md](./timestamp.md) |

---

## Before Every Commit

- [ ] `./gradlew assembleDebug` passes
- [ ] Manual verification done for touched flows
- [ ] No unsafe main-thread I/O introduced
- [ ] Commit message follows convention

## Constrained Environment Rule (Build tools unavailable)

When the current AI runtime does not have required Android build tools/SDK support:

1. AI must **not** claim build/test passed.
2. AI should output the exact commands for human execution (for example: `./gradlew assembleDebug`, `./gradlew test`).
3. Human executes checks locally and reports results.
4. AI continues only after receiving those results.

---

**Language**: All documentation should be written in **English**.
