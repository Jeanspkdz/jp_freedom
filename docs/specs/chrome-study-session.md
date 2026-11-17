# Chrome study sessions

## Agreed scope

The Chrome extension focuses on three functions: starting a study session, managing the shared desktop site blocklist, and managing its sync connection. Plans, block creation and editing, reviews, and other settings remain in Android. Android-only permission setup is not shown in Chrome.

The extension uses the Android app's dark navy, amber, and periwinkle visual language, adapted to a full desktop management page. Its current light editorial treatment is not the target design.

## Session flow

The user chooses any study duration in Chrome and starts the session. The control's final label is a design detail; it should clearly communicate that it starts a timed focus session. The timer begins immediately, and Chrome blocks every site in the shared desktop site blocklist until the session ends. A session can also end early when the user stops it.

The same session is sent to Android. A Chrome-started session uses Android's Essentials-only access profile. Android applies phone restrictions when it receives the session; Chrome does not apply Android access profiles or the evening restriction. If Android reconnects after the timer has started, it enforces only the remaining duration. If the session has already elapsed, Android records its completion without starting a stale restriction. The session review remains in Android.

The Android app can also start a session that activates Chrome's shared site blocklist after sync. Planned blocks continue to activate that list on Chrome. Chrome's timer does not create or edit planned blocks.

## Shared state and offline behavior

The sync API is the shared record for plans, settings, session state, study history, reviews, and focus areas, even though Chrome only exposes the timer, site list, and connection controls. Both devices cache the data they use. An offline action is shown as pending until the other device receives it. Independent edits merge; edits to the same field require the user's choice. There is one active study session across devices; if both start different sessions while offline, preserve both attempts and ask the user which becomes the shared session. A session's duration is measured from its original start time.

The existing Android study history is imported on the first rollout of shared session sync, preserving conflicting data for user resolution. History is retained until the user explicitly clears it. Device permissions and API credentials remain local to each device.

## Existing implementation boundary

The extension has a manual timer, a separately versioned `/v1/session` API
resource, desktop enforcement for active sessions, and Android client sync.
The session resource stores the latest session and a separate history resource
imports prior Android sessions. Chrome offers a choice when two offline timers
conflict, and Android offers a choice for divergent reviews. End-to-end device
verification remains before this specification is fully met.
