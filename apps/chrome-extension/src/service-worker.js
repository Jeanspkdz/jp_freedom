import { applySnapshotRules } from './enforcement.js';

const STATE_ALARM = 'focus-state-refresh';

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
  });
}
