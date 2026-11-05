import assert from 'node:assert/strict';
import test from 'node:test';
import {
  PLANNING_REMINDER_CUTOFF_ALARM,
  PLANNING_REMINDER_ALARM,
  PLANNING_REMINDER_NOTIFICATION,
  createPlanningReminderScheduler,
} from '../src/reminder-scheduler.js';

function createChromeBoundaries() {
  const alarmRecords = new Map();
  const notifications = [];
  const clearedNotifications = [];
  return {
    alarms: {
      records: alarmRecords,
      async get(name) { return alarmRecords.get(name); },
      async create(name, options) { alarmRecords.set(name, { name, ...options }); },
      async clear(name) { return alarmRecords.delete(name); },
    },
    notifications: {
      shown: notifications,
      cleared: clearedNotifications,
      async create(id, options) { notifications.push({ id, options }); return id; },
      async clear(id) { clearedNotifications.push(id); return true; },
    },
  };
}

test('an incomplete plan sends a phone-planning notification and repeats after 30 minutes', async () => {
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
  assert.equal(chrome.notifications.shown[0].options.type, 'basic');
  assert.equal('buttons' in chrome.notifications.shown[0].options, false);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).delayInMinutes, 30);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).periodInMinutes, 30);
});

test('the reminder repeats, stops when the plan is sufficient, and resumes if it falls short again', async () => {
  const chrome = createChromeBoundaries();
  let snapshot = {
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    blocks: [],
  };
  let currentTime = new Date(2026, 10, 5, 21, 0);
  const scheduler = createPlanningReminderScheduler({
    getSnapshot: async () => snapshot,
    alarms: chrome.alarms,
    notifications: chrome.notifications,
    iconUrl: 'icons/reminder-128.png',
    now: () => currentTime,
  });

  await scheduler.reconcile();
  await scheduler.onAlarm(PLANNING_REMINDER_ALARM);
  assert.equal(chrome.notifications.shown.length, 2);

  snapshot = {
    ...snapshot,
    blocks: [
      { date: '2026-11-06' },
      { date: '2026-11-06' },
      { date: '2026-11-06' },
    ],
  };
  currentTime = new Date(2026, 10, 5, 22, 0);
  await scheduler.reconcile();

  assert.equal(chrome.alarms.records.has(PLANNING_REMINDER_ALARM), false);
  assert.deepEqual(chrome.notifications.cleared, [PLANNING_REMINDER_NOTIFICATION]);

  snapshot = { ...snapshot, blocks: snapshot.blocks.slice(0, 2) };
  currentTime = new Date(2026, 10, 5, 22, 30);
  await scheduler.reconcile();

  assert.equal(chrome.notifications.shown.length, 3);
  assert.equal(chrome.alarms.records.get(PLANNING_REMINDER_ALARM).periodInMinutes, 30);
});

test('an incomplete cached plan is checked when the configured cutoff arrives', async () => {
  const chrome = createChromeBoundaries();
  let currentTime = new Date(2026, 10, 5, 20, 59);
  const scheduler = createPlanningReminderScheduler({
    getSnapshot: async () => ({
      settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
      blocks: [],
    }),
    alarms: chrome.alarms,
    notifications: chrome.notifications,
    iconUrl: 'icons/reminder-128.png',
    now: () => currentTime,
  });

  await scheduler.reconcile();
  assert.equal(
    chrome.alarms.records.get(PLANNING_REMINDER_CUTOFF_ALARM).when,
    new Date(2026, 10, 5, 21, 0).getTime(),
  );

  currentTime = new Date(2026, 10, 5, 21, 0);
  await scheduler.onAlarm(PLANNING_REMINDER_CUTOFF_ALARM);
  assert.equal(chrome.notifications.shown.length, 1);
});

test('a reminder keeps its target plan date across midnight until the next cutoff', async () => {
  const chrome = createChromeBoundaries();
  let currentTime = new Date(2026, 10, 5, 21, 0);
  let reminderDate = null;
  const snapshot = {
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    blocks: [
      { date: '2026-11-06' },
      { date: '2026-11-07' },
      { date: '2026-11-07' },
      { date: '2026-11-07' },
    ],
  };
  const dependencies = {
    getSnapshot: async () => snapshot,
    getReminderDate: async () => reminderDate,
    setReminderDate: async date => { reminderDate = date; },
    alarms: chrome.alarms,
    notifications: chrome.notifications,
    iconUrl: 'icons/reminder-128.png',
    now: () => currentTime,
  };
  const firstWorker = createPlanningReminderScheduler(dependencies);

  await firstWorker.reconcile();
  assert.equal(reminderDate, '2026-11-06');
  assert.match(chrome.notifications.shown[0].options.message, /2026-11-06 has 1 of 3/);

  currentTime = new Date(2026, 10, 6, 0, 30);
  const restartedWorker = createPlanningReminderScheduler(dependencies);
  await restartedWorker.onAlarm(PLANNING_REMINDER_ALARM);

  assert.equal(reminderDate, '2026-11-06');
  assert.equal(chrome.notifications.shown.length, 2);
  assert.match(chrome.notifications.shown[1].options.message, /2026-11-06 has 1 of 3/);

  currentTime = new Date(2026, 10, 6, 20, 59);
  await restartedWorker.reconcile();
  assert.equal(reminderDate, '2026-11-06');

  currentTime = new Date(2026, 10, 6, 21, 0);
  await restartedWorker.onAlarm(PLANNING_REMINDER_CUTOFF_ALARM);
  assert.equal(reminderDate, '2026-11-07');
  assert.deepEqual(chrome.notifications.cleared, [PLANNING_REMINDER_NOTIFICATION]);
});

test('the Saturday cutoff skips Sunday and the next cutoff checks Monday', async () => {
  const chrome = createChromeBoundaries();
  let currentTime = new Date(2026, 10, 7, 20, 59);
  const scheduler = createPlanningReminderScheduler({
    getSnapshot: async () => ({
      settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
      blocks: [
        { date: '2026-11-08' },
        { date: '2026-11-08' },
        { date: '2026-11-08' },
      ],
    }),
    alarms: chrome.alarms,
    notifications: chrome.notifications,
    iconUrl: 'icons/reminder-128.png',
    now: () => currentTime,
  });

  await scheduler.reconcile();
  assert.equal(
    chrome.alarms.records.get(PLANNING_REMINDER_CUTOFF_ALARM).when,
    new Date(2026, 10, 8, 21, 0).getTime(),
  );

  currentTime = new Date(2026, 10, 8, 21, 0);
  await scheduler.onAlarm(PLANNING_REMINDER_CUTOFF_ALARM);

  assert.match(chrome.notifications.shown[0].options.message, /2026-11-09 has 0 of 3/);
});
