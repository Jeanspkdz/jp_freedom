import assert from 'node:assert/strict';
import test from 'node:test';
import { validSnapshotResponse } from '../src/sync-client.js';

const validResponse = {
  revision: 1,
  updatedAt: '2026-11-05T10:00:00.000Z',
  snapshot: {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['example.com'],
    blocks: [],
  },
};

test('sync snapshots accept bare domains and reject URL values', () => {
  assert.equal(validSnapshotResponse(validResponse), true);
  assert.equal(validSnapshotResponse({
    ...validResponse,
    snapshot: { ...validResponse.snapshot, desktopBlockedDomains: ['https://example.com/path'] },
  }), false);
});

test('sync snapshots accept an Android-started session with absolute start and end instants', () => {
  const activeStudySession = {
    id: 'session-1', taskName: 'Study',
    startedAt: '2026-11-05T10:00:00.000Z', endsAt: '2026-11-05T11:00:00.000Z',
    accessProfile: 'ESSENTIALS_ONLY',
  };
  assert.equal(validSnapshotResponse({
    ...validResponse,
    snapshot: { ...validResponse.snapshot, activeStudySession },
  }), true);
  assert.equal(validSnapshotResponse({
    ...validResponse,
    snapshot: { ...validResponse.snapshot, activeStudySession: { ...activeStudySession, endsAt: 'invalid' } },
  }), false);
});
