import assert from 'node:assert/strict';
import test from 'node:test';
import { initializeManagement } from '../src/management.js';
import { initializeBlockedPage } from '../src/blocked.js';
import { refreshFocusState, refreshPlanningReminders, refreshRemoteSnapshot } from '../src/service-worker.js';

function createNode() {
  return {
    textContent: '',
    value: '',
    hidden: false,
    disabled: false,
    checked: false,
    dataset: {},
    listeners: new Map(),
    children: [],
    addEventListener(type, listener) {
      this.listeners.set(type, listener);
    },
    append(...children) { this.children.push(...children); },
  };
}

function createHarness(initialStorage = {}) {
  const nodes = new Map();
  const storage = { ...initialStorage };
  const rules = [];
  const alarms = new Map();
  const notifications = { shown: [], cleared: [] };
  const chromeApi = {
    storage: {
      local: {
        async get(keys) {
          return Object.fromEntries(keys.filter(key => key in storage).map(key => [key, storage[key]]));
        },
        async set(values) {
          Object.assign(storage, values);
        },
      },
    },
    alarms: {
      records: alarms,
      async get(name) { return alarms.get(name); },
      async create(name, options) { alarms.set(name, { name, ...options }); },
      async clear(name) { return alarms.delete(name); },
    },
    notifications: {
      shown: notifications.shown,
      cleared: notifications.cleared,
      async create(id, options) { notifications.shown.push({ id, options }); return id; },
      async clear(id) { notifications.cleared.push(id); return true; },
    },
    declarativeNetRequest: {
      async getDynamicRules() {
        return [...rules];
      },
      async updateDynamicRules({ removeRuleIds, addRules }) {
        for (const id of removeRuleIds) {
          const index = rules.findIndex(rule => rule.id === id);
          if (index !== -1) rules.splice(index, 1);
        }
        rules.push(...addRules);
      },
    },
  };
  const documentRef = {
    getElementById(id) {
      if (!nodes.has(id)) nodes.set(id, createNode());
      return nodes.get(id);
    },
    createElement() { return createNode(); },
    createTextNode(textContent) { return { textContent }; },
  };
  return { chromeApi, documentRef, nodes, rules, storage, alarms, notifications };
}

test('without a valid snapshot, management reports inactive protection and leaves browsing unblocked', async () => {
  const harness = createHarness();

  await initializeManagement({ ...harness, fetchImpl: async () => { throw new Error('unexpected request'); } });

  assert.match(harness.nodes.get('protection-state').textContent, /inactive/i);
  assert.match(harness.nodes.get('sync-state').textContent, /not connected|not synced/i);
  assert.deepEqual(
    ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com'].map(domain => harness.nodes.get(`domain-${domain}`).checked),
    [true, true, true, true, true],
  );
  assert.equal(harness.nodes.get('domain-youtube.com').checked, false);
  assert.equal(harness.nodes.get('domain-web.whatsapp.com').checked, false);
  assert.equal(harness.nodes.get('domain-facebook.com').disabled, true);
  assert.deepEqual(harness.rules, []);
});

test('a new empty server snapshot starts with the common sites selected', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: [],
    blocks: [{ id: 'focus', date: '2026-11-05', name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };

  const writes = [];
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options) => {
      if (options.method === 'PUT') {
        writes.push(JSON.parse(options.body));
        return { ok: true, json: async () => ({ revision: 1, updatedAt: '2026-11-05T10:30:00.000Z', snapshot: writes.at(-1).snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 0, updatedAt: null, snapshot }) };
    },
  });

  assert.deepEqual(
    ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com'].map(domain => harness.nodes.get(`domain-${domain}`).checked),
    [true, true, true, true, true],
  );
  assert.equal(harness.nodes.get('domain-youtube.com').checked, false);
  assert.equal(harness.nodes.get('domain-web.whatsapp.com').checked, false);
  assert.deepEqual(writes[0].snapshot.desktopBlockedDomains, ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com']);
  assert.deepEqual(harness.storage.lastValidSnapshot.desktopBlockedDomains, writes[0].snapshot.desktopBlockedDomains);
  assert.equal(writes[0].expectedRevision, 0);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com']);
});

