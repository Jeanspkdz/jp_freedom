import assert from 'node:assert/strict';
import test from 'node:test';
import { getActiveStudySession, makeDesktopBlockRules } from '../src/policy.js';

const session = {
  id: 'session-1', taskName: 'Read chapter four',
  startedAt: '2026-11-04T10:15:00.000Z',
  endsAt: '2026-11-04T11:15:00.000Z',
  accessProfile: 'ESSENTIALS_ONLY',
};
const snapshot = {
  version: 1,
  desktopBlockedDomains: ['facebook.com', 'youtube.com'],
  blocks: [{ id: 'legacy', date: '2026-11-04', startTime: '10:00', endTime: '11:00' }],
  activeStudySession: session,
};

test('a study session activates selected site rules until its absolute end', () => {
  const now = new Date('2026-11-04T10:30:00.000Z');
  assert.deepEqual(getActiveStudySession(snapshot, now), session);
  assert.deepEqual(makeDesktopBlockRules(snapshot, getActiveStudySession(snapshot, now)), [
    { id: 1, priority: 1, action: { type: 'block' }, condition: { requestDomains: ['facebook.com'], resourceTypes: ['main_frame'] } },
    { id: 2, priority: 1, action: { type: 'block' }, condition: { requestDomains: ['youtube.com'], resourceTypes: ['main_frame'] } },
  ]);
  assert.deepEqual(makeDesktopBlockRules(snapshot, getActiveStudySession(snapshot, new Date(session.endsAt))), []);
});

test('a legacy planned block never activates website blocking', () => {
  assert.equal(getActiveStudySession({ ...snapshot, activeStudySession: null }, new Date('2026-11-04T10:30:00.000Z')), null);
  assert.deepEqual(makeDesktopBlockRules(snapshot, null), []);
});
