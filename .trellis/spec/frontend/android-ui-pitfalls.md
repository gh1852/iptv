# Kotlin/Android UI Pitfalls (Replaces React Pitfalls)

> Common frontend bugs in current Android UI architecture.

---

## Pitfall 1: Leaking delayed callbacks after UI state changes

When using `postDelayed`/`Handler`, always cancel previous runnables before scheduling a new one.

Good example:
- Overlay hide runnable cancellation before rescheduling:
  `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`

Risk if missed:
- stale runnable hides current overlay unexpectedly
- outdated state transition after channel switch

---

## Pitfall 2: Playing stale session events

Playback callbacks can arrive late from old attempts. Guard by session id/state checks.

Good example:
- `PlaybackStore.markPlaying(sessionId)` validates session identity before state transition:
  `app/src/main/java/com/jons/iptv/playback/PlaybackStore.kt`

---

## Pitfall 3: Focus loss when toggling menu visibility

Hiding/showing menu without saving/restoring focus position causes broken TV navigation UX.

Good example:
- Save/restore menu state and focused item:
  `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`

---

## Pitfall 4: Treating invalid stream inputs as playable state

Always validate stream list/index before playback attempt.

Good example:
- Guard clauses in `PlaybackController.play()`:
  `app/src/main/java/com/jons/iptv/playback/PlaybackController.kt`

---

## Pitfall 5: Dialog double-dismiss race

Repeated clicks or repeated callbacks can trigger duplicate dismiss logic.

Good example:
- Animated dismiss guard flag:
  `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`

---

## Anti-Patterns

- Using `!!` for UI objects where lifecycle can invalidate references.
- Keeping multiple delayed tasks active for a single visual element.
- Triggering `show()` on already showing dialogs.
- Handling key events in both Activity and scattered views inconsistently.
