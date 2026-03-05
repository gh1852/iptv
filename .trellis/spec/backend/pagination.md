# List Growth & Pagination Guidelines (Android)

> Pagination/list-scaling guidance adapted to current project architecture.

---

## Current Reality

The current IPTV channel flow loads a full playlist and then groups/displays channels locally.

References:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/MainActivity.kt`

---

## When to Introduce Pagination

Consider incremental loading when one or more apply:

- playlist/channel source grows enough to affect startup latency
- UI memory pressure from full-list rendering becomes noticeable
- remote API supports page/cursor semantics

---

## Baseline Rules

1. Keep pagination logic in repository/data layer.
2. Keep UI adapters agnostic of transport pagination details.
3. Define stable sort key and merge behavior before coding.
4. Clearly specify initial load vs load-more UI states.

---

## Integration Pattern (Future)

```text
Repository: fetchPage(cursor/offset) -> typed page result
Activity/Coordinator: merge and submit list
Adapter: render current snapshot only
```

---

## Existing Non-Paginated Pattern (As-Is)

- repository fetches and validates complete payload
- activity groups channels by category
- adapter presents flattened/grouped rows

Paths:
- `app/src/main/java/com/jons/iptv/data/ChannelRepository.kt`
- `app/src/main/java/com/jons/iptv/ui/GroupedChannelAdapter.kt`

---

## Anti-Patterns

- implementing page merge logic inside RecyclerView adapter
- changing item identity semantics between pages (breaks stable focus/selection)
- mixing pagination transport concerns into UI event router code
