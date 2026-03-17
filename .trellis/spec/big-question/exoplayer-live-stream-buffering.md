# ExoPlayer Live Stream Buffering & Stall (IPTV)

> Severity: P1 - live streams stall frequently or take long to start, while same streams work fine in other players.

---

## Problem

ExoPlayer default buffer parameters are tuned for VOD (video-on-demand), not live IPTV streams. Using defaults causes:

- Slow startup: large pre-buffer requirement before playback begins
- Frequent `STATE_BUFFERING`: live streams cannot sustain the large min-buffer target
- Aggressive live edge catching: small `liveTargetOffset` causes ExoPlayer to speed up playback to catch the live edge, draining the buffer faster than it fills
- `STATE_ENDED` on server disconnect: ExoPlayer misidentifies dropped connections as stream end, freezing the screen permanently
- Slow error detection: malformed container streams (`ERROR_CODE_PARSING_CONTAINER_MALFORMED`) take 30+ seconds to fail due to accumulated read timeouts

---

## Current Project Configuration

Reference: `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`

```kotlin
// DefaultLoadControl (buffer targets)
LOAD_CONTROL_MIN_BUFFER_MS = 5_000          // min healthy buffer
LOAD_CONTROL_MAX_BUFFER_MS = 10_000         // max pre-buffer
LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS = 500   // start playback threshold
LOAD_CONTROL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_000

// MediaSourceFactory
.setLiveTargetOffsetMs(8_000)               // don't aggressively chase live edge

// HTTP
HTTP_CONNECT_TIMEOUT_MS = 4_000
HTTP_READ_TIMEOUT_MS = 4_000                // lower = faster bad-source detection
```

Reference: `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

```kotlin
DEFAULT_BUFFERING_TIMEOUT_MS = 15_000       // single buffering timeout fallback
MAX_BUFFERING_COUNT = 3                     // switch source after 3 cumulative stalls
```

---

## Key Diagnostic: Log Analysis

### Symptom 1: position jumps backward
```
state=3 position=19908 → state=2 → state=3 position=10322
```
Means ExoPlayer drained buffer and re-synced to live edge. Root cause: `liveTargetOffset` too small.

### Symptom 2: frequent short stalls (every 4-8s)
```
state=3 → state=2 (4s later) → state=3 → state=2 (4s later)
```
Means network throughput cannot sustain stream bitrate. Buffer parameters cannot fix this — only source quality or switching to better source helps.

### Symptom 3: state=4 (STATE_ENDED) on live stream
```
state=4, playWhenReady=true
```
Live streams should never end. Server dropped connection or playlist contained `#EXT-X-ENDLIST`. Must handle with `switchToNextOrFail`.

---

## Resilience Rules

- `MIN_BUFFER` for live IPTV: 3-8s (not 30s+)
- `MAX_BUFFER` for live IPTV: 8-15s (not 90s+)
- `liveTargetOffset`: 6-10s to avoid aggressive catching
- Always handle `STATE_ENDED` on live streams — treat as source failure
- Track cumulative `STATE_BUFFERING` count per source; switch after threshold (e.g. 3 stalls)
- Lower `HTTP_READ_TIMEOUT` (4s) for faster bad-source detection

---

## Anti-Patterns

- Using VOD buffer defaults (`MIN=30s`, `MAX=90s`) for live streams
- Setting `liveTargetOffset` < `MIN_BUFFER` (causes chase→drain→stall loop)
- Ignoring `STATE_ENDED` on live streams (permanent freeze)
- High `HTTP_READ_TIMEOUT` (8s+) — multiplies into 30s+ detection time for malformed streams
- Resetting stall count on `STATE_READY` (prevents cumulative detection of bad sources)
- Using `DefaultHttpDataSource` — creates a new TCP connection per request; use `OkHttpDataSource` to reuse connection pool across source switches

---

## OkHttp DataSource

Use `media3-datasource-okhttp` instead of `DefaultHttpDataSource` for connection pool reuse:

```kotlin
// build.gradle.kts
implementation("androidx.media3:media3-datasource-okhttp:1.6.0")

// PlayerEngineCoordinator.kt
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(HTTP_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
    .readTimeout(HTTP_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
    .build()

val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
val dataSourceFactory = DefaultDataSource.Factory(activity, httpDataSourceFactory)
```

Benefit: when switching to a backup source on the same host, TCP connection is already established in the pool.
