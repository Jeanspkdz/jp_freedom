import assert from 'node:assert/strict';
import test from 'node:test';
import { getActiveBlock, getDomainPermissionOrigins, getNextBlock, getNextBoundary, makeDesktopBlockRules } from '../src/policy.js';

const snapshot = {
  version: 1,
  settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
  desktopBlockedDomains: ['facebook.com', 'reddit.com'],
  blocks: [
    {
      id: 'focus',
      date: '2026-11-04',
      name: 'Study',
      startTime: '10:00',
      endTime: '11:00',
      accessProfile: 'ESSENTIALS_ONLY',
      completionStatus: 'NOT_STARTED',
    },
  ],
};

test('an active Planned block redirects top-level requests for every configured domain', () => {
  const now = new Date(2026, 10, 4, 10, 30);
  const active = getActiveBlock(snapshot, now);
  const rules = makeDesktopBlockRules(snapshot, active);

  assert.deepEqual(rules.map(rule => rule.condition.requestDomains[0]), ['facebook.com', 'reddit.com']);
  assert.deepEqual(rules.map(rule => rule.condition.resourceTypes), [['main_frame'], ['main_frame']]);
  assert.ok(rules.every(rule => rule.action.type === 'redirect'));
});

test('a Finished or ended block leaves the configured sites available', () => {
  const active = new Date(2026, 10, 4, 10, 30);
  const ended = new Date(2026, 10, 4, 11, 0);
  const finishedSnapshot = {
    ...snapshot,
    blocks: snapshot.blocks.map(block => ({ ...block, completionStatus: 'FINISHED' })),
  };

  assert.equal(getActiveBlock(finishedSnapshot, active), null);
  assert.deepEqual(makeDesktopBlockRules(finishedSnapshot, null), []);
  assert.equal(getActiveBlock(snapshot, ended), null);
  assert.deepEqual(makeDesktopBlockRules(snapshot, null), []);
});

test('the phone Evening restriction does not block desktop browsing', () => {
  const eveningSnapshot = { ...snapshot, blocks: [] };
  const evening = new Date(2026, 10, 4, 22, 0);

  assert.equal(getActiveBlock(eveningSnapshot, evening), null);
  assert.deepEqual(makeDesktopBlockRules(eveningSnapshot, null), []);
});

test('site permission requests cover each configured domain and its subdomains', () => {
  assert.deepEqual(getDomainPermissionOrigins(['facebook.com', 'reddit.com', 'facebook.com']), [
    'http://facebook.com/*',
    'http://*.facebook.com/*',
    'https://facebook.com/*',
    'https://*.facebook.com/*',
    'http://reddit.com/*',
    'http://*.reddit.com/*',
    'https://reddit.com/*',
    'https://*.reddit.com/*',
  ]);
});

test('the popup can show the next unfinished block later today', () => {
  const withNext = {
    ...snapshot,
    blocks: [...snapshot.blocks, {
      id: 'later',
      date: '2026-11-04',
      name: 'Project',
      startTime: '13:00',
      endTime: '14:00',
      accessProfile: 'ESSENTIALS_ONLY',
      completionStatus: 'NOT_STARTED',
    }],
  };

  assert.equal(getNextBlock(withNext, new Date(2026, 10, 4, 11, 0)).id, 'later');
});

test('the popup shows the next block on a later day when today has ended', () => {
  const tomorrow = {
    ...snapshot,
    blocks: [{
      id: 'tomorrow',
      date: '2026-11-05',
      name: 'Morning study',
      startTime: '09:00',
      endTime: '10:00',
      accessProfile: 'ESSENTIALS_ONLY',
      completionStatus: 'NOT_STARTED',
    }],
  };

  assert.equal(getNextBlock(tomorrow, new Date(2026, 10, 4, 22, 0)).id, 'tomorrow');
});

test('the next enforcement boundary schedules a planned block start', () => {
  const boundary = getNextBoundary(snapshot, new Date(2026, 10, 4, 9, 59));

  assert.equal(boundary.getTime(), new Date(2026, 10, 4, 10, 0).getTime());
});