test('an intentionally empty list at a later revision stays empty', async () => {
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: [], blocks: [] };
  const writes = [];
  await initializeManagement({
    ...harness,
    fetchImpl: async (_url, options) => {
      if (options.method === 'PUT') writes.push(JSON.parse(options.body));
      return { ok: true, json: async () => ({ revision: 4, updatedAt: '2026-11-05T10:30:00.000Z', snapshot }) };
    },
  });

  assert.equal(writes.length, 0);
  assert.deepEqual(harness.rules, []);
});

test('selected sites cannot be removed during a focus block while new sites save immediately', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: '2026-11-05', name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  const writes = [];
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options) => {
      if (options.method === 'PUT') {
        writes.push(JSON.parse(options.body));
        return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: writes.at(-1).snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 1, updatedAt: now.toISOString(), snapshot }) };
    },
  });

  assert.equal(harness.nodes.get('domain-reddit.com').disabled, true);
  assert.equal(harness.nodes.get('domain-youtube.com').disabled, false);
  await harness.nodes.get('domain-youtube.com').listeners.get('change')({ target: { checked: true } });

  assert.deepEqual(writes[0].snapshot.desktopBlockedDomains, ['reddit.com', 'youtube.com']);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com', 'youtube.com']);
});

test('a revision conflict reapplies one domain change to the latest blocklist and preserves the other snapshot fields', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 4, eveningCutoff: '22:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
  };
  const latest = { ...snapshot, desktopBlockedDomains: ['reddit.com', 'facebook.com'] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  const requests = [];
  let gets = 0;
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options) => {
      requests.push(options);
      if (options.method === 'GET') {
        gets += 1;
        const result = gets === 1 ? { revision: 3, snapshot } : { revision: 4, snapshot: latest };
        return { ok: true, json: async () => ({ ...result, updatedAt: now.toISOString() }) };
      }
      if (requests.filter(request => request.method === 'PUT').length === 1) return { ok: false, status: 409 };
      const write = JSON.parse(options.body);
      return { ok: true, json: async () => ({ revision: 5, updatedAt: now.toISOString(), snapshot: write.snapshot }) };
    },
  });

  await harness.nodes.get('domain-youtube.com').listeners.get('change')({ target: { checked: true } });

  const writes = requests.filter(request => request.method === 'PUT').map(request => JSON.parse(request.body));
  assert.equal(writes.length, 2);
  assert.equal(writes[1].expectedRevision, 4);
  assert.deepEqual(writes[1].snapshot.desktopBlockedDomains, ['reddit.com', 'facebook.com', 'youtube.com']);
  assert.deepEqual(writes[1].snapshot.settings, snapshot.settings);
  assert.deepEqual(writes[1].snapshot.blocks, snapshot.blocks);
});

test('custom sites accept bare domains and reject URLs and paths', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: [], blocks: [] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  let savedSnapshot;
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options) => {
      if (options.method === 'PUT') {
        savedSnapshot = JSON.parse(options.body).snapshot;
        return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: savedSnapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 1, updatedAt: now.toISOString(), snapshot }) };
    },
  });
  const field = harness.nodes.get('custom-domain');
  const submit = harness.nodes.get('add-domain-form').listeners.get('submit');

  field.value = 'https://example.com/path';
  await submit({ preventDefault() {} });
  assert.match(harness.nodes.get('blocklist-status').textContent, /bare domain/i);
  assert.equal(savedSnapshot, undefined);
  field.value = ' Example.com ';
  await submit({ preventDefault() {} });

  assert.deepEqual(savedSnapshot.desktopBlockedDomains, ['example.com']);
});

test('a cached plan without a configured connection is visibly offline', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: '2026-11-05', name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });

  await initializeManagement({ ...harness, now: () => now, fetchImpl: async () => { throw new Error('unexpected request'); } });

  assert.match(harness.nodes.get('sync-state').textContent, /offline.*cached plan/i);
  assert.match(harness.nodes.get('sync-error').textContent, /enter the sync API URL and bearer token/i);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
});

test('a successful sync shows the active block and planned end, saves the snapshot, and installs its rules', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['facebook.com', 'youtube.com'],
    blocks: [{ id: 'focus', date: today, name: 'Deep work', startTime: '10:00', endTime: '11:30', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'secret-token' });
  let request;

  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (url, options) => {
      request = { url, options };
      return { ok: true, json: async () => ({ revision: 4, updatedAt: now.toISOString(), snapshot }) };
    },
  });

  assert.equal(request.url, 'https://sync.example/v1/snapshot');
  assert.equal(request.options.headers.Authorization, 'Bearer secret-token');
  assert.equal(harness.nodes.get('current-block').textContent, 'Deep work');
  assert.equal(harness.nodes.get('planned-end').textContent, 'Planned end: 11:30');
  assert.match(harness.nodes.get('last-sync').textContent, /Last successful sync: (?!Never)/);
  assert.deepEqual(harness.storage.lastValidSnapshot, snapshot);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['facebook.com', 'youtube.com']);
});

