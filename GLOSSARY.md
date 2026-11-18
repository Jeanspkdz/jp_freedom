# Focus Blocking

Terms for the personal, open-source system that coordinates blocking across an Android phone and a Chrome extension.

## Language

**Allowed apps list**:
A reusable list of Android apps that remain available during a study timer session. Apps not selected in the list are blocked; the fixed Essentials list applies when no custom list is selected.
_Avoid_: Access profile, phone blocklist

**Essentials list**:
The fixed set of essential phone functions that remain available during a study timer session when no custom allowed apps list is selected. It cannot be edited or deleted.

**Desktop site blocklist**:
A shared set of websites that can be edited from Android or the Chrome extension and that Chrome blocks during a focus session. Facebook, Instagram, TikTok, X, and Reddit are selected initially; YouTube and WhatsApp Web remain available unless selected by the user. During a focus session, selected sites cannot be removed, but additional sites can be added; outside a session, the list can be edited freely.
_Avoid_: Access profile

**Focus session**:
A timed study period started on Android or Chrome. Android applies the selected allowed apps list, or Essentials, to the phone, while Chrome blocks the selected desktop sites for the session's duration.
_Avoid_: Blocking session

**Day plan**:
A plan made on the phone for one calendar day that places focus sessions at chosen times and can be adjusted based on what happened that day.
_Avoid_: Weekly schedule, recurring schedule

**Daily review**:
A summary of a person's waking day, including recorded study time, focus areas, concrete progress, and other activities. Study time alone is compared with the study-hours target.
_Avoid_: Session review

**Weekly review**:
A Monday-to-Sunday summary of recorded study time by focus area, concrete progress, and other activities.

**Focus area**:
A user-defined subject or project, such as English or an app project, to which a study timer session contributes its recorded study time.
_Avoid_: Study-hours target, task

**Activity record**:
A completed activity recorded without a study timer session. Its duration counts toward the study-hours target only when the user explicitly marks it as study and provides a duration.
_Avoid_: Study timer session

**Unrecorded interval**:
A period within a person's waking day for which no study timer session or activity record is available.

**Planned block**:
A recommended study activity with a planned start and end time in a day plan. Planned blocks cannot overlap; they contribute their duration to the planned study-hours total and continue to schedule desktop website restrictions in Chrome, but do not automatically start an Android focus session.

**Completion status**:
The user's assessment of a planned block after working on it: Finished, Partly done, or Not done, with an optional note about what remains.

**Study-hours target**:
The configurable daily amount of study time used to compare planned study time with actual time recorded by study timer sessions and explicitly marked manual study. At the evening cutoff, the next required day's plan must reach this target to avoid or lift the evening restriction; its initial value is three hours.

**Evening restriction**:
A restriction on most phone use after the configured evening cutoff when the next required day's planned study blocks total less than the study-hours target. Calls, messages, emergency functions, and the planner remain available; it lifts when the plan reaches the target and does not restrict desktop browsing. An automatic post-session break temporarily pauses phone restrictions; the evening restriction resumes after the break if the plan is still short.

**Rest day**:
The weekday selected during setup as free from required study planning each week. The user can change it in settings, study voluntarily, and continue using the app.

**Exceptional day**:
A day the user marks with a reason such as illness, travel, or an unforeseen event, so its review does not present an unmet study target as a failure.
_Avoid_: Rest day

**Offline enforcement**:
Continued blocking on a disconnected device until the scheduled end of a focus session it has already received. Marking its planned block Finished on the phone releases the phone immediately; Chrome receives that change through background sync when it reconnects, even if the management page is closed.

**Offline planning**:
Creating or editing a day plan on the phone without internet access. The phone evaluates its own evening restriction from that plan and syncs changes when connectivity returns.

**Planning reminder**:
A notification from the Chrome extension every 30 minutes while the next required day plan is incomplete and Chrome is open. It directs the user to plan on the phone but does not restrict desktop browsing.

**Study timer session**:
A manually started, duration-based focus session that records actual elapsed study time. A session started in Chrome uses the Essentials list on Android and blocks the selected desktop sites in Chrome.

**Session review**:
The required update after a study timer session ends, where the user records the task name and status and may add notes or remaining work. It must be completed before another study timer session can start.

**Automatic break**:
A configurable rest period that begins after the session review is completed. Phone restrictions are off during the break; the user may skip or end it early after recording what they plan to work on next.

**Device**:
An installation of the Android app or Chrome extension that participates in the user's focus sessions.
