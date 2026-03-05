# Component Guidelines (Android XML + RecyclerView)

> UI component construction rules for this project.

---

## RecyclerView Row Pattern

Use `ViewBinding` in adapter ViewHolders and bind all row state in `bind()`.

Examples:
- `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`
- `app/src/main/java/com/jons/iptv/ui/ChannelAdapter.kt`
- `app/src/main/res/layout/item_channel.xml`

### Required Practices

1. Row layout should be focusable for TV/remote operation:
   - `android:focusable="true"`
   - `android:focusableInTouchMode="true"`
2. Use `isSelected` to drive selected/playing visual state.
3. Keep image loading placeholder/error fallback configured.

Example path:
- `app/src/main/res/layout/item_channel.xml`
- `app/src/main/java/com/jons/iptv/ui/ChannelAdapter.kt`

---

## Grouped List Components

For grouped channel list:

- Header row (`GroupHeader`) and channel row (`ChannelRow`) should be modeled explicitly.
- Expand/collapse state should be owned by adapter internal state (`expandedGroups`).
- Rebuild flattened rows from source groups after state change.

Example:
- `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`

---

## Overlay and Dialog Components

### Channel Overlay

- Overlay should start hidden (`visibility="gone"`, alpha 0) and animate in/out.
- Show only on stream-ready event, and auto-hide via delayed runnable.

Examples:
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/jons/iptv/playback/PlayerEngineCoordinator.kt`

### Failure/Update Dialog

- Use coordinator to own dialog show/dismiss lifecycle.
- Enter/exit animation should be centralized in animator utility.

Examples:
- `app/src/main/java/com/jons/iptv/ui/dialog/PlaybackFailureDialogCoordinator.kt`
- `app/src/main/java/com/jons/iptv/ui/dialog/CctvStyleDialogAnimator.kt`

---

## Focusable Interaction Components

- Menu container visibility changes must preserve and restore focused item.
- RecyclerView item focus should be centered when possible after restore.

Example:
- `app/src/main/java/com/jons/iptv/ui/menu/MenuFocusCoordinator.kt`

---

## Anti-Patterns

- Calling `notifyDataSetChanged()` for every tiny item state change when targeted updates are possible.
- Spreading dialog animation code across multiple callers.
- Creating non-focusable list rows in TV-oriented navigation flows.
