# Live TV Feature Spec (CCTV-style) for Kotlin Android

> Reference baseline: CCTV app live TV experience.
> Target project: `com.jons.iptv` Android app.

---

## 1) Goal

Provide a TV-first live streaming experience similar to CCTV app live TV:

- Enter live TV quickly
- Browse channels by group in one list
- Play channels with multi-source failover
- Keep playback resilient when stream URLs fail

---

## 2) Scope

### In Scope

1. Landscape-first startup
2. Grouped live channel list (expand/collapse)
3. Single channel entry with multiple stream URLs
4. Auto failover to next source on playback error
5. Final failure dialog with `Retry` / `Close`
6. Bottom overlay for channel name + logo (auto hide)

### Out of Scope

1. Account/login/VIP
2. EPG timeline and program reservation
3. Cloud sync/history/favorites
4. Search/recommendation feed

---

## 3) Functional Requirements

### FR-1 App Startup

- App MUST open in landscape orientation by default.
- On successful playlist load, app SHOULD autoplay the first channel in the first group.

### FR-2 Channel Group Browsing

- UI MUST show grouped channels in a single RecyclerView region.
- Each group header MUST display:
  - group name
  - channel count
  - expand/collapse indicator
- Groups SHOULD be expanded by default on first load.

### FR-3 Channel Identity and Source Merging

- Parser MUST merge multiple stream URLs into one channel item by key:
  - `category + channelName`
- Channel row shows one logical channel, while playback uses ordered `streamUrls`.

### FR-4 Playback and Failover

- Selecting a channel MUST start playback from stream index `0`.
- On `Player` error, app MUST try the next stream URL automatically.
- Failover MUST continue until last URL.

### FR-5 Failure UX

- If all stream URLs fail, app MUST show dialog:
  - Title: playback failed
  - Message includes channel name
  - Actions: `Retry` (restart from index 0), `Close`

### FR-6 On-Screen Overlay

- When channel changes successfully, app MUST show overlay with:
  - channel name
  - channel logo (placeholder allowed)
- Overlay MUST auto-hide after 3 seconds.

---

## 4) Kotlin-Oriented Data Contract (KT)

```kotlin
data class Channel(
    val name: String,
    val category: String,
    val logoUrl: String?,
    val streamUrls: List<String>
)

data class CategoryChannels(
    val category: String,
    val channels: List<Channel>
)
```

Playback state model:

```kotlin
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Playing(val channel: Channel, val streamIndex: Int) : PlaybackState
    data class Failed(val channel: Channel) : PlaybackState
}
```

---

## 5) Behavioral Rules

1. `streamUrls.isEmpty()` => cannot play, go to failure UX.
2. `startIndex !in indices` => treat as failure.
3. Any successful `setMediaItem + prepare + playWhenReady` updates current channel and stream index.
4. Dialog MUST NOT stack (only one failure dialog at a time).
5. Player listener and UI callbacks MUST be lifecycle-safe (`isFinishing/isDestroyed` guard).

---

## 6) Acceptance Criteria (KT-ready)

- [ ] AC-1: App enters landscape directly on cold start.
- [ ] AC-2: Grouped channel list is visible in one panel; groups can expand/collapse.
- [ ] AC-3: One channel item can contain multiple stream URLs.
- [ ] AC-4: Playback error triggers automatic next-source failover.
- [ ] AC-5: After all sources fail, a Retry/Close dialog appears.
- [ ] AC-6: Retry restarts playback attempts from first source.
- [ ] AC-7: On channel switch, overlay appears and hides in 3 seconds.

---

## 7) Mapping to Current Codebase

- Playback orchestration: `app/src/main/java/com/jons/iptv/MainActivity.kt`
- M3U merge parser: `app/src/main/java/com/jons/iptv/data/M3uParser.kt`
- Channel repository: `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- Grouped channel list adapter: `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`

---

## 8) Non-Functional Baseline

1. Playlist fetch timeout should remain bounded.
2. Failure at network/parser/player boundaries must not crash app.
3. UI interactions (group toggle/channel click) should remain responsive during playback changes.

---

## 9) Playback Retry Policy Guardrail

### Pattern: Prefer minimal load-error policy customization

**Problem**: Over-customizing `LoadErrorHandlingPolicy` can create side effects without improving failover, because this feature already has explicit switch-source fallback in `PlaybackController`.

**Rule**:

- Keep `DefaultLoadErrorHandlingPolicy(0)` to disable internal repeated retries for a source.
- Do not override `getMinimumLoadableRetryCount()` when the constructor already sets the same value.
- Only add custom `getRetryDelayMsFor()` when there is measured evidence that delay tuning improves success rate.

**Good**:

```kotlin
.setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
```

**Avoid**:

```kotlin
object : DefaultLoadErrorHandlingPolicy(0) {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 0
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long = ...
}
```

**Why**: This keeps behavior aligned with existing failover state machine (`player error` / `first-frame timeout` => switch source), reduces redundant logic, and lowers maintenance risk.
