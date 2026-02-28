# Improve startup orientation and channel navigation UX

## Goal
Align the IPTV app navigation and playback failure UX with the requested TV-focused flow.

## Requirements
- App launches directly in landscape orientation.
- First-level menu should display channel groups directly, without an extra outer categories layer.
- Channel list should be shown directly as grouped/expanded by channel group, not in a separate channels area.
- When all stream sources for a channel fail, show a friendly dialog with Retry and Close actions.

## Acceptance Criteria
- [x] On app startup, UI is landscape without manual rotation.
- [x] Left-side navigation no longer shows an outer categories wrapper.
- [x] Grouped channels are directly visible/expandable in one list region.
- [x] If all stream URLs fail for a selected channel, a dialog appears with Retry and Close buttons.
- [x] Retry attempts playback again for the current channel.

## Technical Notes
- Existing category/channel split is implemented via two RecyclerViews in MainActivity and activity_main.xml.
- Existing stream fallback retries next URL in MainActivity; final failure currently uses Toast.
- Keep scope focused to requested UX changes only.