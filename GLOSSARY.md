# Focus Blocking

Terms for the personal, open-source system that coordinates blocking across an Android phone and a Chrome extension.

## Language

**Access profile**:
A saved list of apps allowed on the Android phone during a study timer session. Users can create named access profiles from currently installed apps; the fixed Essentials profile applies when none is chosen.
_Avoid_: Phone blocklist

**Desktop site blocklist**:
A shared set of websites that can be edited from Android or the Chrome extension and that Chrome blocks during a focus session. Facebook, Instagram, TikTok, X, and Reddit are selected initially; YouTube and WhatsApp Web remain available unless selected by the user. During a focus session, selected sites cannot be removed, but additional sites can be added; outside a session, the list can be edited freely.
_Avoid_: Access profile

**Focus session**:
A period when a manually started study timer applies its chosen access profile on the Android phone. A planned block continues to determine when Chrome applies the desktop site blocklist.
_Avoid_: Blocking session

**Day plan**:
A plan made on the phone for one calendar day that places focus sessions at chosen times and can be adjusted based on what happened that day.
_Avoid_: Weekly schedule, recurring schedule

**Planned block**:
A recommended study activity with a planned start and end time in a day plan. Planned blocks cannot overlap; they contribute their duration to the planned study-hours total and continue to schedule desktop website restrictions in Chrome, but do not automatically start an Android focus session.

**Completion status**:
The user's assessment of a planned block after working on it: Finished, Partly done, or Not done, with an optional note about what remains.

**Study-hours target**:
The configurable daily amount of study time used to compare planned study time with actual time recorded by study timer sessions. At the evening cutoff, the next required day's plan must reach this target to avoid or lift the evening restriction; its initial value is three hours.

**Evening restriction**:
A restriction on most phone use after the configured evening cutoff when the next required day's planned study blocks total less than the study-hours target. Calls, messages, emergency functions, and the planner remain available; it lifts when the plan reaches the target and does not restrict desktop browsing. An automatic post-session break temporarily pauses phone restrictions; the evening restriction resumes after the break if the plan is still short.

**Rest day**:
A Sunday free from scheduled focus sessions unless the user chooses to plan them. Sunday evening still requires planning Monday.

**Offline enforcement**:
Continued blocking on a disconnected device until the scheduled end of a focus session it has already received. Marking its planned block Finished on the phone releases the phone immediately; Chrome receives that change through background sync when it reconnects, even if the management page is closed.

**Offline planning**:
Creating or editing a day plan on the phone without internet access. The phone evaluates its own evening restriction from that plan and syncs changes when connectivity returns.

**Planning reminder**:
A notification from the Chrome extension every 30 minutes while the next required day plan is incomplete and Chrome is open. It directs the user to plan on the phone but does not restrict desktop browsing.

**Study timer session**:
A manually started, duration-based focus session with a chosen access profile and task name. It records actual elapsed study time, continues while the app is in the background or the phone restarts, and ends when its duration elapses or the user stops it early.

**Session review**:
The required update after a study timer session ends, where the user records the task name and status and may add notes or remaining work. It must be completed before another study timer session can start.

**Automatic break**:
A configurable rest period that begins after the session review is completed. Phone restrictions are off during the break; the user may skip or end it early after recording what they plan to work on next.

**Device**:
An installation of the Android app or Chrome extension that participates in the user's focus sessions.
