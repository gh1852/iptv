# Timestamp Precision & Unit Mismatch

> Severity: P1 - ordering/filtering/display bugs due to mixed units.

---

## Problem

A feature may accidentally compare timestamps in different units (seconds vs milliseconds), causing incorrect ordering or stale-state decisions.

---

## Current Project Context

This project currently has no active Room schema, but timestamp values may come from:

- remote payloads
- in-memory state
- lightweight persisted references

Therefore, unit normalization must happen at repository/mapper boundary.

---

## Detection Signals

- sorting appears unstable or reversed
- retry/backoff windows behave incorrectly
- "recent" items look too old/new

---

## Prevention Rules

1. Choose one unit per feature contract and document it.
2. Convert once at boundary, not repeatedly downstream.
3. Avoid mixing conversion code across UI and service layers.
4. Add a small test or manual check for ordering logic.

---

## Quick Checks

- [ ] Does repository normalize incoming timestamp unit?
- [ ] Do consumers assume the same unit consistently?
- [ ] Are comparisons done between same-unit values?
- [ ] Is display formatting separated from storage/compare values?

---

## Anti-Patterns

- comparing second-based values with millisecond-based values directly
- serializing one unit and deserializing as another without markers
- fixing only one code path while leaving fallback path unchanged
