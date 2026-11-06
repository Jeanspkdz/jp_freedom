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

test('Chrome reminds at the cutoff, asks the user to plan on Android, and repeats every 30 minutes', async () => {
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
  assert.match(chrome.notifications.shown[0].options.message, /Open JP Freedom on your phone/);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).periodInMinutes, 30);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).delayInMinutes, 30);
  assert.equal(
    chrome.alarms.records.get(PLANNING_REMINDER_CUTOFF_ALARM).when,
    new Date(2026, 10, 6, 21, 0).getTime(),
  );
});

test('Chrome stops reminders when the plan is complete and resumes after it falls below the minimum', async () => {
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

  snapshot = { ...snapshot, blocks: [{ date: '2026-11-06' }, { date: '2026-11-06' }] };
  await scheduler.reconcile();
  assert.equal(chrome.alarms.records.has(PLANNING_REMINDER_ALARM), false);
  assert.deepEqual(chrome.notifications.cleared, [PLANNING_REMINDER_NOTIFICATION]);

  snapshot = { ...snapshot, blocks: snapshot.blocks.slice(0, 1) };
  await scheduler.reconcile();
  assert.equal(chrome.notifications.shown.length, 3);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).periodInMinutes, 30);
});

test('the cutoff alarm checks Monday after Sunday and Saturday does not require Sunday', async () => {
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
    new Date(2026, 10, 8, 21, 0).getTime(),
  );
  now = new Date(2026, 10, 8, 21, 0);
  snapshot = { ...snapshot, blocks: [] };
  await scheduler.onAlarm(PLANNING_REMINDER_CUTOFF_ALARM);
  assert.match(chrome.notifications.shown[0].options.message, /2026-11-09 has 0 of 1/);
});
