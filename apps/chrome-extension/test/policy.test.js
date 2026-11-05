import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getActiveBlock,
  getNextBlock,
  isEveningPlanIncomplete,
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

test('blocklist rules cover configured hosts and are empty outside a focus block', () => {
  const current = { id: 'focus', date: '2026-11-04', name: 'Study', startTime: '10:00', endTime: '11:00', completionStatus: 'NOT_STARTED' };
  assert.deepEqual(makeDesktopBlockRules(snapshot, current).map(rule => rule.condition.requestDomains[0]), ['facebook.com', 'youtube.com']);
  assert.deepEqual(makeDesktopBlockRules(snapshot, null), []);
});
