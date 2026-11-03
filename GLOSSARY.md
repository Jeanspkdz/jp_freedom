# Focus Blocking

Terms for the personal, open-source system that coordinates blocking across an Android phone and a Chrome extension.

## Language

**Access profile**:
A named choice of apps and websites allowed on the Android phone during a focus session. The initial choices allow essentials only; essentials plus ChatGPT; or essentials plus ChatGPT and Google Search.
_Avoid_: Phone blocklist

**Desktop site blocklist**:
A phone-editable set of websites that the Chrome extension blocks during a focus session. It initially includes Facebook, Instagram, TikTok, X, YouTube, and Reddit; WhatsApp Web remains available.
_Avoid_: Access profile

**Focus session**:
A period during which the chosen access profile applies on the Android phone and the desktop site blocklist applies in Chrome. It ends at its planned time or when its planned block is marked Finished.
_Avoid_: Blocking session

**Day plan**:
A plan made on the phone for one calendar day that places focus sessions at chosen times and can be adjusted based on what happened that day.
_Avoid_: Weekly schedule, recurring schedule

**Planned block**:
A named activity with a planned start and end time in a day plan that automatically starts a focus session. Planned blocks cannot overlap. A written outcome is optional; marking the block Finished ends its focus session early.

**Completion status**:
The user's assessment of a planned block after working on it: Finished, Partly done, or Not done, with an optional note about what remains.

**Required block count**:
The configurable number of planned study or similar blocks needed for tomorrow's day plan to lift the evening phone restriction. Its default is three.

**Evening restriction**:
A restriction on most phone use after a chosen start time of 9, 10, or 11 p.m. when tomorrow's day plan has too few qualifying blocks. Calls, messages, emergency functions, and the planner remain available; the restriction ends when the plan meets the required block count and returns if the plan falls below it again. It does not restrict desktop browsing.

**Rest day**:
A Sunday free from scheduled focus sessions unless the user chooses to plan them. Sunday evening still requires planning Monday.

**Offline enforcement**:
Continued blocking on a disconnected device until the scheduled end of a focus session it has already received. Marking its planned block Finished on the phone releases the phone immediately; the extension receives that change when it reconnects.

**Offline planning**:
Creating or editing a day plan on the phone without internet access. The phone evaluates its own evening restriction from that plan and syncs changes when connectivity returns.

**Planning reminder**:
A notification from the Chrome extension every 30 minutes while the next required day plan has too few planned blocks and Chrome is open. It directs the user to plan on the phone but does not restrict desktop browsing.

**Device**:
An installation of the Android app or Chrome extension that participates in the user's focus sessions.
