import { applySnapshotRules } from './enforcement.js';
import {
  PLANNING_REMINDER_ALARM,
  PLANNING_REMINDER_CUTOFF_ALARM,
  createPlanningReminderScheduler,
} from './reminder-scheduler.js';

const STATE_ALARM = 'focus-state-refresh';

export async function refreshPlanningReminders(chromeApi = globalThis.chrome, now = new Date(), alarmName = null) {
  let { lastValidSnapshot, planningReminderDate } = await chromeApi.storage.local.get([
    'lastValidSnapshot',
    'planningReminderDate',
  ]);
  const scheduler = createPlanningReminderScheduler({
    getSnapshot: async () => lastValidSnapshot?.version === 1 && Array.isArray(lastValidSnapshot.blocks)
      ? lastValidSnapshot
      : null,
    getReminderDate: async () => planningReminderDate ?? null,
    setReminderDate: async date => {
      planningReminderDate = date;
      await chromeApi.storage.local.set({ planningReminderDate: date });
    },
    alarms: chromeApi.alarms,
    notifications: chromeApi.notifications,
    iconUrl: chromeApi.runtime?.getURL?.('icons/reminder-128.png') ?? 'icons/reminder-128.png',
    now: () => now,
  });
  if (alarmName) await scheduler.onAlarm(alarmName);
  else await scheduler.reconcile();
}

function nextBoundary(snapshot, now) {
  const boundaries = [];
  for (const block of snapshot.blocks) {
    if (block.completionStatus === 'FINISHED') continue;
    const [year, month, day] = block.date.split('-').map(Number);
    for (const time of [block.startTime, block.endTime]) {
      const [hours, minutes] = time.split(':').map(Number);
      const boundary = new Date(year, month - 1, day, hours, minutes).getTime();
      if (boundary > now.getTime()) boundaries.push(boundary);
    }
  }
  return boundaries.length ? Math.min(...boundaries) : null;
}

export async function refreshFocusState(chromeApi = globalThis.chrome, now = new Date()) {
  const { lastValidSnapshot } = await chromeApi.storage.local.get(['lastValidSnapshot']);
  const snapshot = lastValidSnapshot?.version === 1 && Array.isArray(lastValidSnapshot.blocks)
    ? lastValidSnapshot
    : null;
  await applySnapshotRules(chromeApi, snapshot, now);

  await chromeApi.alarms.clear(STATE_ALARM);
  if (snapshot) {
    const boundary = nextBoundary(snapshot, now);
    if (boundary) await chromeApi.alarms.create(STATE_ALARM, { when: boundary });
    else await chromeApi.alarms.create(STATE_ALARM, { periodInMinutes: 1 });
  }
  await refreshPlanningReminders(chromeApi, now);
}

if (globalThis.chrome) {
  chrome.action.onClicked.addListener(() => chrome.runtime.openOptionsPage());
  chrome.runtime.onInstalled.addListener(() => { void refreshFocusState(); });
  chrome.runtime.onStartup.addListener(() => { void refreshFocusState(); });
  chrome.runtime.onMessage.addListener(message => {
    if (message?.type === 'refresh-focus-state') void refreshFocusState();
  });
  chrome.alarms.onAlarm.addListener(alarm => {
    if (alarm.name === STATE_ALARM) void refreshFocusState();
    else if ([PLANNING_REMINDER_ALARM, PLANNING_REMINDER_CUTOFF_ALARM].includes(alarm.name)) {
      void refreshPlanningReminders(chrome, new Date(), alarm.name);
    }
  });
}