test('saving the connection form stores the API URL and bearer token and starts a sync', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: [],
    blocks: [],
  };
  const harness = createHarness();
  let authorization;
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options) => {
      authorization = options.headers.Authorization;
      return { ok: true, json: async () => ({ revision: 1, updatedAt: now.toISOString(), snapshot }) };
    },
  });
  harness.nodes.get('api-url').value = 'https://sync.example';
  harness.nodes.get('api-token').value = 'entered-token';

  await harness.nodes.get('settings-form').listeners.get('submit')({ preventDefault() {} });

  assert.equal(harness.storage.apiUrl, 'https://sync.example');
  assert.equal(harness.storage.apiToken, 'entered-token');
  assert.equal(authorization, 'Bearer entered-token');
  assert.match(harness.nodes.get('sync-state').textContent, /synced/i);
});

test('a failed sync keeps cached enforcement and explains the offline state', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const cachedSnapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: today, name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const lastSuccessfulSync = '2026-11-05T09:00:00.000Z';
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'secret-token', lastValidSnapshot: cachedSnapshot, lastSuccessfulSync });

  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async () => { throw new TypeError('fetch failed'); },
  });

  assert.match(harness.nodes.get('sync-state').textContent, /offline.*cached plan/i);
  assert.match(harness.nodes.get('sync-error').textContent, /connection.*API URL/i);
  assert.equal(harness.nodes.get('domain-reddit.com').disabled, true);
  assert.match(harness.nodes.get('blocklist-status').textContent, /internet.*save/i);
  assert.equal(harness.nodes.get('current-block').textContent, 'Study');
  assert.match(harness.nodes.get('last-sync').textContent, /Last successful sync: (?!Never)/);
  assert.deepEqual(harness.storage.lastValidSnapshot, cachedSnapshot);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
});

test('a Finished block is released when its newer snapshot syncs', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const active = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: today, name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const finished = { ...active, blocks: [{ ...active.blocks[0], completionStatus: 'FINISHED' }] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'secret-token', lastValidSnapshot: active });

  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async () => ({ ok: true, json: async () => ({ revision: 5, updatedAt: now.toISOString(), snapshot: finished }) }),
  });

  assert.equal(harness.nodes.get('current-block').textContent, 'No active focus block');
  assert.deepEqual(harness.rules, []);
});

test('management explains missing site access and requests it from the user gesture', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });
  let requested;
  harness.chromeApi.permissions = {
    async contains() { return false; },
    async request({ origins }) { requested = origins; return true; },
  };

  await initializeManagement({ ...harness, now: () => now, fetchImpl: async () => { throw new Error('unexpected request'); } });

  assert.match(harness.nodes.get('permission-state').textContent, /site access.*needed/i);
  await harness.nodes.get('grant-site-access').listeners.get('click')();
  assert.deepEqual(requested, ['http://reddit.com/*', 'https://reddit.com/*']);
  assert.equal(harness.nodes.get('permission-state').textContent, 'Site access granted.');
});

test('the blocked page shows the active activity and planned end without a bypass control', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: today, name: 'Write project proposal', startTime: '10:00', endTime: '11:15', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });

  await initializeBlockedPage({ ...harness, now: () => now });

  assert.equal(harness.nodes.get('blocked-activity').textContent, 'Write project proposal');
  assert.equal(harness.nodes.get('blocked-end').textContent, 'Planned end: 11:15');
  assert.equal(harness.nodes.has('bypass'), false);
});

test('an invalid newer snapshot does not replace the last valid cached plan', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const cachedSnapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: today, name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const invalidSnapshot = {
    ...cachedSnapshot,
    desktopBlockedDomains: ['facebook.com'],
    blocks: [{ ...cachedSnapshot.blocks[0], endTime: '25:00' }],
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'secret-token', lastValidSnapshot: cachedSnapshot });

  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async () => ({ ok: true, json: async () => ({ revision: 5, snapshot: invalidSnapshot }) }),
  });

  assert.match(harness.nodes.get('sync-state').textContent, /offline.*cached plan/i);
  assert.match(harness.nodes.get('sync-error').textContent, /unsupported or incomplete plan/i);
  assert.deepEqual(harness.storage.lastValidSnapshot, cachedSnapshot);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
});

