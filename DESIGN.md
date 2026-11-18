---
name: Tally
description: A daily focus planner whose schedule determines what the phone allows.
colors:
  night-primary: "#FFC27A"
  night-on-primary: "#402D12"
  night-primary-container: "#563C1D"
  night-on-primary-container: "#FFDDB2"
  night-secondary: "#B39DDB"
  night-on-secondary: "#2E243D"
  night-secondary-container: "#47395F"
  night-on-secondary-container: "#EBDDFF"
  night-background: "#10182B"
  night-on-background: "#F2F0EA"
  night-surface: "#18233A"
  night-on-surface: "#F2F0EA"
  night-surface-variant: "#222E47"
  night-on-surface-variant: "#B4BED8"
  night-outline: "#7785A5"
  night-outline-variant: "#34415D"
  night-error: "#FFB4AB"
  night-on-error: "#690005"
  amber-block-accent: "#FFC27A"
typography:
  display-small:
    fontFamily: "Roboto Mono, monospace"
    fontSize: "36sp"
    fontWeight: 500
  headline-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "28sp"
    fontWeight: 400
  headline-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "24sp"
    fontWeight: 400
  title-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22sp"
    fontWeight: 400
  title-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
  body-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
  label-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "11sp"
    fontWeight: 500
rounded:
  action: "12dp"
  notice: "14dp"
  control: "16dp"
  agenda-row: "18dp"
  card: "20dp"
  summary: "22dp"
  timer-panel: "24dp"
  chip: "50%"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "20dp"
  2xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.night-primary}"
    textColor: "{colors.night-on-primary}"
    rounded: "{rounded.action}"
    height: "56dp"
    padding: "12dp 20dp"
  agenda-row-current:
    backgroundColor: "{colors.night-primary-container}"
    textColor: "{colors.night-on-surface}"
    rounded: "{rounded.agenda-row}"
    padding: "13dp 16dp"
  summary-panel:
    backgroundColor: "{colors.night-surface}"
    textColor: "{colors.night-on-surface}"
    rounded: "{rounded.summary}"
    padding: "20dp"
  focus-notice:
    backgroundColor: "{colors.night-secondary}"
    textColor: "{colors.night-on-secondary}"
    rounded: "{rounded.notice}"
    padding: "10dp 14dp"
  selected-duration:
    backgroundColor: "{colors.night-secondary}"
    textColor: "{colors.night-on-secondary}"
    rounded: "{rounded.control}"
    padding: "8dp 12dp"
---

# Design System: Tally

## Overview

**Creative North Star: “A live timetable board”**

Tally presents the day as a readable schedule on a deep navy canvas. Warm amber marks primary actions, studied time, and the active block; medium purple brings planned time, selected durations, and focus guidance forward. The established dark palette stays familiar while its two accents carry clear, consistent roles.

The Android app follows Material 3 for navigation, controls, and semantic color pairing. Agenda rows remain light and time ordered; grouped surfaces organize summaries and settings without turning every item into a card. System text scaling and Android navigation behavior remain intact.

**Key Characteristics:**
- Deep navy surfaces create the app's persistent night setting.
- Warm amber identifies primary actions, studied time, and active blocks.
- Medium purple highlights planned time, selected durations, and focus guidance.
- Explicit labels accompany color-coded completion and restriction states.
- Material 3 controls preserve familiar Android interaction behavior.

## Colors

The fixed dark palette pairs blue-black and slate surfaces with warm amber and medium purple accents.

### Primary
- **Warm vivid amber**: Primary actions, studied-time progress, and the current agenda treatment.
- **Deep amber container**: Tonal background for the current block and selected review states.

### Secondary
- **Medium purple**: Planned-time progress, duration choices, selected states, and focus guidance.
- **Deep purple container**: Lower-emphasis focus information where a filled amber surface would dominate.
- **Warm amber**: Studied-time progress and small timeline marks.

### Neutral
- **Night background**: Main app canvas and screen scaffolding.
- **Raised navy**: Grouped settings, review panels, and summary surfaces.
- **Slate surface**: Progress tracks, secondary controls, and neutral status badges.
- **Warm white**: Primary text and icon foregrounds on dark surfaces.
- **Pale blue-gray**: Supporting copy, secondary values, and quiet icons.
- **Blue-gray outlines**: Field edges, card boundaries, and dividers.
- **Soft coral error**: Validation and genuine error states only.

**The Role-First Rule.** Use Material semantic roles with their paired foregrounds; do not hard-code a one-off foreground against a semantic surface.

**The Amber Signal Rule.** Amber is the vivid signal for primary actions, studied time, and the current agenda treatment. Keep it off decorative elements and pair it with readable dark text.

