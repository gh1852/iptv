# XML Design System Guidelines

> Android equivalent of CSS design guidance.

---

## Resource-First Styling

Centralize visual tokens in resources:

- Colors: `res/values/colors.xml`
- Dimensions: `res/values/dimens.xml`
- Text/labels: `res/values/strings.xml`
- Theme/style: `res/values/themes.xml`
- Shape/selectors: `res/drawable/*.xml`

---

## Layout Styling Rules

1. Use resource references for repeated dimensions/colors.
2. Keep row-level spacing and text sizes consistent across related list items.
3. Prefer drawable selectors for focused/selected states instead of runtime color mutation.

Examples:
- Row selectors in `item_group_header.xml`, `item_channel.xml`
- Theme overlay definitions in `themes.xml`

Paths:
- `app/src/main/res/layout/item_group_header.xml`
- `app/src/main/res/layout/item_channel.xml`
- `app/src/main/res/values/themes.xml`

---

## Dialog Visual Consistency

Dialog animation timing/style should be centralized.

Example:
- `CctvStyleDialogAnimator` shared enter/dismiss behavior

Path:
- `app/src/main/java/com/jons/iptv/ui/dialog/CctvStyleDialogAnimator.kt`

---

## Overlay Styling

Playback overlay should use dedicated drawable background and be controlled via alpha/visibility transitions.

Example path:
- `app/src/main/res/layout/activity_main.xml`

---

## Anti-Patterns

- Hardcoding many dp/sp literals in Kotlin where reusable dimens belong in resources.
- Inconsistent focus visuals across menu/group/channel rows.
- Defining ad-hoc dialog look in code when drawable/theme resources can express it better.