test('offline enforcement refreshes its dynamic rules at the cached block boundaries', async () => {
  const now = new Date(2026, 10, 5, 9, 59);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['youtube.com'],
    blocks: [{
      id: 'focus', date: '2026-11-05', name: 'Study', startTime: '10:00', endTime: '11:00',
      accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED',
    }],
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });
  await refreshFocusState(harness.chromeApi, now);
  assert.deepEqual(harness.rules, []);
  assert.equal(harness.alarms.get('focus-state-refresh').when, new Date(2026, 10, 5, 10, 0).getTime());

  await refreshFocusState(harness.chromeApi, new Date(2026, 10, 5, 10, 0));
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['youtube.com']);
  assert.equal(harness.alarms.get('focus-state-refresh').when, new Date(2026, 10, 5, 11, 0).getTime());
});

test('the background worker polls for Android completion and releases rules with the management page closed', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const active = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: '2026-11-05', name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
  };
  const finished = { ...active, blocks: [{ ...active.blocks[0], completionStatus: 'FINISHED' }] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: active, lastSnapshotRevision: 1 });
  await refreshFocusState(harness.chromeApi, now);
  assert.equal(harness.alarms.get('snapshot-sync-refresh').periodInMinutes, 1);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);

  await refreshRemoteSnapshot(harness.chromeApi, async () => ({
    ok: true,
    json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: finished }),
  }), now);

  assert.deepEqual(harness.storage.lastValidSnapshot, finished);
  assert.equal(harness.storage.lastSnapshotRevision, 2);
  assert.deepEqual(harness.rules, []);
});

test('overlapping background refreshes share one request and cannot replace a newer cached revision', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const cached = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: ['reddit.com'], blocks: [] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: cached, lastSnapshotRevision: 8 });
  let requests = 0;
  let release;
  const fetchImpl = () => {
    requests += 1;
    return new Promise(resolve => { release = () => resolve({ ok: true, json: async () => ({ revision: 7, updatedAt: now.toISOString(), snapshot: { ...cached, desktopBlockedDomains: [] } }) }); });
  };

  const first = refreshRemoteSnapshot(harness.chromeApi, fetchImpl, now);
  const second = refreshRemoteSnapshot(harness.chromeApi, fetchImpl, now);
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(requests, 1);
  release();
  await Promise.all([first, second]);

  assert.equal(harness.storage.lastSnapshotRevision, 8);
  assert.deepEqual(harness.storage.lastValidSnapshot, cached);
});

test('the worker reminds from cached plan data offline without installing browsing blocks', async () => {
  const now = new Date(2026, 10, 5, 21, 0);
  const cachedSnapshot = {
    version: 1,
    settings: { requiredBlockCount: 2, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
  };
  const harness = createHarness({ lastValidSnapshot: cachedSnapshot });

  await refreshFocusState(harness.chromeApi, now);

  assert.equal(harness.notifications.shown.length, 1);
  assert.match(harness.notifications.shown[0].options.message, /Open JP Freedom on your phone/);
  assert.equal(harness.alarms.get('planning-reminder-repeat').periodInMinutes, 30);
  assert.deepEqual(harness.rules, []);
});

test('the worker keeps the same plan target when a repeating reminder crosses midnight', async () => {
  const cachedSnapshot = {
    version: 1,
    settings: { requiredBlockCount: 2, eveningCutoff: '21:00' },
    desktopBlockedDomains: [],
    blocks: [],
  };
  const harness = createHarness({ lastValidSnapshot: cachedSnapshot });

  await refreshFocusState(harness.chromeApi, new Date(2026, 10, 5, 21, 0));
  await refreshPlanningReminders(harness.chromeApi, new Date(2026, 10, 6, 0, 30), 'planning-reminder-repeat');

  assert.equal(harness.storage.planningReminderDate, '2026-11-06');
  assert.match(harness.notifications.shown[0].options.message, /2026-11-06 has 0 of 2/);
  assert.match(harness.notifications.shown[1].options.message, /2026-11-06 has 0 of 2/);
});
