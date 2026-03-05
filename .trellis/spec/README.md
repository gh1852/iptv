# Android IPTV Development Guidelines

Project development guidelines aligned to the current codebase (**Android + Kotlin + XML + Media3**).

## Structure

### [Frontend](./frontend/index.md)

Android UI development patterns:

- [Directory Structure](./frontend/directory-structure.md)
- [Components](./frontend/components.md)
- [State Management](./frontend/state-management.md)
- [Coroutine/Callback Patterns](./frontend/hooks.md)
- [MainActivity ↔ Coordinator Boundaries](./frontend/activity-coordinator-boundaries.md)
- [XML Design](./frontend/xml-design.md)
- [Type Safety](./frontend/type-safety.md)
- [Kotlin/Android UI Pitfalls](./frontend/android-ui-pitfalls.md)
- [Android Platform Restrictions](./frontend/android-platform-restrictions.md)

### [Backend](./backend/index.md)

Android service/data domain patterns:

- [Directory Structure](./backend/directory-structure.md)
- [API Module](./backend/api-module.md)
- [API Patterns](./backend/api-patterns.md)
- [Data & Persistence](./backend/database.md)
- [Logging](./backend/logging.md)
- [Error Handling](./backend/error-handling.md)
- [Pagination Strategy](./backend/pagination.md)
- [Environment](./backend/environment.md)
- [Type Safety](./backend/type-safety.md)
- [Permission Constraints](./backend/android-permissions.md)
- [Text Input Boundaries](./backend/text-input.md)

### [Shared](./shared/index.md)

Cross-cutting conventions:

- [Code Quality](./shared/code-quality.md)
- [Type Safety (Kotlin)](./shared/kotlin-type-safety.md)
- [Git Conventions](./shared/git-conventions.md)
- [Timestamp Handling](./shared/timestamp.md)
- [Build/Packaging Notes](./shared/android-build-packaging.md)

### [Guides](./guides/index.md)

Thinking guides:

- [Pre-Implementation Checklist](./guides/pre-implementation-checklist.md)
- [Cross-Layer Thinking Guide](./guides/cross-layer-thinking-guide.md)
- [Code Reuse Thinking Guide](./guides/code-reuse-thinking-guide.md)
- [Bug Root Cause Thinking Guide](./guides/bug-root-cause-thinking-guide.md)
- [DB Schema Change Guide](./guides/db-schema-change-guide.md)
- [Transaction Consistency Guide](./guides/transaction-consistency-guide.md)
- [Semantic Change Checklist](./guides/semantic-change-checklist.md)

### [Big Questions / Pitfalls](./big-question/index.md)

Historical pitfalls and project-specific notes.

## Tech Stack

- **UI**: Android Views (XML), RecyclerView, ViewBinding
- **Language**: Kotlin (JVM 17)
- **Playback**: Media3 ExoPlayer
- **Network**: OkHttp + Coroutines
- **Build**: Gradle (AGP + Kotlin Android)

## Usage

These guidelines are used for:

1. Daily feature development and bug fixes
2. Code review consistency checks
3. Onboarding and architecture understanding
4. Capturing project-specific patterns to avoid regressions
