import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getActiveBlock,
  getActiveStudySession,
  getNextBlock,
  getCurrentAndNext,
  isEveningPlanIncomplete,
  getPlanningReminderStatus,
  getRequiredPlanDate,
  makeDesktopBlockRules,
} from '../src/policy.js';

const snapshot = {
  version: 1,
  settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
  desktopBlockedDomains: ['facebook.com', 'youtube.com'],
  blocks: [
    { id: 'early', date: '2026-11-04', name: 'Reading', startTime: '09:00', endTime: '10:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' },
    { id: 'focus', date: '2026-11-04', name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_WITH_CHATGPT', completionStatus: 'NOT_STARTED' },
    { id: 'later', date: '2026-11-04', name: 'Project', startTime: '13:00', endTime: '14:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' },
  ],
};

test('a planned block starts at its time and ends at its exact boundary', () => {
  assert.equal(getActiveBlock(snapshot, new Date('2026-11-04T10:00:00')), 'focus');
  assert.equal(getActiveBlock(snapshot, new Date('2026-11-04T11:00:00')), null);
});

test('Partly done and Not done keep the desktop block active until its end', () => {
  for (const completionStatus of ['PARTLY_DONE', 'NOT_DONE']) {
    const changed = {
      ...snapshot,
      blocks: snapshot.blocks.map(block => block.id === 'focus' ? { ...block, completionStatus } : block),
    };
    assert.equal(getActiveBlock(changed, new Date('2026-11-04T10:30:00')), 'focus');
  }
});

test('Finished releases the desktop block immediately', () => {
  const changed = {
    ...snapshot,
    blocks: snapshot.blocks.map(block => block.id === 'focus' ? { ...block, completionStatus: 'FINISHED' } : block),
  };
  assert.equal(getActiveBlock(changed, new Date('2026-11-04T10:30:00')), null);
});

test('the popup can identify the next upcoming block', () => {
  assert.equal(getNextBlock(snapshot, new Date('2026-11-04T11:00:00'))?.id, 'later');
});

test('the policy summary reports the same upcoming block as the standalone lookup', () => {
  const summary = getCurrentAndNext(snapshot, new Date('2026-11-04T10:30:00'));
  assert.equal(summary.current.id, 'focus');
  assert.equal(summary.next.id, 'later');
});

test('desktop evening reminders target Monday on Sunday and never block browsing', () => {
  const sundayAtCutoff = new Date('2026-11-08T21:00:00');
  assert.equal(isEveningPlanIncomplete(snapshot, sundayAtCutoff), true);
  assert.equal(isEveningPlanIncomplete({
    ...snapshot,
    blocks: [
      { id: 'm1', date: '2026-11-09', name: 'One', startTime: '09:00', endTime: '10:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' },
      { id: 'm2', date: '2026-11-09', name: 'Two', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' },
      { id: 'm3', date: '2026-11-09', name: 'Three', startTime: '11:00', endTime: '12:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' },
    ],
  }, sundayAtCutoff), false);
});

test('planning reminders start at the configured cutoff and follow the required block count', () => {
  for (const [eveningCutoff, hour] of [['21:00', 21], ['22:00', 22], ['23:00', 23]]) {
    const configured = { ...snapshot, settings: { requiredBlockCount: 3, eveningCutoff } };
    assert.equal(getPlanningReminderStatus(configured, new Date(2026, 10, 5, hour - 1, 59)), null);
    assert.deepEqual(getPlanningReminderStatus(configured, new Date(2026, 10, 5, hour)), {
      planDate: '2026-11-06', plannedCount: 0, requiredBlockCount: 3,
    });
  }
  const complete = {
    ...snapshot,
    blocks: [
      { date: '2026-11-06' }, { date: '2026-11-06' }, { date: '2026-11-06' },
    ],
  };
  assert.equal(getPlanningReminderStatus(complete, new Date(2026, 10, 5, 21)), null);
});

test('Saturday has no required plan and Sunday evening requires Monday', () => {
  assert.equal(getRequiredPlanDate(new Date(2026, 10, 7, 21)), null);
  assert.deepEqual(getPlanningReminderStatus(snapshot, new Date(2026, 10, 7, 21)), null);
  assert.equal(getRequiredPlanDate(new Date(2026, 10, 8, 21)), '2026-11-09');
  assert.deepEqual(getPlanningReminderStatus(snapshot, new Date(2026, 10, 8, 21)), {
    planDate: '2026-11-09', plannedCount: 0, requiredBlockCount: 3,
  });
});

test('blocklist rules cover configured hosts and are empty outside a focus block', () => {
  const current = { id: 'focus', date: '2026-11-04', name: 'Study', startTime: '10:00', endTime: '11:00', completionStatus: 'NOT_STARTED' };
  const rules = makeDesktopBlockRules(snapshot, current);
  assert.deepEqual(rules.map(rule => rule.condition.requestDomains[0]), ['facebook.com', 'youtube.com']);
  assert.ok(rules.every(rule => rule.action.type === 'block'));
  assert.ok(rules.every(rule => rule.condition.resourceTypes.length === 1 && rule.condition.resourceTypes[0] === 'main_frame'));
  assert.deepEqual(makeDesktopBlockRules(snapshot, null), []);
});

test('an Android-started study session activates the selected site rules until its absolute end', () => {
  const sharedSession = {
    id: 'session-1',
    taskName: 'Read chapter four',
    startedAt: '2026-11-04T10:15:00.000Z',
    endsAt: '2026-11-04T11:15:00.000Z',
    accessProfile: 'ESSENTIALS_ONLY',
  };
  const shared = { ...snapshot, activeStudySession: sharedSession };

  const now = new Date('2026-11-04T10:30:00.000Z');
  assert.equal(getActiveBlock(shared, now), null);
  assert.deepEqual(getActiveStudySession(shared, now), sharedSession);
  assert.deepEqual(makeDesktopBlockRules(shared, getActiveStudySession(shared, now)), [
    { id: 1, priority: 1, action: { type: 'block' }, condition: { requestDomains: ['facebook.com'], resourceTypes: ['main_frame'] } },
    { id: 2, priority: 1, action: { type: 'block' }, condition: { requestDomains: ['youtube.com'], resourceTypes: ['main_frame'] } },
  ]);
  assert.deepEqual(makeDesktopBlockRules(shared, getActiveStudySession(shared, new Date('2026-11-04T11:15:00.000Z'))), []);
});

test('evening restriction without an active planned block leaves desktop browsing unblocked', () => {
  const tomorrow = {
    ...snapshot,
    blocks: [{ id: 'tomorrow', date: '2026-11-05', name: 'Study', startTime: '09:00', endTime: '10:00', completionStatus: 'NOT_STARTED' }],
  };
  assert.deepEqual(makeDesktopBlockRules(tomorrow, getActiveBlock(tomorrow, new Date('2026-11-04T22:00:00'))), []);
});
