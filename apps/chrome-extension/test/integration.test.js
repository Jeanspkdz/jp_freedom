import assert from 'node:assert/strict';
import test from 'node:test';
import { initializeManagement } from '../src/management.js';
import { refreshFocusState } from '../src/service-worker.js';

function createNode() {
  return {
    textContent: '',
    value: '',
    hidden: false,
    disabled: false,
    listeners: new Map(),
    addEventListener(type, listener) {
      this.listeners.set(type, listener);
    },
  };
}

function createHarness(initialStorage = {}) {
  const nodes = new Map();
  const storage = { ...initialStorage };
  const rules = [];
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
  };
  return { chromeApi, documentRef, nodes, rules, storage };
}

test('without a valid snapshot, management reports inactive protection and leaves browsing unblocked', async () => {
  const harness = createHarness();

  await initializeManagement({ ...harness, fetchImpl: async () => { throw new Error('unexpected request'); } });

  assert.match(harness.nodes.get('protection-state').textContent, /inactive/i);
  assert.match(harness.nodes.get('sync-state').textContent, /not connected|not synced/i);
  assert.deepEqual(harness.rules, []);
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
  assert.equal(harness.nodes.get('current-block').textContent, 'Study');
  assert.match(harness.nodes.get('last-sync').textContent, /Last successful sync: (?!Never)/);
  assert.deepEqual(harness.storage.lastValidSnapshot, cachedSnapshot);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
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
  let alarm = null;
  harness.chromeApi.alarms = {
    async clear() { alarm = null; },
    async create(name, options) { alarm = { name, options }; },
  };

  await refreshFocusState(harness.chromeApi, now);
  assert.deepEqual(harness.rules, []);
  assert.equal(alarm.name, 'focus-state-refresh');
  assert.equal(alarm.options.when, new Date(2026, 10, 5, 10, 0).getTime());

  await refreshFocusState(harness.chromeApi, new Date(2026, 10, 5, 10, 0));
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['youtube.com']);
  assert.equal(alarm.options.when, new Date(2026, 10, 5, 11, 0).getTime());
});
