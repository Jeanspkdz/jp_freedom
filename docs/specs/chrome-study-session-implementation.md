# Chrome study sessions and shared timer sync

## Problem Statement

The Chrome extension blocks selected websites during planned blocks from Android, but it cannot start a timed study session. A timer started on either device does not yet create one session that restricts both Chrome and the Android phone. The extension's current light design also differs from the agreed Android visual language.

## Solution

In Chrome, choose any positive duration and start a study timer. The timer immediately blocks every selected desktop site. Android receives the same session and applies its Essentials-only access profile for the remaining duration. Android-started sessions also activate Chrome's selected-site blocking after sync. Chrome remains focused on its timer, shared site list, and connection; Android handles plans, reviews, and the remaining settings.

## User Stories

1. As a Chrome user, I want to choose any study duration, so that I can focus for 30 minutes, an hour, or another interval.
2. As a Chrome user, I want one clear action to start the chosen timer, so that focus and desktop blocking begin together.
3. As a Chrome user, I want to see remaining time and the planned end, so that I know when the session finishes.
4. As a Chrome user, I want the timer to continue after closing the management page, so that blocking persists while I work.
5. As a Chrome user, I want the active timer restored when Chrome reopens, so that I can see its true remaining time.
6. As a Chrome user, I want all selected desktop sites blocked immediately, so that I cannot open a listed distraction during the session.
7. As a Chrome user, I want selected domains and their subdomains covered, so that changing subdomains does not bypass the list.
8. As a Chrome user, I want a blocked page to show the session and end time, so that I understand why navigation stopped.
9. As a Chrome user, I want site blocking to end when the timer expires, so that normal browsing resumes at the right time.
10. As a Chrome user, I want to stop a timer early, so that its shared state and restrictions end when I intentionally stop.
11. As a Chrome user, I want selected sites locked against removal during an active session, so that I cannot weaken the blocklist mid-session.
12. As a Chrome user, I want to add sites during a session, so that new distractions are blocked immediately.
13. As a Chrome user, I want to edit the shared site list outside a session, so that it reflects my current needs.
14. As a Chrome user, I want sync status and the last successful sync visible, so that I know whether Android has received the session.
15. As a Chrome user, I want a locally started session shown as pending on Android while the phone is offline, so that I do not mistake browser blocking for phone blocking.
16. As a Chrome user, I want to start the local timer while Android is offline, so that desktop focus is immediate.
17. As an Android user, I want a Chrome-started session to use Essentials-only, so that no Android profile selector is needed in Chrome.
18. As an Android user, I want the phone to apply a Chrome-started session after sync, so that both devices join the same focus period.
19. As an Android user, I want delayed sync to apply only the remaining time, so that the phone does not restart the timer.
20. As an Android user, I want an expired session recorded without a stale restriction, so that a late sync cannot interrupt me.
21. As a user of both devices, I want an Android-started session to activate Chrome's selected-site blocklist, so that desktop focus follows the same timer.
22. As a user of both devices, I want only one active session and one review state, so that my study time is not duplicated.
23. As a user of both devices, I want an early stop to reach the other device, so that its restrictions lift when it syncs.
24. As a user of both devices, I want the original start time retained, so that disconnection cannot extend the chosen duration.
25. As a user of both devices, I want independent offline edits merged, so that changing the site list does not erase a timer action.
26. As a user of both devices, I want a choice when both edit the same value offline, so that neither change is silently discarded.
27. As a user of both devices, I want two conflicting offline timer starts preserved for my choice, so that neither attempt disappears.
28. As an existing Android user, I want my prior study history imported, so that the new sync does not lose earlier sessions.
29. As an existing Android user, I want initial import conflicts shown, so that existing server values are not silently replaced.
30. As a user, I want history retained until I explicitly clear it, so that earlier reviews remain available.
31. As a Chrome user, I want the extension to use the app's dark navy, amber, and periwinkle design language, so that both apps feel related.
32. As a Chrome user, I want a desktop layout for the timer, sites, and connection controls, so that I can use them in a full browser page.
33. As a Chrome user, I want a clear inactive state before the first valid sync, so that I do not assume protection without a valid list.
34. As an Android user, I want plan creation, reviews, and the other settings to stay in Android, so that Chrome remains focused on its three functions.
35. As a Chrome user, I want existing planning reminders to keep directing me to Android, so that the timer does not replace planning.

