import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PLANNING_REMINDER_ALARM,
  PLANNING_REMINDER_CUTOFF_ALARM,
  PLANNING_REMINDER_NOTIFICATION,
  createPlanningReminderScheduler,
} from '../src/reminder-scheduler.js';

function createChromeBoundaries() {
  const alarms = new Map();
  const shown = [];
  const cleared = [];
  return {
    alarms: {
      records: alarms,
      async get(name) { return alarms.get(name); },
      async create(name, options) { alarms.set(name, { name, ...options }); },
      async clear(name) { return alarms.delete(name); },
    },
    notifications: {
      shown,
      cleared,
      async create(id, options) { shown.push({ id, options }); return id; },
      async clear(id) { cleared.push(id); return true; },
    },
  };
}

test('Chrome reminds at 21:00 to start studying and repeats every 30 minutes', async () => {
  const chrome = createChromeBoundaries();
  const scheduler = createPlanningReminderScheduler({
    getSnapshot: async () => ({
      settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
      blocks: [],
    }),
    alarms: chrome.alarms,
    notifications: chrome.notifications,
    iconUrl: 'icons/reminder-128.png',
    now: () => new Date(2026, 10, 5, 21, 0),
  });

  await scheduler.reconcile();

  assert.equal(chrome.notifications.shown.length, 1);
  assert.equal(chrome.notifications.shown[0].id, PLANNING_REMINDER_NOTIFICATION);
  assert.match(chrome.notifications.shown[0].options.message, /Start a focus session/);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).periodInMinutes, 30);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).delayInMinutes, 30);
  assert.equal(
    chrome.alarms.records.get(PLANNING_REMINDER_CUTOFF_ALARM).when,
    new Date(2026, 10, 6, 21, 0).getTime(),
  );
});

test('Chrome stops reminders after a study session starts', async () => {
  const chrome = createChromeBoundaries();
  let snapshot = { settings: { requiredBlockCount: 2, eveningCutoff: '21:00' }, blocks: [] };
  let reminderDate = null;
  const scheduler = createPlanningReminderScheduler({
    getSnapshot: async () => snapshot,
    getReminderDate: async () => reminderDate,
    setReminderDate: async date => { reminderDate = date; },
    alarms: chrome.alarms,
    notifications: chrome.notifications,
    iconUrl: 'icons/reminder-128.png',
    now: () => new Date(2026, 10, 5, 21, 0),
  });

  await scheduler.reconcile();
  await scheduler.onAlarm(PLANNING_REMINDER_ALARM);
  assert.equal(chrome.notifications.shown.length, 2);

  snapshot = { ...snapshot, activeStudySession: { startedAt: '2026-11-05T21:00:00', endsAt: '2026-11-05T21:30:00' } };
  await scheduler.reconcile();
  assert.equal(chrome.alarms.records.has(PLANNING_REMINDER_ALARM), false);
  assert.deepEqual(chrome.notifications.cleared, [PLANNING_REMINDER_NOTIFICATION]);

  await scheduler.reconcile();
  assert.equal(chrome.notifications.shown.length, 2);
});

test('the study reminder runs on weekends too', async () => {
  const chrome = createChromeBoundaries();
  let now = new Date(2026, 10, 7, 20, 59);
  let snapshot = {
    settings: { requiredBlockCount: 1, eveningCutoff: '21:00' },
    blocks: [{ date: '2026-11-08' }],
  };
  const scheduler = createPlanningReminderScheduler({
    getSnapshot: async () => snapshot,
    alarms: chrome.alarms,
    notifications: chrome.notifications,
    iconUrl: 'icons/reminder-128.png',
    now: () => now,
  });

  await scheduler.reconcile();
  assert.equal(
    chrome.alarms.records.get(PLANNING_REMINDER_CUTOFF_ALARM).when,
    new Date(2026, 10, 7, 21, 0).getTime(),
  );
  now = new Date(2026, 10, 8, 21, 0);
  snapshot = { ...snapshot, blocks: [] };
  await scheduler.onAlarm(PLANNING_REMINDER_CUTOFF_ALARM);
  assert.match(chrome.notifications.shown[0].options.message, /Start a focus session/);
});