**The Fixed-Night Rule.** Keep the established dark palette across app screens; do not substitute a light or wallpaper-derived scheme.

## Typography

**Display Font:** Android system monospace for elapsed-time digits; system sans-serif for Material roles.
**Body Font:** Android system sans-serif (Roboto on the reference device).
**Accent Font:** Android system serif, used selectively for agenda times and settings section headings.

**Character:** Familiar Android typography carries instructions and controls. Serif display moments add distinction to schedule times, while monospaced figures keep durations aligned and easy to compare.

### Hierarchy
- **Display small** (medium, 36sp): Elapsed study time in the planner ledger, using monospaced numerals.
- **Headline medium** (regular, 28sp): Study targets and schedule times; agenda times use a bold serif accent.
- **Headline small** (regular, 24sp): Screen headings and selected dates.
- **Title large** (regular, 22sp): Section headings and prominent labels.
- **Title medium** (medium, 16sp): Activity names, setting labels, and segmented controls.
- **Body medium** (regular, 14sp): Instructions and supporting descriptions.
- **Label small** (medium, 11sp): Compact metrics, dates, and captions.

**The Role-Based Type Rule.** Use Material type roles and scalable `sp` units; reserve serif and monospace accents for the existing time-led hierarchy.

## Layout

The planner uses a scrollable single column below a native top app bar. Its reading order is date, today/tomorrow selector, study ledger, daily review action, permission or restriction guidance when needed, then the chronological agenda. The full-width session action stays anchored above the system navigation area.

Agenda rows keep time, activity, and state together. The current row receives a persistent tonal fill and progress indicator; quiet rows remain transparent with thin dividers. Editors and settings scroll as forms, and their primary action remains reachable when system insets or the keyboard are present.

Use a compact 4dp-based rhythm, 20dp screen insets, and minimum 48dp touch targets. Follow Material navigation patterns on expanded layouts instead of stretching phone compositions unchanged.

**The Time-First Rule.** Sort blocks by start time and make each time label scannable beside its activity and state.

## Elevation & Depth

The interface stays mostly flat. Tonal surfaces and fine outlines group settings, timer details, and daily review content; thin dividers separate rows. Native Material state layers communicate interaction. Avoid decorative shadows.

**The Flat-Board Rule.** Use tonal depth and fine dividers to organize the schedule, not custom drop shadows.

## Shapes

Use Material 3 control shapes. Small actions use tighter rounded rectangles; notices and agenda rows use medium corners; summary and grouped panels use broader corners. Status labels remain compact pills. Outlines stay thin and tonal.

## Components

### Buttons
- **Primary:** Filled amber for the session action and task completion; preserve the paired dark foreground and 48dp minimum target.
- **Secondary:** Text and outlined Material buttons for navigation, recovery, and less frequent actions.
- **Focus action:** Filled amber for the main session action; use medium purple for duration choices and focus guidance.

### Day selector
- Use the native two-choice Material segmented control for Hoy and Mañana.
- Keep the selected day explicit through the selected treatment and label.

### Agenda rows and status chips
- Keep time, activity, and state together in each row.
- Use a deep amber fill and progress indicator for the active block.
- Keep status labels explicit; color reinforces the state but never carries it alone.
- Use amber for completed and active states, medium purple for selected durations and focus guidance, and slate for neutral states.

### Progress ledger
- Pair elapsed and target values with their captions.
- Use amber for studied time and medium purple for planned time, with the same scale and a subdued track.

### Cards and grouped surfaces
- Use outlined navy surfaces for study summaries, settings groups, and daily review sections.
- Keep agenda rows visually lighter than grouped surfaces.

### Inputs and fields
- Use native Material 3 outlined fields for activity, settings, and sync configuration.
- Place validation text beside the relevant field and reserve error color for actual errors.

### Navigation
- Use a native top app bar with screen context and a settings action on the planner.
- Keep the session action above navigation-bar insets.
- System Back returns from editor, settings, timer, restriction, and review screens to the planner.

## Do's and Don'ts

### Do:
- **Do** keep time, activity, and completion state readable together.
- **Do** use the fixed dark semantic colors from `FocusTheme`.
- **Do** use amber for primary actions and studied time, and medium purple for secondary emphasis and planned time.
- **Do** pair status colors with visible text labels.
- **Do** preserve Android touch targets, system Back, and font scaling.

### Don't:
- **Don't** replace the established palette with a new color family or dynamic wallpaper color.
- **Don't** use amber as general decoration or as the only status cue.
- **Don't** rely on a transient animation as the only signal of a changed or current state.
- **Don't** add decorative shadows to the mostly flat timetable.
- **Don't** use error red for a normal incomplete or not-started state.
