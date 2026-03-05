# Database Schema Change Thinking Guide (Android)

> Purpose: ensure schema/data contract changes are deployed safely.

---

## Why This Guide?

Schema change is not only code change. It also requires:

1. migration/update plan
2. data normalization strategy
3. verification queries/checks

Skipping any step creates mixed-format data and runtime bugs.

---

## Checklist

### 1) Contract Change Scope

- [ ] What data contract changed? (field type/unit/meaning)
- [ ] Which files/modules consume this field?
- [ ] Is there existing stored data requiring conversion?

### 2) Code Changes

- [ ] Update model/repository/parser code
- [ ] Update all read/write paths
- [ ] Update docs/spec for the contract

### 3) Data Migration Plan

If local DB is introduced/changed later:

- [ ] define idempotent migration steps
- [ ] include rollback/recovery notes where applicable
- [ ] define verification checks after migration

### 4) Verification

- [ ] Build passes (`./gradlew assembleDebug`)
- [ ] Existing data still readable
- [ ] New writes follow new contract
- [ ] No mixed-format outputs observed

---

## Timestamp/Unit Change Mini-Checklist

When changing time unit/format semantics:

- [ ] repository conversion point is explicit and single-owner
- [ ] old data conversion strategy defined
- [ ] UI display path tested with old + new records
- [ ] ordering/sorting behavior validated

---

## Common Pitfalls

- Updating parser but not serializer/output path
- Updating new writes but leaving old stored data unconverted
- Mixing unit conversion logic across UI and repository
- Shipping contract changes without post-change verification
