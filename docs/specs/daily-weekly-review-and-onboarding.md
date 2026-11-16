# Daily and weekly review, activity record, and onboarding

> Sync note: ADR-0003 supersedes the Android-local study tracking decision in this older spec. Session and review history are shared through the sync API in the agreed cross-device design. Chrome does not expose the review interface.

## Problem Statement

The Android daily review is a plain list of timer sessions and planned blocks. It shows how long the user studied but not where that time went, what they accomplished, or what happened during unrecorded parts of the waking day. The app has no weekly review or first-run explanation. A fixed Sunday rest day does not fit a user who wants to choose one regular rest weekday.

## Solution

Give the user a clear account of each waking day and Monday-to-Sunday week. Study time is grouped by user-defined focus area and compared with the daily study-hours target. Session reviews capture what the user did and what remains. The user can record activities without a timer, including explicitly marked manual study that counts toward the target. The app invites the user to explain long unrecorded intervals and days with no records, without demanding an account of every minute. A five-phase, interactive onboarding explains the product and sets up its essential choices. One configurable weekday is the recurring rest day, and the user can mark exceptional days with a reason.

## User Stories

1. As a new user, I want to understand the purpose of the app in a visually engaging welcome, so that I know why I would use it.
2. As a new user, I want to see a live preview change as I configure the app, so that the setup feels connected to my future reviews.
3. As a new user, I want to set my daily study-hours target, so that progress reflects my own goal.
4. As a new user, I want to create focus areas such as English or an app project, so that study time can be grouped by what matters to me.
5. As a new user, I want to set my usual waking hours, so that the app does not ask about time spent asleep.
6. As a new user, I want to select one recurring rest weekday, so that the app respects my weekly rhythm.
7. As a new user, I want to choose the daily review reminder time, so that the review arrives at a useful moment.
8. As a new user, I want to skip onboarding and adjust settings later, so that setup does not block me from using the app.
9. As a new user, I want to create my first planned block or start my first timer at the end of onboarding, so that setup leads to a real action.
10. As a returning user, I want onboarding to stay completed, so that I do not repeat it on every launch.
11. As a user, I want each study timer session assigned to one focus area, so that its time appears in the right daily and weekly totals.
12. As a user with older sessions, I want them shown as Unassigned and their time preserved, so that existing history is not lost.
13. As a user, I want to classify an older session later, so that my historical area totals can become more useful.
14. As a user completing a session review, I want to record what I did and what remains, so that my reviews describe concrete progress.
15. As a user, I want to record an activity without running a timer, so that the review can include other parts of my day.
16. As a user, I want an activity to have a description and optional duration, so that logging an ordinary activity stays quick.
17. As a user who studied without a timer, I want to mark an activity as study and enter its duration, so that the time counts toward my target.
18. As a user adding manual time, I want occupied times unavailable in the picker, so that the same minutes are not counted twice.
19. As a user, I want to record several activities within one unrecorded interval, so that the account matches what I remember.
20. As a user, I want to leave part of an interval unexplained, so that the app does not force me to invent details.
21. As a user, I want one gentle prompt after more than two waking hours without a record, so that I can remember what I did without repeated alerts.
22. As a user, I want an unanswered prompt to appear in the daily review, so that I can complete it later.
23. As a user with no records for a day, I want one invitation to explain what happened, so that an illness, trip, or surprise can be represented without a string of empty intervals.
24. As a user, I want to mark an exceptional day with a reason, so that the review does not frame an unmet target as a failure.
25. As a user, I want to change my waking hours for an unusual day, so that gap prompts match that day.
26. As a user, I want the daily review to lead with actual study time against my target, so that I understand the result at a glance.
27. As a user, I want to see timer time and manually added study time distinguished, so that I know where the total came from.
28. As a user, I want to see study time by focus area, so that I know where my effort went.
29. As a user, I want to see my sessions, other activities, and progress in chronological order, so that the day reads as a story.
30. As a user, I want planned blocks shown as secondary plan details, so that plans are not mistaken for recorded study time.
31. As a user, I want the review to use encouraging, factual language, so that it helps me reflect without judging an incomplete day.
32. As a user, I want a calm animation of the progress and timeline, so that the review feels engaging without slowing me down.
33. As a user, I want the weekly review to show a Monday-to-Sunday chart of study hours by day, so that I can see the pattern of my week.
34. As a user, I want the weekly review to lead with study time by focus area, so that I can see how my time was distributed.
35. As a user, I want the weekly review to gather concrete advances, so that I can remember what I achieved beyond a time total.
36. As a user, I want my selected rest day shown in daily and weekly reviews, so that a day without study is not treated as a missed target.
37. As a user, I want to study voluntarily on my rest day, so that the app records my time while leaving the day free of a required target.
38. As a user, I want to change my regular rest weekday in settings, so that my routine can change without a weekly setup task.
39. As a user, I want previously reviewed days to retain their recorded rest or exceptional status, so that changing settings does not rewrite the story of past weeks.