## Implementation Decisions

- Chrome exposes the study timer, shared desktop site blocklist, and sync connection. Android owns planned-block editing, session reviews, and general settings.
- Chrome accepts any positive duration. Starting creates a shared session with absolute start and end times, begins local browser enforcement immediately, and shows whether Android has received it.
- A Chrome-started session uses the Essentials-only Android access profile. Android applies phone restrictions on receipt for the remaining duration. Chrome never applies Android access profiles or the evening restriction.
- Chrome blocks the shared desktop site list during a planned block or an active study timer, regardless of which device started the timer. Selected entries remain locked during either focus period; new entries take effect immediately. The blocked page has no bypass.
- The sync API holds shared plans, settings, session state, study history, review data, and focus areas. Chrome writes session actions and the desktop site list. Android edits plans, reviews, and general settings. Credentials and device permissions stay local.
- Extend the revisioned sync contract with session state and history. Queue offline actions with visible pending status. Merge independent edits; ask the user about same-field conflicts. Preserve competing offline starts until the user chooses one shared session.
- Enforce one active timer and the existing review-before-next-session rule across devices. Android remains the review interface; review data is shared.
- Duration always runs from the original start time. Expiry and early stop release each device's enforcement when it observes the change. Late Android delivery never starts an already expired restriction.
- Import Android's existing history at rollout without overwriting conflicting data. Retain it until an explicit clear action.
- Use the existing single-user bearer-token connection. Before the first valid snapshot, Chrome does not claim browser protection. While offline, it enforces its last valid cached policy.
- Persist timer and schedule state in extension storage and use background alarms so closing the management page does not end enforcement.
- Apply Android's dark semantic colors and type hierarchy in a desktop layout. Choose a start-control label that clearly describes beginning a timed focus session.

## Testing Decisions

- Verify user-visible outcomes at the highest existing seams: the revisioned sync contract, effective Chrome policy and installed browser rules, and Android's timer policy. Avoid tests coupled to individual controls or storage tables.
- Cover Chrome start, expiry, early stop, Android start, delayed phone receipt, expired late receipt, browser restart, closed management page, first-sync absence, and offline cached enforcement.
- Cover all selected sites and subdomains, locked removals, immediate additions, and unrelated sites remaining available during a timer.
- Cover conflict reconciliation: unrelated edits merge, same-field edits require choice, and competing offline starts remain visible until resolved.
- Cover import of existing Android history, conflicts with server data, and explicit clearing.
- Prefer the extension's existing integration, policy, and enforcement tests, the API's HTTP tests, and Android's timer controller and sync reconciler tests as prior art.

## Out of Scope

- Creating or editing planned blocks, doing reviews, or changing general settings in Chrome.
- Android permission setup or access-profile selection in Chrome.
- Desktop evening restriction or profile-based browser allowlisting; Chrome blocks only selected desktop sites.
- Android roadmap features that have not shipped, including onboarding, weekly review, manual activity records, gap prompts, exceptional-day metadata, and configurable rest weekday.
- Multiuser accounts or a new extension distribution channel.

## Further Notes

- The user's final clarification narrowed Chrome's interface to the timer, site list, and connection after an earlier broader parity discussion.
- This supersedes issue #30's assumptions that Chrome receives no timer history and that the new design follows system light/dark preference. The agreed Chrome design uses the fixed dark navy palette; issue #30's other accessibility and responsive-layout guidance can still inform implementation.
- Current code synchronizes planned blocks and the site list. The shared timer contract, history import, and timer-aware browser enforcement remain to be implemented.
- The initial release assumes phone and computer share a time zone for planned blocks. Timer start and end are absolute instants.
- Proposed high-level behavior seams are the revisioned sync request and effective device policy. Existing integration and policy tests provide prior art.
