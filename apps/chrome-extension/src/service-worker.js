import { applySnapshotRules } from './enforcement.js';
import { readSnapshot } from './sync-client.js';
import { readStudySession, activeStudySession, finishStudySessionIfDue, publishPendingStudySession } from './study-session.js';
import {
  PLANNING_REMINDER_ALARM,
  PLANNING_REMINDER_CUTOFF_ALARM,
  createPlanningReminderScheduler,
} from './reminder-scheduler.js';

const STATE_ALARM = 'focus-state-refresh';
const SNAPSHOT_SYNC_ALARM = 'snapshot-sync-refresh';
const INITIAL_DESKTOP_DOMAINS = ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com'];
let remoteRefreshInFlight = null;
let focusRefreshQueue = Promise.resolve();

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

export function refreshFocusState(chromeApi = globalThis.chrome, now = new Date()) {
  const refresh = async () => {
    const { lastValidSnapshot, lastStudySession, apiUrl, apiToken } = await chromeApi.storage.local.get(['lastValidSnapshot', 'lastStudySession', 'apiUrl', 'apiToken']);
    const snapshot = lastValidSnapshot?.version === 1 && Array.isArray(lastValidSnapshot.blocks)
      ? lastValidSnapshot
      : null;
    await applySnapshotRules(chromeApi, snapshot ?? (lastStudySession
      ? { desktopBlockedDomains: INITIAL_DESKTOP_DOMAINS, blocks: [] } : null), now, lastStudySession);

    await chromeApi.alarms.clear(STATE_ALARM);
    if (apiUrl && apiToken) await chromeApi.alarms.create(SNAPSHOT_SYNC_ALARM, { periodInMinutes: 1 });
    else await chromeApi.alarms.clear(SNAPSHOT_SYNC_ALARM);
    if (snapshot || lastStudySession) {
      const boundary = snapshot ? nextBoundary(snapshot, now) : null;
      const sessionEnd = activeStudySession(lastStudySession, now)
        ? Date.parse(lastStudySession.startedAt) + lastStudySession.durationMillis : null;
      const next = Math.min(...[boundary, sessionEnd].filter(value => value != null));
      if (Number.isFinite(next)) await chromeApi.alarms.create(STATE_ALARM, { when: next });
      else await chromeApi.alarms.create(STATE_ALARM, { periodInMinutes: 1 });
    }
    await refreshPlanningReminders(chromeApi, now);
  };
  focusRefreshQueue = focusRefreshQueue.then(refresh, refresh);
  return focusRefreshQueue;
}

export async function refreshRemoteSnapshot(chromeApi = globalThis.chrome, fetchImpl = globalThis.fetch, now = new Date()) {
  if (remoteRefreshInFlight) return remoteRefreshInFlight;
  remoteRefreshInFlight = (async () => {
    const { apiUrl, apiToken, lastSnapshotRevision = -1, studyPending, lastStudySession } = await chromeApi.storage.local.get([
      'apiUrl', 'apiToken', 'lastSnapshotRevision', 'studyPending', 'lastStudySession',
    ]);
    if (!apiUrl || !apiToken) return false;
    try {
      const payload = await readSnapshot(apiUrl, apiToken, fetchImpl);
      if (payload.revision < lastSnapshotRevision) return false;
      let studyConflict = false;
      const study = await (studyPending && lastStudySession
        ? publishPendingStudySession(apiUrl, apiToken, lastStudySession, fetchImpl)
        : readStudySession(apiUrl, apiToken, fetchImpl))
        .then(current => finishStudySessionIfDue(apiUrl, apiToken, current, fetchImpl, now))
        .catch(error => {
          studyConflict = /Another study session/.test(error.message);
          return null;
        });
      const lastSuccessfulSync = now.toISOString();
      await chromeApi.storage.local.set({
        lastValidSnapshot: payload.snapshot,
        ...(study ? { lastStudySession: study.session, lastStudyRevision: study.revision, studyPending: false } : {}),
        studyConflict,
        lastSnapshotRevision: payload.revision,
        lastSuccessfulSync,
      });
      await refreshFocusState(chromeApi, now);
      return true;
    } catch {
      // Keep enforcing the last valid cached snapshot until the API is reachable.
      await refreshFocusState(chromeApi, now);
      return false;
    }
  })().finally(() => { remoteRefreshInFlight = null; });
  return remoteRefreshInFlight;
}

if (globalThis.chrome) {
  chrome.action.onClicked.addListener(() => chrome.runtime.openOptionsPage());
  chrome.runtime.onInstalled.addListener(() => { void refreshFocusState(); });
  chrome.runtime.onStartup.addListener(() => {
    void refreshRemoteSnapshot().then(() => refreshFocusState());
  });
  chrome.runtime.onMessage.addListener(message => {
    if (message?.type === 'refresh-focus-state') void refreshFocusState();
  });
  chrome.alarms.onAlarm.addListener(alarm => {
    if (alarm.name === STATE_ALARM) void refreshFocusState();
    else if (alarm.name === SNAPSHOT_SYNC_ALARM) void refreshRemoteSnapshot();
    else if ([PLANNING_REMINDER_ALARM, PLANNING_REMINDER_CUTOFF_ALARM].includes(alarm.name)) {
      void refreshPlanningReminders(chrome, new Date(), alarm.name);
    }
  });
}
