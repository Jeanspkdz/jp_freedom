import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { createSyncApi } from '../src/api.js';

const readToken = 'private-read-token';
const writeToken = 'private-write-token';
const token = writeToken;
const snapshot = {
  version: 1,
  settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
  desktopBlockedDomains: ['facebook.com'],
  blocks: [],
};

test('an authorized client reads the versioned snapshot and revision', async () => {
  const expected = { revision: 4, updatedAt: '2026-11-05T12:00:00.000Z', snapshot };
  const api = createSyncApi({
    readToken,
    writeToken,
    store: { async read() { return expected; } },
  });
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    headers: { authorization: `Bearer ${token}` },
  }));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), expected);
});

test('snapshot reads require the configured bearer token', async () => {
  let reads = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: { async read() { reads += 1; return { revision: 0, updatedAt: null, snapshot }; } },
  });
  const response = await api(new Request('https://sync.example/v1/snapshot'));

  assert.equal(response.status, 401);
  assert.deepEqual(await response.json(), { error: 'unauthorized' });
  assert.equal(reads, 0);
});

test('health and browser preflight requests do not require client tokens', async () => {
  const api = createSyncApi({
    readToken,
    writeToken,
    store: { async read() { return { revision: 0, updatedAt: null, snapshot }; } },
  });
  const health = await api(new Request('https://sync.example/health'));
  const preflight = await api(new Request('https://sync.example/v1/snapshot', { method: 'OPTIONS' }));

  assert.equal(health.status, 200);
  assert.deepEqual(await health.json(), { ok: true });
  assert.equal(preflight.status, 204);
  assert.equal(preflight.headers.get('access-control-allow-origin'), '*');
  assert.match(preflight.headers.get('access-control-allow-headers'), /authorization/i);
});

test('the extension read token can fetch snapshots but cannot write them', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const read = await api(new Request('https://sync.example/v1/snapshot', {
    headers: { authorization: `Bearer ${readToken}` },
  }));
  const write = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${readToken}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot }),
  }));

  assert.equal(read.status, 200);
  assert.equal(write.status, 403);
  assert.deepEqual(await write.json(), { error: 'read_only' });
  assert.equal(writes, 0);
});

test('deployment rejects identical read and write tokens', () => {
  assert.throws(
    () => createSyncApi({ readToken, writeToken: readToken, store: {} }),
    /must be different/,
  );
});

test('an authorized Android write advances the revision and becomes readable', async () => {
  let current = { revision: 0, updatedAt: null, snapshot };
  const store = {
    async read() { return current; },
    async replace(expectedRevision, nextSnapshot) {
      if (expectedRevision !== current.revision) {
        return { conflict: true, revision: current.revision };
      }
      current = {
        revision: current.revision + 1,
        updatedAt: '2026-11-05T13:00:00.000Z',
        snapshot: nextSnapshot,
      };
      return { conflict: false, ...current };
    },
  };
  const api = createSyncApi({ readToken, writeToken, store });
  const nextSnapshot = {
    ...snapshot,
    blocks: [{
      id: 'study-1',
      date: '2026-11-06',
      name: 'Study',
      startTime: '09:00',
      endTime: '10:00',
      accessProfile: 'ESSENTIALS_WITH_CHATGPT',
      completionStatus: 'NOT_STARTED',
      note: 'Review exercise four',
    }],
  };
  const write = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot: nextSnapshot }),
  }));

  assert.equal(write.status, 200);
  assert.equal((await write.json()).revision, 1);
  const read = await api(new Request('https://sync.example/v1/snapshot', {
    headers: { authorization: `Bearer ${token}` },
  }));
  assert.deepEqual((await read.json()).snapshot, nextSnapshot);
});

test('a stale Android write conflicts and preserves the newer snapshot', async () => {
  const currentSnapshot = { ...snapshot, settings: { requiredBlockCount: 4, eveningCutoff: '22:00' } };
  let current = { revision: 1, updatedAt: '2026-11-05T13:00:00.000Z', snapshot: currentSnapshot };
  const store = {
    async read() { return current; },
    async replace(expectedRevision, nextSnapshot) {
      if (expectedRevision !== current.revision) {
        return { conflict: true, revision: current.revision };
      }
      current = { ...current, revision: current.revision + 1, snapshot: nextSnapshot };
      return { conflict: false, ...current };
    },
  };
  const api = createSyncApi({ readToken, writeToken, store });
  const stale = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot }),
  }));

  assert.equal(stale.status, 409);
  assert.deepEqual(await stale.json(), { error: 'revision_conflict', currentRevision: 1 });
  assert.deepEqual(current.snapshot, currentSnapshot);
});

test('snapshot writes reject impossible calendar dates before storage', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const invalidSnapshot = {
    ...snapshot,
    blocks: [{
      id: 'study-1',
      date: '2026-02-30',
      name: 'Study',
      startTime: '09:00',
      endTime: '10:00',
      accessProfile: 'ESSENTIALS_ONLY',
      completionStatus: 'NOT_STARTED',
    }],
  };
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot: invalidSnapshot }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_blocks' });
  assert.equal(writes, 0);
});

test('snapshot writes reject blocks that do not have a valid increasing time range', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const invalidSnapshot = {
    ...snapshot,
    blocks: [{
      id: 'study-1',
      date: '2026-11-06',
      name: 'Study',
      startTime: '10:00',
      endTime: '09:00',
      accessProfile: 'ESSENTIALS_ONLY',
      completionStatus: 'NOT_STARTED',
    }],
  };
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot: invalidSnapshot }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_blocks' });
  assert.equal(writes, 0);
});

