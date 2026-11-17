import assert from 'node:assert/strict';
import test from 'node:test';
import { createSyncApi } from '../src/api.js';

const token = 'private-test-token';
const emptySnapshot = {
  version: 1,
  settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
  desktopBlockedDomains: [
    'facebook.com',
    'instagram.com',
    'tiktok.com',
    'x.com',
    'reddit.com',
  ],
  blocks: [],
};

function fakeStore(initial = null) {
  let value = initial;
  return {
    async read() {
      return value ?? { revision: 0, updatedAt: null, snapshot: emptySnapshot };
    },
    async replace(expectedRevision, snapshot) {
      const current = value?.revision ?? 0;
      if (current !== expectedRevision) return { conflict: true, revision: current };
      value = { revision: current + 1, updatedAt: '2026-11-04T00:00:00.000Z', snapshot };
      return { conflict: false, ...value };
    },
  };
}

function request(path, { method = 'GET', body, authorized = true } = {}) {
  return new Request(`https://sync.example${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(authorized ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

test('the health endpoint exposes readiness without disclosing sync configuration', async () => {
  const api = createSyncApi({ store: fakeStore(), token });
  const response = await api(request('/health', { authorized: false }));

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
});

test('Chrome extension preflight requests can send the bearer token', async () => {
  const api = createSyncApi({ store: fakeStore(), token });
  const response = await api(request('/v1/snapshot', { method: 'OPTIONS', authorized: false }));

  assert.equal(response.status, 204);
  assert.equal(response.headers.get('access-control-allow-origin'), '*');
  assert.match(response.headers.get('access-control-allow-headers'), /authorization/i);
});

test('snapshot access requires the configured bearer token', async () => {
  const api = createSyncApi({ store: fakeStore(), token });
  const response = await api(request('/v1/snapshot', { authorized: false }));

  assert.equal(response.status, 401);
  assert.deepEqual(await response.json(), { error: 'unauthorized' });
});

test('a valid Android snapshot is revisioned and returned to other devices', async () => {
  const store = fakeStore();
  const api = createSyncApi({ store, token });
  const snapshot = {
    ...emptySnapshot,
    blocks: [{
      id: 'focus-1',
      date: '2026-11-05',
      name: 'Study',
      startTime: '09:00',
      endTime: '10:00',
      accessProfile: 'ESSENTIALS_WITH_CHATGPT',
      completionStatus: 'NOT_STARTED',
    }],
  };
  const write = await api(request('/v1/snapshot', {
    method: 'PUT',
    body: { expectedRevision: 0, snapshot },
  }));

  assert.equal(write.status, 200);
  assert.equal((await write.json()).revision, 1);

  const read = await api(request('/v1/snapshot'));
  assert.equal(read.status, 200);
  assert.deepEqual((await read.json()).snapshot, snapshot);
});

test('concurrent edits receive a conflict instead of overwriting newer data', async () => {
  const api = createSyncApi({ store: fakeStore(), token });
  const first = await api(request('/v1/snapshot', {
    method: 'PUT',
    body: { expectedRevision: 0, snapshot: emptySnapshot },
  }));
  assert.equal(first.status, 200);

  const stale = await api(request('/v1/snapshot', {
    method: 'PUT',
    body: { expectedRevision: 0, snapshot: emptySnapshot },
  }));
  assert.equal(stale.status, 409);
  assert.deepEqual(await stale.json(), { error: 'revision_conflict', currentRevision: 1 });
});

test('a Chrome study session is shared with Android without changing the plan', async () => {
  const store = fakeStore();
  let session = { revision: 0, updatedAt: null, session: null };
  store.readSession = async () => session;
  store.replaceSession = async (expectedRevision, next) => {
    if (expectedRevision !== session.revision) return { conflict: true, revision: session.revision };
    session = { revision: session.revision + 1, updatedAt: '2026-11-16T12:00:00.000Z', session: next };
    return { conflict: false, ...session };
  };
  const api = createSyncApi({ store, token });
  const started = {
    id: 'study-1', startedAt: '2026-11-16T12:00:00.000Z', durationMillis: 30 * 60_000,
    state: 'ACTIVE', accessProfile: 'ESSENTIALS_ONLY', taskName: '',
  };
  const write = await api(request('/v1/session', { method: 'PUT', body: { expectedRevision: 0, session: started } }));
  assert.equal(write.status, 200);
  const read = await api(request('/v1/session'));
  assert.deepEqual((await read.json()).session, started);
  assert.deepEqual((await api(request('/v1/snapshot')).then(response => response.json())).snapshot, emptySnapshot);
  const conflict = await api(request('/v1/session', { method: 'PUT', body: { expectedRevision: 0, session: started } }));
  assert.equal(conflict.status, 409);
});

test('study session writes require authorization and a valid duration', async () => {
  const api = createSyncApi({ store: fakeStore(), token });
  const session = { id: 'study-1', startedAt: '2026-11-16T12:00:00.000Z',
    durationMillis: -1, state: 'ACTIVE', accessProfile: 'ESSENTIALS_ONLY', taskName: '' };
  const body = { expectedRevision: 0, session };
  assert.equal((await api(request('/v1/session', { method: 'PUT', body, authorized: false }))).status, 401);
  const invalid = await api(request('/v1/session', { method: 'PUT', body }));
  assert.equal(invalid.status, 422);
  assert.deepEqual(await invalid.json(), { error: 'invalid_session' });
});

test('Android can import past study sessions without replacing an existing session', async () => {
  const store = fakeStore();
  const history = new Map();
  store.readHistory = async () => [...history.values()];
  store.importHistory = async sessions => {
    for (const session of sessions) if (!history.has(session.id)) history.set(session.id, session);
    return [...history.values()];
  };
  const api = createSyncApi({ store, token });
  const older = { id: 'study-old', startedAt: '2026-11-10T12:00:00.000Z',
    durationMillis: 1_800_000, state: 'REVIEWED', accessProfile: 'ESSENTIALS_ONLY', taskName: 'Reading' };
  const imported = await api(request('/v1/history', { method: 'PUT', body: { sessions: [older] } }));
  assert.equal(imported.status, 200);
  const read = await api(request('/v1/history'));
  assert.deepEqual((await read.json()).sessions, [older]);
});

test('an explicit review choice replaces one shared history record', async () => {
  const store = fakeStore();
  const remote = { id: 'study-1', startedAt: '2026-11-16T12:00:00.000Z', durationMillis: 1_800_000,
    state: 'REVIEWED', accessProfile: 'ESSENTIALS_ONLY', taskName: 'Reading', outcome: 'FINISHED' };
  let history = [remote];
  store.resolveHistory = async session => { history = [session]; return session; };
  const api = createSyncApi({ store, token });
  const phone = { ...remote, taskName: 'Math' };
  const response = await api(request('/v1/history/resolve', { method: 'PUT', body: { session: phone } }));
  assert.equal(response.status, 200);
  assert.deepEqual(history, [phone]);
});

test('invalid, overlapping blocks are rejected before storage', async () => {
  let writes = 0;
  const store = fakeStore();
  const api = createSyncApi({
    token,
    store: {
      ...store,
      async replace(...args) {
        writes += 1;
        return store.replace(...args);
      },
    },
  });
  const block = (id, startTime, endTime) => ({
    id,
    date: '2026-11-05',
    name: id,
    startTime,
    endTime,
    accessProfile: 'ESSENTIALS_ONLY',
    completionStatus: 'NOT_STARTED',
  });
  const response = await api(request('/v1/snapshot', {
    method: 'PUT',
    body: {
      expectedRevision: 0,
      snapshot: {
        ...emptySnapshot,
        blocks: [block('first', '09:00', '10:00'), block('second', '09:30', '10:30')],
      },
    },
  }));

  assert.equal(response.status, 422);
  assert.deepEqual(await response.json(), { error: 'overlapping_blocks' });
  assert.equal(writes, 0);
});
