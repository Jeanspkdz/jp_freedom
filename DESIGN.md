---
name: JP Freedom
description: A daily focus planner whose schedule determines what the phone allows.
colors:
  amber-primary: "#A65608"
  white-on-primary: "#FFFFFF"
  amber-container: "#F9E0B4"
  brown-on-amber-container: "#603300"
  slate-secondary: "#50545B"
  white-on-secondary: "#FFFFFF"
  silver-secondary-container: "#E9E9ED"
  ink-on-secondary-container: "#22232A"
  warm-neutral-background: "#F7F6F6"
  ink-on-background: "#0A0A0E"
  white-surface: "#FFFEFE"
  ink-on-surface: "#0A0A0E"
  silver-surface-variant: "#EBEBEF"
  muted-ink: "#36363D"
  silver-outline: "#D4D4DA"
  pale-outline: "#E3E3E7"
  error-red: "#BA1A1A"
  white-on-error: "#FFFFFF"
  night-amber-primary: "#FFB95E"
  night-brown-on-primary: "#522C00"
  night-amber-container: "#6B3A00"
  light-on-night-amber-container: "#FFE0B4"
  night-slate-secondary: "#BDC2C8"
  night-charcoal-on-secondary: "#2D3136"
  night-slate-container: "#414148"
  light-on-night-slate-container: "#E2E2E8"
  night-background: "#141414"
  night-foreground: "#F0EFF1"
  night-surface: "#161616"
  night-surface-foreground: "#F0EFF1"
  night-surface-variant: "#2B2B30"
  night-muted-foreground: "#C5C5CC"
  night-outline: "#8A8A91"
  night-outline-variant: "#414148"
  night-error: "#FFB4AB"
  night-on-error: "#690005"
typography:
  headline:
    fontFamily: "Roboto, sans-serif"
    fontSize: "24px"
    fontWeight: 700
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22px"
    fontWeight: 600
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 400
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 600
rounded:
  icon-tile: "14px"
  agenda-row: "16px"
  summary-card: "24px"
  pill: "50%"
spacing:
  xxs: "4px"
  sm-tight: "10px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  2xl: "24px"
  3xl: "28px"
components:
  button-primary:
    backgroundColor: "{colors.amber-primary}"
    textColor: "{colors.white-on-primary}"
    rounded: "{rounded.pill}"
    padding: "12px 20px"
    height: "60px"
  agenda-row:
    backgroundColor: "{colors.white-surface}"
    textColor: "{colors.ink-on-surface}"
    rounded: "{rounded.agenda-row}"
    padding: "12px"
  agenda-row-current:
    backgroundColor: "{colors.amber-container}"
    textColor: "{colors.ink-on-surface}"
    rounded: "{rounded.agenda-row}"
    padding: "12px"
  status-chip-active:
    backgroundColor: "{colors.amber-primary}"
    textColor: "{colors.white-on-primary}"
    rounded: "{rounded.pill}"
    padding: "4px 10px"
  tomorrow-summary:
    backgroundColor: "{colors.silver-surface-variant}"
    textColor: "{colors.ink-on-surface}"
    rounded: "{rounded.summary-card}"
    padding: "12px 16px"
    height: "64px"
---
# Design System: JP Freedom

## Overview

**Creative North Star: "A live timetable board"**

JP Freedom presents the day as a time-ordered board, so the current focus state and the next commitment are easy to scan. Native Material 3 structure keeps planning familiar on Android; a quiet neutral field and a warm amber accent carry the approved direction into planner, editor, and settings screens.

**Key Characteristics:**
- Time gives the agenda its order.
- The current block and its state remain visible in the row.
- Warm amber marks active focus and the primary action.
- Native Android controls and system text scaling keep the interface familiar and readable.

**The Time-First Rule.** Order planned blocks by start time and keep their time labels easy to scan.

**The Held-State Rule.** Keep the current or completed state visible in the agenda; a transient animation is never the only signal.

## Colors

The light and dark schemes use the same semantic roles from FocusTheme. Warm amber is the primary role for the active block and main action; the pale amber container supports the selected row. Cool slate and silver roles organize supporting controls and secondary surfaces. Neutral foregrounds maintain contrast across the day and night schemes. Error roles identify a restriction or a form problem.

**The Role-First Rule.** Use semantic Material color roles so surfaces and content stay paired in both system themes.

## Typography

The interface uses the Android system sans-serif through the default Material 3 type scale. Screen headings use headline and title roles; block names, supporting instructions, time labels, and status chips use the matching title, body, and label roles. Screen and block headings use stronger weights for hierarchy. Text follows the system font scale.

**The System-Type Rule.** Use Material 3 type roles and scalable Android text units instead of assigning one-off sizes or a brand typeface.

## Layout

The planner reads from top to bottom: day selection, date, a time rail, and agenda rows ordered by start time. The time rail remains separate from each block surface, with a stable time column and a minimum-height row. Keep the tomorrow summary and primary action grouped at the bottom of the planner. Editor and settings content use a scrollable single-column form; respect status, navigation, and keyboard insets.

The layout uses the shared spacing rhythm in the frontmatter. Use the larger steps for screen insets and the smaller steps between labels, controls, and row details. Keep touch targets at Android's native minimum and allow content to grow with font scaling.

## Elevation & Depth

The visual language is mostly flat. White and variant surfaces, thin outlines, and the timeline rail separate content; no custom drop shadows are defined. Use Material 3 state layers for pressed and focused controls, and keep elevation subordinate to the schedule.

**The Flat-Board Rule.** Separate agenda rows with tonal surfaces and borders rather than decorative shadow.

## Shapes

Agenda rows use a gently rounded rectangle; the current icon tile is slightly tighter, while the tomorrow summary is broader and the main action is pill-shaped. Material 3 supplies the native shapes for segmented controls, text fields, menus, dialogs, and time pickers. Outlines stay thin and use the semantic outline roles.

## Components

**Primary action — prominent and direct.** The filled Material button spans the planner width, uses the primary role, and remains easy to tap. Its measured height and padding are recorded in the frontmatter.

**Day selector — compact and native.** Use a two-choice Material 3 segmented control for Hoy and Mañana. Preserve a clear selected state, readable labels, and a 48 dp minimum touch target.

**Agenda row — time first, state in place.** The time rail shows the 24-hour range beside a tappable block surface. Keep the current block highlighted with the amber container at 64% opacity and a primary outline at 36% opacity; ordinary rows use a neutral surface and outline. A small icon tile and a visible state label reinforce the row without replacing its activity name.

**State chips — concise and persistent.** The active chip uses the primary role; other completion states use a neutral variant. Always pair color with a text label.

**Tomorrow summary — quiet and actionable.** A tonal surface previews tomorrow's plan and opens it when selected; the primary add action remains a separate control below.

**Inputs and time controls — themed Material.** Use outlined fields for the activity and access profile. Start and end open a Compose dialog with separate numeric hour and minute fields in 24-hour format; validate 00:00–23:59 before accepting. Keep supporting and validation text directly with the relevant field.

**Navigation — Android first.** Use a contextual top app bar, Material controls, and the system Back behavior. Keep the planner available from restriction states.

## Do's and Don'ts

- Do keep the time, activity, and state readable together.
- Do use the semantic light and dark roles from FocusTheme.
- Do pair status color with an explicit text label.
- Do follow system font scaling and native Android touch behavior.
- Don't rely on a transient animation as the only signal of a changed or current block.
- Don't introduce aviation terms or airport imagery; the selected direction uses schedule ordering only.
- Don't add decorative shadows or a competing accent.
- Don't replace the approved amber role with dynamic wallpaper color.
