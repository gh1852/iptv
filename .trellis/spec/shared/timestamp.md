# Timestamp Specification

> Rule: Keep timestamp unit **consistent per data contract**, and document it explicitly.

---

## Current Project Reality

This Android IPTV project currently relies mostly on:

- remote playlist/update payloads
- in-memory state
- SharedPreferences/file cache for lightweight local persistence

There is no active Room/SQLite schema in the current codebase.

---

## Practical Rules

1. Do not mix seconds and milliseconds in the same feature contract.
2. If timestamp unit comes from remote API, normalize once at repository boundary.
3. Keep UI layer independent from raw timestamp parsing details.
4. When adding new local persistence, define unit in schema/docs immediately.

---

## Recommended Kotlin Helpers

```kotlin
fun nowMillis(): Long = System.currentTimeMillis()

fun secondsToMillis(seconds: Long): Long = seconds * 1000
```

---

## Checklist (When Adding Time Fields)

- [ ] Timestamp unit is documented in type/model comments or spec
- [ ] Conversion happens in one place (repository/mapper)
- [ ] No mixed-unit comparisons in business logic
- [ ] Manual tests include boundary and ordering checks

---

## Anti-Patterns

- Comparing second-based values with millisecond-based values directly
- Spreading conversion logic across multiple UI classes
- Relying on implicit assumptions about timestamp unit
