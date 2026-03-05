# Database Guidelines

> Database patterns and conventions for this project.

---

## Overview

Current codebase has **no ORM/database layer**. There is no Room/SQLite/Realm/SqlDelight setup in active app code.

Data flow is currently:

1. Fetch remote resources over HTTP
2. Parse into in-memory models
3. Keep short-lived in-memory cache where needed
4. Persist only specific files when required (APK download)

---

## Query Patterns

There are no SQL query patterns in the current repository.

Instead, data access uses OkHttp + parsing:

- Playlist fetch and parse: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt:94`
- Update metadata fetch and JSON parse: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:46`
- APK binary download to file: `app/src/main/java/com/jons/iptv/data/AppUpdateRepository.kt:84`

In-memory cache pattern:

- Volatile cached channels field: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt:215`

---

## Migrations

No migration mechanism exists today.

No evidence found for:

- Room annotations (`@Entity`, `@Dao`, `@Database`)
- `SQLiteOpenHelper`
- SqlDelight/Realm/ObjectBox migration files

If persistence is added later, this file should be updated with real migration conventions.

---

## Naming Conventions

Since no DB schema exists, naming conventions currently apply to data-layer classes:

- `*Repository` for data-source access classes
- Data models under `data/` (for example `Channel`, `UpdateInfo`)

---

## Common Mistakes

- Assuming a local DB exists; currently it does not.
- Introducing ad-hoc local persistence without documenting schema/migration strategy.
- Mixing remote fetch/retry logic into UI layer instead of repository classes.