test('snapshot writes reject unsupported access profiles before storage', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const invalidSnapshot = {
    ...snapshot,
    blocks: [{
      id: 'study-1',
      date: '2026-11-06',
      name: 'Study',
      startTime: '09:00',
      endTime: '10:00',
      accessProfile: 'ALLOW_EVERYTHING',
      completionStatus: 'NOT_STARTED',
    }],
  };
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot: invalidSnapshot }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_blocks' });
  assert.equal(writes, 0);
});

test('snapshot writes reject unsupported completion statuses before storage', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const invalidSnapshot = {
    ...snapshot,
    blocks: [{
      id: 'study-1',
      date: '2026-11-06',
      name: 'Study',
      startTime: '09:00',
      endTime: '10:00',
      accessProfile: 'ESSENTIALS_ONLY',
      completionStatus: 'DONE',
    }],
  };
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot: invalidSnapshot }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_blocks' });
  assert.equal(writes, 0);
});

test('snapshot writes reject invalid required block counts before storage', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      expectedRevision: 0,
      snapshot: { ...snapshot, settings: { ...snapshot.settings, requiredBlockCount: 0 } },
    }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_settings' });
  assert.equal(writes, 0);
});

test('snapshot writes accept the largest required block count supported by Android', async () => {
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace(expectedRevision, nextSnapshot) {
        return { conflict: false, revision: expectedRevision + 1, snapshot: nextSnapshot };
      },
    },
  });
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      expectedRevision: 0,
      snapshot: { ...snapshot, settings: { ...snapshot.settings, requiredBlockCount: 2147483647 } },
    }),
  }));

  assert.equal(response.status, 200);
});

test('snapshot writes accept only the configured evening cutoffs', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      expectedRevision: 0,
      snapshot: { ...snapshot, settings: { ...snapshot.settings, eveningCutoff: '20:00' } },
    }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_settings' });
  assert.equal(writes, 0);
});

test('snapshot writes reject values that are not desktop site domains', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      expectedRevision: 0,
      snapshot: { ...snapshot, desktopBlockedDomains: ['https://example.com/path'] },
    }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_blocklist' });
  assert.equal(writes, 0);
});

test('snapshot writes reject overlapping planned blocks on the same day', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const plannedBlock = (id, startTime, endTime) => ({
    id,
    date: '2026-11-06',
    name: id,
    startTime,
    endTime,
    accessProfile: 'ESSENTIALS_ONLY',
    completionStatus: 'NOT_STARTED',
  });
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      expectedRevision: 0,
      snapshot: {
        ...snapshot,
        blocks: [
          plannedBlock('first', '09:00', '10:00'),
          plannedBlock('second', '09:30', '10:30'),
        ],
      },
    }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'overlapping_blocks' });
  assert.equal(writes, 0);
});

test('the migration seeds one versioned snapshot and can be applied repeatedly', async () => {
  const sql = await readFile(new URL('../migrations/001_focus_sync_snapshot.sql', import.meta.url), 'utf8');

  assert.match(sql, /CREATE TABLE IF NOT EXISTS focus_sync_snapshot/i);
  assert.match(sql, /singleton_id SMALLINT PRIMARY KEY CHECK \(singleton_id = 1\)/i);
  assert.match(sql, /revision BIGINT NOT NULL DEFAULT 0 CHECK \(revision >= 0\)/i);
  assert.match(sql, /snapshot JSONB NOT NULL/i);
  assert.match(sql, /"version": 1/i);
  assert.match(sql, /"requiredBlockCount": 3/i);
  assert.match(sql, /"eveningCutoff": "21:00"/i);
  assert.match(sql, /"desktopBlockedDomains"/i);
  assert.match(sql, /ON CONFLICT \(singleton_id\) DO NOTHING/i);
});

test('snapshot writes reject unsupported contract versions before storage', async () => {
  let writes = 0;
  const api = createSyncApi({
    readToken,
    writeToken,
    store: {
      async read() { return { revision: 0, updatedAt: null, snapshot }; },
      async replace() { writes += 1; return { conflict: false, revision: 1, snapshot }; },
    },
  });
  const response = await api(new Request('https://sync.example/v1/snapshot', {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ expectedRevision: 0, snapshot: { ...snapshot, version: 2 } }),
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'invalid_snapshot' });
  assert.equal(writes, 0);
});

test('deployment setup keeps role tokens in Neon and documents the current CLI workflow', async () => {
  const config = await readFile(new URL('../../../neon.ts', import.meta.url), 'utf8');
  const docs = await readFile(new URL('../../../docs/sync-and-extension.md', import.meta.url), 'utf8');

  assert.match(config, /SYNC_API_READ_TOKEN:\s*process\.env\.SYNC_API_READ_TOKEN/);
  assert.match(config, /SYNC_API_WRITE_TOKEN:\s*process\.env\.SYNC_API_WRITE_TOKEN/);
  assert.doesNotMatch(config, /DATABASE_URL/);
  assert.match(docs, /pnpm dlx neon@latest link/);
  assert.match(docs, /pnpm dlx neon@latest env pull --file \.env\.local/);
  assert.match(docs, /pnpm dlx neon@latest deploy --env \.env\.local/);
  assert.match(docs, /read token/i);
  assert.match(docs, /write token/i);
});