## Implementation Decisions

- Android owns study and activity history locally, consistent with the existing Android-local study-tracking decision. Planned blocks continue their existing sync behavior; the Chrome extension does not receive personal review history.
- Extend the existing study timer session and session-review flow with one optional focus area and a clear “what I did” / “what remains” prompt. Preserve existing notes and older sessions; older unclassified sessions appear as Unassigned.
- Persist focus areas, manual activity records, waking-hour settings, the recurring rest weekday, exceptional-day reasons, and onboarding completion locally. Manual study requires a start/end interval and explicit study classification; other activities may omit duration.
- Derive the daily and weekly views from one review projection over local sessions, activity records, day metadata, settings, and planned blocks. The projection is the primary behavioral seam. Day and week views use the same underlying records rather than duplicate entries.
- Count completed timer time and explicitly marked manual study intervals toward actual study time. Planned-block duration does not count as actual study. Present timer and manual components separately within the total.
- The manual time picker excludes already occupied recorded intervals. Multiple activities may fill an unrecorded interval, and uncovered time remains allowed. Never count a minute twice.
- Detect unrecorded intervals only within the user's waking hours. After a continuous interval exceeds two hours, issue at most one gentle prompt for that interval. Defer unanswered intervals to the daily review; a fully unrecorded day uses one whole-day invitation.
- Onboarding has five phases: animated value preview, target, focus areas, daily rhythm, and first real action. The daily-rhythm phase covers waking hours, recurring rest weekday, and review reminder. The final phase offers first planned block as the primary action and first timer as an alternative. Onboarding is skippable, resumable, and shown only until completed or dismissed.
- The rest weekday is a single recurring setting chosen at onboarding and editable in settings. Replace the current Sunday-specific planning rule with the selected weekday. The app's features remain available on the rest day; voluntary planning and timer sessions still work.
- An exceptional day is a user-marked day with an optional reason. Its review avoids unmet-target language. The exceptional-day marker is distinct from the recurring rest day; it does not silently change the existing evening planning restriction.
- Daily review hierarchy: actual study versus target; time by focus area; chronological sessions, progress, and activities; unresolved gaps; secondary planned-block detail. On a rest or exceptional day, show that context rather than an unmet-target state.
- Weekly review covers Monday through Sunday and leads with focus-area distribution, then hours by day, progress highlights, other activities, and rest/exceptional-day context. It does not introduce a separate weekly target.
- Use a visually expressive, interactive onboarding and a calmer review surface. Apply short, purposeful motion to progress and timeline reveals; respect system reduced-motion and font-scaling settings. Empty and zero-data states need their own designed presentation.

## Testing Decisions

- Test observable user results: recorded totals, displayed area grouping, prompt eligibility, rest-day behavior, and preserved history. Avoid tests coupled to specific composables, storage tables, or animation internals.
- Use the review projection as the primary automated seam for daily and weekly behavior. Feed representative records and inspect complete review results: timer plus manual study, unassigned legacy sessions, overlap boundaries, day transitions, rest days, exceptional days, and no-record days.
- Extend existing policy tests for the recurring rest weekday and evening planning behavior. The repository already has domain-level tests for day-plan policy and daily timer review, which provide prior art.
- Exercise one high-level Android flow on a device or emulator: onboarding to first action, session review to daily review, manual interval entry, and weekly review. Check light/dark themes, large text, system Back, and reduced motion through visible behavior.
- Verify local persistence across app restart for focus areas, manual records, onboarding state, rest weekday, and exceptional days. Verify older session data still reads as Unassigned.

## Out of Scope

- Syncing study or activity history to Chrome or a server.
- Automatically inferring what the user did during an unrecorded interval.
- Assigning one timer session to multiple focus areas.
- Combining planned-block completion and recorded study time into one score.
- A separate weekly study-hours target or streak-based grading.
- Requiring the user to account for every waking minute.
- Changing the evening planning restriction when a day is marked exceptional; that policy can be specified separately if desired.

## Further Notes

- Extends issue #15's Android study tracking and daily review. The configurable recurring rest weekday intentionally supersedes that issue's preserved Saturday/Sunday planning-date behavior.
- The current daily review lists timer sessions and planned blocks as text; it has no activity record, focus-area grouping, weekly view, or onboarding. The current rest-day planning rule names Sunday directly.
- The glossary defines Daily review, Weekly review, Focus area, Activity record, Unrecorded interval, Rest day, and Exceptional day. The existing ADR for local Android study tracking remains authoritative.
- The primary seam is the review projection; existing day-plan policy tests cover the separately affected enforcement rule. This keeps the feature testable at user-visible boundaries while avoiding a test for every visual component.
