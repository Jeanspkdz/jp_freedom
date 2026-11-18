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
    className: '',
    dataset: {},
    style: {},
    attributes: new Map(),
    listeners: new Map(),
    children: [],
    focusCount: 0,
    focus() { this.focusCount += 1; },
    setAttribute(name, value) { this.attributes.set(name, value); },
    getAttribute(name) { return this.attributes.get(name) ?? null; },
    removeAttribute(name) { this.attributes.delete(name); },
    addEventListener(type, listener) {
      this.listeners.set(type, listener);
    },
    append(...children) {
      for (const child of children) {
        if (child && typeof child === 'object') child.parentElement = this;
        this.children.push(child);
      }
    },
    replaceChildren(...children) {
      this.children = [];
      this.append(...children);
    },
    remove() {
      if (!this.parentElement) return;
      this.parentElement.children = this.parentElement.children.filter(child => child !== this);
      this.parentElement = null;
    },
  };
}

function createHarness(initialStorage = {}, page = 'all') {
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
  const navigationLinks = ['management.html', 'sites.html', 'connection.html'].map(href => {
    const link = createNode();
    link.setAttribute('href', href);
    return link;
  });
  const sessionValues = new Map();
  const documentRef = {
    title: 'Tally · Focus plan',
    documentElement: { dataset: { page }, classList: { add() {}, remove() {} } },
    location: { href: '' },
    defaultView: {
      sessionStorage: {
        setItem(key, value) { sessionValues.set(key, value); },
        getItem(key) { return sessionValues.get(key) ?? null; },
        removeItem(key) { sessionValues.delete(key); },
      },
    },
    querySelectorAll(selector) { return selector === '.view-nav a' ? navigationLinks : []; },
    getElementById(id) {
      if (!nodes.has(id)) {
        const node = createNode();
        node.id = id;
        if (id === 'focus-clock-minutes') node.dataset.value = '30';
        if (id === 'focus-clock-seconds') node.dataset.value = '00';
        nodes.set(id, node);
      }
      return nodes.get(id);
    },
    createElement() { return createNode(); },
    createTextNode(textContent) { return { textContent }; },
  };
  return { chromeApi, documentRef, navigationLinks, nodes, rules, storage, alarms, notifications, sessionValues };
}

test('without a valid snapshot, management reports inactive protection and leaves browsing unblocked', async () => {
  const harness = createHarness();

  await initializeManagement({ ...harness, fetchImpl: async () => { throw new Error('unexpected request'); } });

  assert.match(harness.nodes.get('protection-state').textContent, /inactive/i);
  assert.match(harness.nodes.get('sync-state').textContent, /not connected|not synced/i);
  assert.match(harness.nodes.get('api-url').value, /jpfreedomsync\.compute\./);
  assert.deepEqual(
    ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com'].map(domain => harness.nodes.get(`domain-${domain}`).checked),
    [true, true, true, true, true],
  );
  assert.equal(harness.nodes.get('domain-youtube.com').checked, false);
  assert.equal(harness.nodes.get('domain-web.whatsapp.com').checked, false);
  assert.equal(harness.nodes.get('domain-facebook.com').disabled, true);
  assert.deepEqual(harness.rules, []);
});

test('separate management pages use ordinary links', async () => {
  const harness = createHarness({}, 'sites');
  await initializeManagement({ ...harness, fetchImpl: async () => { throw new Error('unexpected request'); } });
  assert.deepEqual(harness.navigationLinks.map(link => link.attributes.get('href')),
    ['management.html', 'sites.html', 'connection.html']);
  harness.navigationLinks[0].listeners.get('click')({ button: 0 });
  assert.equal(harness.sessionValues.get('Tally-nav-direction'), 'back');
});

test('an unreachable local test API explains how to restore the connection', async () => {
  const harness = createHarness({ apiUrl: 'http://127.0.0.1:18787', apiToken: 'local-token' });

  await initializeManagement({
    ...harness,
    fetchImpl: async () => { throw new TypeError('fetch failed'); },
  });

  assert.equal(harness.nodes.get('sync-state').textContent, 'Offline. No plan has synced yet.');
  assert.match(harness.nodes.get('sync-error').textContent, /local sync API at 127\.0\.0\.1:18787.*start the local test server/i);
  assert.match(harness.nodes.get('sync-error').textContent, /deployed Sync API URL from Android settings/i);
});

test('the obsolete sample connection is cleared for the deployed Neon API', async () => {
  const harness = createHarness({ apiUrl: 'http://127.0.0.1:18787', apiToken: 'test-token' });

  await initializeManagement({ ...harness, fetchImpl: async () => { throw new Error('unexpected request'); } });

  assert.match(harness.nodes.get('api-url').value, /jpfreedomsync\.compute\./);
  assert.equal(harness.nodes.get('api-token').value, '');
  assert.match(harness.nodes.get('sync-state').textContent, /not connected/i);
});

test('management initializes from browser globals when called without explicit arguments', async () => {
  const harness = createHarness();
  const originalGlobals = {
    chrome: globalThis.chrome,
    document: globalThis.document,
    fetch: globalThis.fetch,
  };
  globalThis.chrome = harness.chromeApi;
  globalThis.document = harness.documentRef;
  globalThis.fetch = async () => { throw new Error('unexpected request'); };

  try {
    await initializeManagement();
    assert.match(harness.nodes.get('protection-state').textContent, /inactive/i);
  } finally {
    for (const [key, value] of Object.entries(originalGlobals)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
  }
});

test('a new empty server snapshot selects common sites without blocking until a session', async () => {
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
  assert.deepEqual(harness.rules, []);
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

test('selected sites cannot be removed during a study session while new sites save immediately', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: '2026-11-05', name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
    activeStudySession: { id: 'session', taskName: 'Study', startedAt: new Date(2026, 10, 5, 10, 0).toISOString(), endsAt: new Date(2026, 10, 5, 11, 0).toISOString(), accessProfile: 'ESSENTIALS_ONLY' },
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

test('a removed custom site disappears from the management page after sync', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshotWithCustomSite = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['example.com'],
    blocks: [],
  };
  const snapshotWithoutCustomSite = { ...snapshotWithCustomSite, desktopBlockedDomains: [] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  let syncCount = 0;
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async () => {
      syncCount += 1;
      const snapshot = syncCount === 1 ? snapshotWithCustomSite : snapshotWithoutCustomSite;
      return { ok: true, json: async () => ({ revision: syncCount, updatedAt: now.toISOString(), snapshot }) };
    },
  });

  const customSites = harness.nodes.get('custom-sites');
  const customCheckbox = customSites.children[0].children[0];
  assert.equal(customSites.children.length, 1);
  assert.equal(customCheckbox.checked, true);

  await harness.nodes.get('sync-now').listeners.get('click')();

  assert.equal(customSites.children.length, 0);
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
  assert.deepEqual(harness.rules, []);
});

test('Plan exposes the manual timer and shows its countdown after starting', async () => {
  const now = new Date('2026-11-16T12:00:00.000Z');
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: ['reddit.com'], blocks: [] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' }, 'plan');
  let session = null;
  let revision = 0;
  const fetchImpl = async (url, options) => {
    if (url.endsWith('/v1/session')) {
      if (options.method === 'PUT') {
        const body = JSON.parse(options.body);
        assert.equal(body.expectedRevision, revision);
        session = body.session;
        revision += 1;
      }
      return new Response(JSON.stringify({ revision, session }), { status: 200 });
    }
    return new Response(JSON.stringify({ revision: 1, updatedAt: now.toISOString(), snapshot }), { status: 200 });
  };
  await initializeManagement({ ...harness, fetchImpl, now: () => now });
  assert.equal(harness.nodes.get('start-study-timer').disabled, false);
  harness.documentRef.getElementById('study-duration').value = '30';
  await harness.nodes.get('study-timer-form').listeners.get('submit')({ preventDefault() {} });
  assert.equal(harness.nodes.get('study-timer-remaining').textContent, '0:30:00');
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
});

test('a timer can start without a connection and stays pending for sync', async () => {
  const now = new Date('2026-11-16T12:00:00.000Z');
  const harness = createHarness({}, 'plan');
  await initializeManagement({ ...harness, now: () => now,
    fetchImpl: async () => { throw new Error('Unexpected network request'); } });
  harness.documentRef.getElementById('study-duration').value = '30';
  assert.equal(harness.nodes.get('start-study-timer').disabled, false);
  await harness.nodes.get('study-timer-form').listeners.get('submit')({ preventDefault() {} });
  assert.equal(harness.storage.studyPending, true);
  assert.equal(harness.nodes.get('study-timer-remaining').textContent, '0:30:00');
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]),
    ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com']);
});

test('Plan lets the user choose between conflicting shared session starts', async () => {
  const now = new Date('2026-11-16T12:05:00.000Z');
  const local = { id: 'chrome-1', startedAt: '2026-11-16T12:00:00.000Z', endsAt: '2026-11-16T12:30:00.000Z',
    accessProfile: 'ESSENTIALS_ONLY', taskName: 'Study session' };
  const remote = { ...local, id: 'android-1' };
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: remote,
    sessionStartCandidates: [remote, local] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' }, 'plan');
  let chosen;
  const fetchImpl = async (_url, options = {}) => {
    if (options.method === 'PUT') {
      chosen = JSON.parse(options.body).snapshot;
      return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: chosen }) };
    }
    return { ok: true, json: async () => ({ revision: 1, updatedAt: now.toISOString(), snapshot }) };
  };
  await initializeManagement({ ...harness, fetchImpl, now: () => now });
  assert.equal(harness.nodes.get('session-conflict-controls').hidden, false);
  harness.nodes.get('session-start-choice').value = 'chrome-1';
  await harness.nodes.get('choose-session-start').listeners.get('click')();
  assert.equal(chosen.activeStudySession.id, 'chrome-1');
  assert.deepEqual(chosen.sessionStartCandidates, []);
});

test('a successful sync shows the active session and end, saves the snapshot, and installs its rules', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['facebook.com', 'youtube.com'],
    blocks: [{ id: 'focus', date: today, name: 'Deep work', startTime: '10:00', endTime: '11:30', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
    activeStudySession: { id: 'session', taskName: 'Deep work', startedAt: new Date(2026, 10, 5, 10, 0).toISOString(), endsAt: new Date(2026, 10, 5, 11, 30).toISOString(), accessProfile: 'ESSENTIALS_ONLY' },
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
  assert.equal(harness.nodes.get('planned-end').textContent, `Planned end: ${new Date(snapshot.activeStudySession.endsAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}`);
  assert.match(harness.nodes.get('last-sync').textContent, /Last successful sync: (?!Never)/);
  assert.deepEqual(harness.storage.lastValidSnapshot, snapshot);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['facebook.com', 'youtube.com']);
});

test('an Android timer snapshot shows its original end and remaining time while installing desktop blocks', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
    activeStudySession: {
      id: 'phone-session-1',
      taskName: 'Read chapter four',
      startedAt: '2026-11-05T10:15:00.000Z',
      endsAt: '2026-11-05T11:15:00.000Z',
      accessProfile: 'ESSENTIALS_ONLY',
    },
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });

  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async () => ({ ok: true, json: async () => ({ revision: 3, updatedAt: now.toISOString(), snapshot }) }),
  });

  assert.equal(harness.nodes.get('current-block').textContent, 'Read chapter four');
  assert.equal(harness.nodes.get('planned-end').textContent, `Planned end: ${new Date(snapshot.activeStudySession.endsAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}`);
  assert.equal(harness.nodes.get('focus-remaining-value').textContent, '45:00');
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
});

test('Chrome can review a pending shared study session and unblock the next timer', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const pending = {
    id: 'phone-session-review', taskName: 'Read chapter four', durationMillis: 1_800_000,
    accessProfile: 'ESSENTIALS_ONLY', startedAt: '2026-11-05T10:00:00.000Z',
    endedAt: '2026-11-05T10:30:00.000Z', actualMillis: 1_800_000,
    state: 'PENDING_REVIEW', completionStatus: null, note: '', remaining: '',
    breakStartedAt: null, breakDurationMillis: 0, nextTask: '',
  };
  const snapshot = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: [], blocks: [], activeStudySession: null,
    pendingStudyReviewSessionId: pending.id, studyHistory: [pending],
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  let saved;
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (options.method === 'PUT') {
        saved = JSON.parse(options.body);
        return { ok: true, json: async () => ({ revision: 8, updatedAt: now.toISOString(), snapshot: saved.snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 7, updatedAt: now.toISOString(), snapshot }) };
    },
  });

  assert.equal(harness.nodes.get('session-review').hidden, false);
  assert.equal(harness.nodes.get('session-review-task').value, pending.taskName);
  harness.nodes.get('session-review-status').value = 'PARTLY_DONE';
  harness.nodes.get('session-review-note').value = 'Finished the key section';
  harness.nodes.get('session-review-remaining').value = 'Review the examples';
  await harness.nodes.get('session-review').listeners.get('submit')({ preventDefault() {} });

  assert.equal(saved.expectedRevision, 7);
  assert.equal(saved.snapshot.pendingStudyReviewSessionId, null);
  assert.equal(saved.snapshot.studyHistory[0].state, 'REVIEWED');
  assert.equal(saved.snapshot.studyHistory[0].completionStatus, 'PARTLY_DONE');
  assert.equal(saved.snapshot.studyHistory[0].note, 'Finished the key section');
  assert.equal(saved.snapshot.studyHistory[0].remaining, 'Review the examples');
  assert.equal(harness.nodes.get('start-session').disabled, false);
});

test('Chrome can recover a pending review when sync only has the review id', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: [], blocks: [], activeStudySession: null,
    pendingStudyReviewSessionId: 'legacy-phone-session', studyHistory: [],
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  let saved;
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (options.method === 'PUT') {
        saved = JSON.parse(options.body);
        return { ok: true, json: async () => ({ revision: 8, updatedAt: now.toISOString(), snapshot: saved.snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 7, updatedAt: now.toISOString(), snapshot }) };
    },
  });

  assert.equal(harness.nodes.get('session-review').hidden, false);
  assert.equal(harness.nodes.get('session-review-duration').hidden, false);
  harness.nodes.get('session-review-task').value = 'Read chapter four';
  harness.nodes.get('session-review-status').value = 'PARTLY_DONE';
  await harness.nodes.get('session-review').listeners.get('submit')({ preventDefault() {} });
  assert.equal(saved, undefined);
  assert.match(harness.nodes.get('session-review-status-message').textContent, /minutes studied/i);
  harness.nodes.get('session-review-duration').value = '27';
  await harness.nodes.get('session-review').listeners.get('submit')({ preventDefault() {} });

  assert.equal(saved.snapshot.pendingStudyReviewSessionId, null);
  assert.equal(saved.snapshot.studyHistory[0].id, 'legacy-phone-session');
  assert.equal(saved.snapshot.studyHistory[0].state, 'REVIEWED');
  assert.equal(saved.snapshot.studyHistory[0].actualMillis, 1_620_000);
  assert.equal(saved.snapshot.studyHistory[0].completionStatus, 'PARTLY_DONE');
  assert.equal(harness.nodes.get('start-session').disabled, false);
});

test('Chrome keeps a recovery review draft when the sync API rejects the save', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: [], blocks: [], activeStudySession: null,
    pendingStudyReviewSessionId: 'legacy-phone-session', studyHistory: [],
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options = {}) => options.method === 'PUT'
      ? { ok: false, status: 503, json: async () => ({}) }
      : { ok: true, json: async () => ({ revision: 7, updatedAt: now.toISOString(), snapshot }) },
  });
  harness.nodes.get('session-review-task').value = 'Read chapter four';
  harness.nodes.get('session-review-duration').value = '27';
  harness.nodes.get('session-review-status').value = 'PARTLY_DONE';
  harness.nodes.get('session-review-note').value = 'Finished the key section';

  await harness.nodes.get('session-review').listeners.get('submit')({ preventDefault() {} });

  assert.equal(harness.nodes.get('session-review-task').value, 'Read chapter four');
  assert.equal(harness.nodes.get('session-review-duration').value, '27');
  assert.equal(harness.nodes.get('session-review-status').value, 'PARTLY_DONE');
  assert.equal(harness.nodes.get('session-review-note').value, 'Finished the key section');
  assert.match(harness.nodes.get('session-review-status-message').textContent, /HTTP 503/i);
});

test('starting a duration in Chrome writes one Essentials-only shared session and blocks sites immediately', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
    activeStudySession: null,
    phoneReceivedStudySessionId: null,
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  const writes = [];

  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (options.method === 'PUT') {
        const body = JSON.parse(options.body);
        writes.push(body);
        return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: body.snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 1, updatedAt: now.toISOString(), snapshot }) };
    },
  });

  await harness.nodes.get('duration-45').listeners.get('click')();
  assert.match(harness.nodes.get('focus-clock-minutes').className, /is-flipping/);
  assert.doesNotMatch(harness.nodes.get('focus-clock-seconds').className, /is-flipping/);
  assert.equal(harness.nodes.get('duration-45').attributes.get('aria-pressed'), 'true');
  harness.nodes.get('focus-clock-minutes-flap-bottom').listeners.get('animationend')({ animationName: 'flip-unfold' });
  assert.equal(harness.nodes.get('focus-clock-minutes-static-top-value').textContent, '45');
  await harness.nodes.get('duration-custom').listeners.get('click')();
  harness.documentRef.getElementById('session-duration').value = '75';
  await harness.nodes.get('session-duration').listeners.get('input')();
  assert.equal(harness.nodes.get('focus-clock-minutes-static-top-value').textContent, '75');
  assert.equal(harness.nodes.get('focus-clock-minutes-static-bottom-value').textContent, '45');
  assert.equal(harness.nodes.get('focus-clock-minutes-flap-top-value').textContent, '45');
  assert.equal(harness.nodes.get('focus-clock-minutes-flap-bottom-value').textContent, '75');
  assert.equal(harness.nodes.get('duration-custom').attributes.get('aria-pressed'), 'true');
  harness.nodes.get('focus-clock-minutes-flap-bottom').listeners.get('animationend')({ animationName: 'flip-unfold' });
  await harness.nodes.get('start-session').listeners.get('click')();

  assert.equal(writes.length, 1);
  assert.equal(writes[0].snapshot.activeStudySession.taskName, 'Study session');
  assert.equal(writes[0].snapshot.activeStudySession.accessProfile, 'ESSENTIALS_ONLY');
  assert.equal(writes[0].snapshot.activeStudySession.startedAt, now.toISOString());
  assert.equal(writes[0].snapshot.activeStudySession.endsAt, '2026-11-05T11:45:00.000Z');
  assert.equal(harness.nodes.get('current-block').textContent, 'Study session');
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
  assert.match(harness.nodes.get('phone-receipt-state').textContent, /waiting for phone/i);
});

test('reduced motion updates split-flap clock layers without starting an animation', async () => {
  const hadMatchMedia = Object.hasOwn(globalThis, 'matchMedia');
  const originalMatchMedia = globalThis.matchMedia;
  globalThis.matchMedia = () => ({ matches: true });
  try {
    const harness = createHarness();
    await initializeManagement({ ...harness, fetchImpl: async () => { throw new Error('unexpected request'); } });

    await harness.nodes.get('duration-45').listeners.get('click')();

    const minuteGroup = harness.nodes.get('focus-clock-minutes');
    assert.doesNotMatch(minuteGroup.className, /is-flipping/);
    for (const layer of ['static-top', 'static-bottom', 'flap-top', 'flap-bottom']) {
      assert.equal(harness.nodes.get(`focus-clock-minutes-${layer}-value`).textContent, '45');
    }
  } finally {
    if (hadMatchMedia) globalThis.matchMedia = originalMatchMedia;
    else delete globalThis.matchMedia;
  }
});

test('a Chrome study session continues from the cached policy while offline and stays pending for the phone', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: null,
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: snapshot, lastSnapshotRevision: 4 });
  await initializeManagement({ ...harness, now: () => now, fetchImpl: async () => { throw new TypeError('offline'); } });

  harness.documentRef.getElementById('session-duration').value = '30';
  await harness.nodes.get('start-session').listeners.get('click')();

  assert.equal(harness.nodes.get('current-block').textContent, 'Study session');
  assert.equal(harness.storage.lastValidSnapshot.activeStudySession.startedAt, now.toISOString());
  assert.equal(harness.storage.pendingSessionStart.id, harness.storage.lastValidSnapshot.activeStudySession.id);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
  assert.match(harness.nodes.get('phone-receipt-state').textContent, /waiting for phone/i);
});

test('reconnecting publishes a still-active cached Chrome start to the phone', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: null };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: snapshot, lastSnapshotRevision: 4 });
  let online = false;
  const writes = [];
  const management = await initializeManagement({
    ...harness, now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (!online) throw new TypeError('offline');
      if (options.method === 'PUT') {
        const body = JSON.parse(options.body);
        writes.push(body.snapshot);
        return { ok: true, json: async () => ({ revision: 6, updatedAt: now.toISOString(), snapshot: body.snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 5, updatedAt: now.toISOString(), snapshot }) };
    },
  });
  harness.documentRef.getElementById('session-duration').value = '30';
  await harness.nodes.get('start-session').listeners.get('click')();
  online = true;
  await management.sync();

  assert.equal(writes.length, 1);
  assert.equal(writes[0].activeStudySession.id, harness.storage.lastValidSnapshot.activeStudySession.id);
  assert.equal(harness.storage.pendingSessionStart, null);
  assert.match(harness.nodes.get('phone-receipt-state').textContent, /waiting for phone/i);
});

test('a user can choose the synced value for a conflicted plan field', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1, settings: { requiredBlockCount: 4, eveningCutoff: '21:00' }, desktopBlockedDomains: ['reddit.com'], blocks: [],
    fieldConflicts: [{ path: 'settings.requiredBlockCount', remoteValue: '3', localValue: '4' }],
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  const writes = [];
  await initializeManagement({
    ...harness, now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (options.method === 'PUT') {
        const body = JSON.parse(options.body);
        writes.push(body.snapshot);
        return { ok: true, json: async () => ({ revision: 3, updatedAt: now.toISOString(), snapshot: body.snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot }) };
    },
  });
  harness.nodes.get('field-conflict-choice').value = 'remote';
  await harness.nodes.get('resolve-field-conflict').listeners.get('click')();

  assert.equal(writes[0].settings.requiredBlockCount, 3);
  assert.deepEqual(writes[0].fieldConflicts, []);
});

test('Chrome does not start a second session while the phone has a pending review', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
    activeStudySession: null,
    phoneReceivedStudySessionId: null,
    pendingStudyReviewSessionId: 'phone-session-review',
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  let writes = 0;

  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (options.method === 'PUT') writes += 1;
      return { ok: true, json: async () => ({ revision: 3, updatedAt: now.toISOString(), snapshot }) };
    },
  });

  assert.equal(harness.nodes.get('start-session').disabled, true);
  assert.equal(harness.nodes.get('open-connection').hidden, true);
  assert.equal(harness.nodes.get('session-review').hidden, false);
  assert.equal(harness.nodes.get('session-review-duration').hidden, false);
  assert.equal(harness.nodes.get('session-connection-label').textContent, 'Review needed');
  assert.match(harness.nodes.get('session-review-summary').textContent, /best estimate/i);
  await harness.nodes.get('start-session').listeners.get('click')();
  assert.equal(writes, 0);
});

test('stopping a shared timer in Chrome releases it and keeps the shared review gate', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const active = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
    activeStudySession: {
      id: 'phone-session-stop', taskName: 'Read chapter four',
      startedAt: '2026-11-05T10:15:00.000Z', endsAt: '2026-11-05T11:15:00.000Z', accessProfile: 'ESSENTIALS_ONLY',
    },
    pendingStudyReviewSessionId: null,
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: active, lastSnapshotRevision: 1 });
  const requests = [];
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async (_url, options) => {
      requests.push(options);
      if (options.method === 'GET') return { ok: true, json: async () => ({ revision: 1, updatedAt: null, snapshot: active }) };
      const body = JSON.parse(options.body);
      return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: body.snapshot }) };
    },
  });

  await harness.nodes.get('stop-session').listeners.get('click')();

  const update = requests.find(request => request.method === 'PUT');
  assert.ok(update);
  const stopped = JSON.parse(update.body).snapshot;
  assert.equal(stopped.activeStudySession, null);
  assert.equal(stopped.pendingStudyReviewSessionId, 'phone-session-stop');
  assert.equal(stopped.studyHistory[0].state, 'PENDING_REVIEW');
  assert.equal(harness.storage.lastValidSnapshot.pendingStudyReviewSessionId, 'phone-session-stop');
  assert.match(harness.nodes.get('session-start-status').textContent, /pending (?:session review|review details)/i);
});

test('Chrome queues an offline stop and publishes it after reconnecting', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const active = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: ['reddit.com'], blocks: [],
    activeStudySession: { id: 'phone-session-offline-stop', taskName: 'Read chapter', startedAt: '2026-11-05T10:00:00.000Z', endsAt: '2026-11-05T11:00:00.000Z', accessProfile: 'ESSENTIALS_ONLY' },
    pendingStudyReviewSessionId: null,
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: active, lastSnapshotRevision: 1 });
  let online = false;
  const writes = [];
  const management = await initializeManagement({
    ...harness, now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (!online) throw new TypeError('offline');
      if (options.method === 'PUT') {
        const body = JSON.parse(options.body);
        writes.push(body.snapshot);
        return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: body.snapshot }) };
      }
      return { ok: true, json: async () => ({ revision: 1, updatedAt: null, snapshot: active }) };
    },
  });

  assert.equal(harness.nodes.get('stop-session').disabled, false);
  await harness.nodes.get('stop-session').listeners.get('click')();
  assert.equal(harness.storage.lastValidSnapshot.activeStudySession, null);
  assert.equal(harness.storage.pendingSessionStop.id, 'phone-session-offline-stop');
  assert.match(harness.nodes.get('session-start-status').textContent, /stop pending/i);

  online = true;
  await management.sync();
  assert.equal(writes.length, 1);
  assert.equal(writes[0].activeStudySession, null);
  assert.equal(writes[0].pendingStudyReviewSessionId, 'phone-session-offline-stop');
  assert.equal(harness.storage.pendingSessionStop, null);
});

test('Chrome clears a queued stop after Android has reviewed the same session', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const reviewed = {
    id: 'phone-session-reviewed-offline-stop', taskName: 'Read chapter',
    startedAt: '2026-11-05T10:00:00.000Z', endedAt: '2026-11-05T10:20:00.000Z',
    durationMillis: 3_600_000, actualMillis: 1_200_000, accessProfile: 'ESSENTIALS_ONLY',
    state: 'REVIEWED', completionStatus: 'FINISHED', note: '', remaining: '',
  };
  const pending = {
    id: reviewed.id, taskName: reviewed.taskName,
    startedAt: '2026-11-05T10:00:00.000Z', endsAt: '2026-11-05T11:00:00.000Z',
    accessProfile: 'ESSENTIALS_ONLY',
  };
  const remote = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: null,
    pendingStudyReviewSessionId: null, studyHistory: [reviewed],
  };
  const harness = createHarness({
    apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: remote,
    lastSnapshotRevision: 1, pendingSessionStop: pending,
  });
  const requests = [];
  const management = await initializeManagement({
    ...harness, now: () => now,
    fetchImpl: async (_url, options = {}) => {
      requests.push(options);
      return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: remote }) };
    },
  });

  await management.sync();

  assert.equal(harness.storage.pendingSessionStop, null);
  assert.equal(harness.storage.lastValidSnapshot.pendingStudyReviewSessionId, null);
  assert.deepEqual(harness.storage.lastValidSnapshot.studyHistory, [reviewed]);
  assert.equal(requests.some(request => request.method === 'PUT'), false);
  assert.doesNotMatch(harness.nodes.get('session-start-status').textContent, /pending phone review/i);
  assert.match(harness.nodes.get('sync-state').textContent, /synced/i);
});

test('Chrome clears a queued stop after Android clears reviewed history', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const pending = {
    id: 'phone-session-cleared-review', taskName: 'Read chapter',
    startedAt: '2026-11-05T10:00:00.000Z', endsAt: '2026-11-05T11:00:00.000Z',
    accessProfile: 'ESSENTIALS_ONLY',
  };
  const remote = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: null,
    pendingStudyReviewSessionId: null, studyHistory: [],
  };
  const harness = createHarness({
    apiUrl: 'https://sync.example', apiToken: 'token',
    lastValidSnapshot: { ...remote, pendingStudyReviewSessionId: pending.id },
    lastSnapshotRevision: 1, pendingSessionStop: pending,
  });
  const requests = [];
  const management = await initializeManagement({
    ...harness, now: () => now,
    fetchImpl: async (_url, options = {}) => {
      requests.push(options);
      return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: remote }) };
    },
  });

  await management.sync();

  assert.equal(harness.storage.pendingSessionStop, null);
  assert.equal(harness.storage.lastValidSnapshot.pendingStudyReviewSessionId, null);
  assert.deepEqual(harness.storage.lastValidSnapshot.studyHistory, []);
  assert.equal(requests.some(request => request.method === 'PUT'), false);
  assert.doesNotMatch(harness.nodes.get('session-start-status').textContent, /pending phone review/i);
  assert.match(harness.nodes.get('sync-state').textContent, /synced/i);
});

test('an expired offline Chrome start remains locked for session review', async () => {
  const now = new Date('2026-11-05T11:30:00.000Z');
  const pending = { id: 'chrome-expired-offline', taskName: 'Study session', startedAt: '2026-11-05T10:00:00.000Z', endsAt: '2026-11-05T11:00:00.000Z', accessProfile: 'ESSENTIALS_ONLY' };
  const empty = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: null, studyHistory: [] };
  const remote = { ...empty, activeStudySession: pending, phoneReceivedStudySessionId: pending.id };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: { ...empty, activeStudySession: pending }, lastSnapshotRevision: 1, pendingSessionStart: pending });
  let published;
  await initializeManagement({
    ...harness, now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (options.method === 'PUT') {
        published = JSON.parse(options.body).snapshot;
        return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: published }) };
      }
      return { ok: true, json: async () => ({ revision: 1, updatedAt: null, snapshot: remote }) };
    },
  });

  assert.equal(published.activeStudySession, null);
  assert.equal(published.pendingStudyReviewSessionId, pending.id);
  assert.equal(published.studyHistory[0].id, pending.id);
  assert.equal(published.studyHistory[0].endedAt, pending.endsAt);
  assert.equal(published.studyHistory[0].state, 'PENDING_REVIEW');
  assert.equal(harness.storage.pendingSessionStart, null);
  assert.equal(harness.nodes.get('start-session').disabled, true);
  assert.match(harness.nodes.get('session-start-status').textContent, /save the pending session review here/i);
});

test('Chrome lets the user choose which imported history version to keep', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const server = { id: 'same-id', taskName: 'Server version', startedAt: '2026-11-05T10:00:00.000Z', endedAt: '2026-11-05T11:00:00.000Z', durationMillis: 3600000, actualMillis: 3600000, accessProfile: 'ESSENTIALS_ONLY', state: 'REVIEWED', completionStatus: 'FINISHED' };
  const phone = { ...server, id: 'same-id-phone-copy', taskName: 'Phone copy · Phone version' };
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' }, desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: null, studyHistory: [server, phone], fieldConflicts: [{ path: 'studyHistory.same-id', remoteValue: 'same-id', localValue: 'same-id-phone-copy' }] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  let published;
  await initializeManagement({
    ...harness, now: () => now,
    fetchImpl: async (_url, options = {}) => {
      if (options.method === 'PUT') {
        published = JSON.parse(options.body).snapshot;
        return { ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: published }) };
      }
      return { ok: true, json: async () => ({ revision: 1, updatedAt: null, snapshot }) };
    },
  });
  harness.nodes.get('field-conflict-choice').value = 'local';
  await harness.nodes.get('resolve-field-conflict').listeners.get('click')();

  assert.deepEqual(published.studyHistory.map(session => session.id), ['same-id-phone-copy']);
  assert.deepEqual(published.fieldConflicts, []);
});

test('connection status distinguishes browser enforcement from confirmed phone receipt', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const session = {
    id: 'chrome-session-1', taskName: 'Study session', accessProfile: 'ESSENTIALS_ONLY',
    startedAt: '2026-11-05T10:00:00.000Z', endsAt: '2026-11-05T11:00:00.000Z',
  };
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: session,
    phoneReceivedStudySessionId: null,
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  await initializeManagement({
    ...harness,
    now: () => now,
    fetchImpl: async () => ({ ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot }) }),
  });
  assert.match(harness.nodes.get('phone-receipt-state').textContent, /waiting for phone/i);

  const confirmedHarness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token' });
  await initializeManagement({
    ...confirmedHarness,
    now: () => now,
    fetchImpl: async () => ({
      ok: true,
      json: async () => ({ revision: 3, updatedAt: now.toISOString(), snapshot: { ...snapshot, phoneReceivedStudySessionId: session.id } }),
    }),
  });
  assert.match(confirmedHarness.nodes.get('phone-receipt-state').textContent, /phone received/i);
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
    activeStudySession: { id: 'session', taskName: 'Study', startedAt: new Date(2026, 10, 5, 10, 0).toISOString(), endsAt: new Date(2026, 10, 5, 11, 0).toISOString(), accessProfile: 'ESSENTIALS_ONLY' },
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

test('management applies active DNR blocks without site permissions', async () => {
  const now = new Date(2026, 10, 5, 10, 30);
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'focus', date: '2026-11-05', name: 'Study', startTime: '10:00', endTime: '11:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
    activeStudySession: { id: 'session', taskName: 'Study', startedAt: new Date(2026, 10, 5, 10, 0).toISOString(), endsAt: new Date(2026, 10, 5, 11, 0).toISOString(), accessProfile: 'ESSENTIALS_ONLY' },
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });
  harness.chromeApi.permissions = {
    async contains() { return false; },
    async request() { throw new Error('site access should not be requested for DNR block rules'); },
  };

  await initializeManagement({ ...harness, now: () => now, fetchImpl: async () => { throw new Error('unexpected request'); } });

  assert.equal(harness.nodes.has('grant-site-access'), false);
  assert.equal(harness.rules.length, 1);
  assert.deepEqual(harness.rules[0].action, { type: 'block' });
  assert.deepEqual(harness.rules[0].condition.requestDomains, ['reddit.com']);
});

test('the blocked page explains an Android-started session and its planned end without a bypass', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
    activeStudySession: {
      id: 'phone-session-1', taskName: 'Read chapter four',
      startedAt: '2026-11-05T10:15:00.000Z', endsAt: '2026-11-05T11:15:00.000Z',
      accessProfile: 'ESSENTIALS_ONLY',
    },
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });

  await initializeBlockedPage({ ...harness, now: () => now });

  assert.equal(harness.nodes.get('blocked-activity').textContent, 'Read chapter four');
  assert.equal(harness.nodes.get('blocked-end').textContent, new Date(snapshot.activeStudySession.endsAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }));
  assert.equal(harness.nodes.has('bypass'), false);
});

test('the blocked page hides the return-time row when no block is active', async () => {
  const harness = createHarness();

  await initializeBlockedPage({ ...harness, now: () => new Date(2026, 10, 5, 10, 30) });

  assert.equal(harness.nodes.get('blocked-activity').textContent, 'No active focus session');
  assert.equal(harness.nodes.get('blocked-end').textContent, '');
  assert.equal(harness.nodes.get('blocked-end-row').hidden, true);
});

test('the blocked page identifies a manually started study session', async () => {
  const session = { id: 'study-1', state: 'ACTIVE', startedAt: '2026-11-16T12:00:00.000Z', durationMillis: 1_800_000 };
  const harness = createHarness({ lastStudySession: session });
  await initializeBlockedPage({ ...harness, now: () => new Date('2026-11-16T12:05:00.000Z') });
  assert.equal(harness.nodes.get('blocked-activity').textContent, 'Study timer');
  assert.equal(harness.nodes.get('blocked-end-row').hidden, false);
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
  assert.deepEqual(harness.rules, []);
});

test('a synced Android timer installs site rules in the background until its absolute end', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com', 'youtube.com'],
    blocks: [],
    activeStudySession: {
      id: 'phone-session-1', taskName: 'Read chapter four',
      startedAt: '2026-11-05T10:15:00.000Z', endsAt: '2026-11-05T11:15:00.000Z',
      accessProfile: 'ESSENTIALS_ONLY',
    },
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });

  await refreshFocusState(harness.chromeApi, now);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com', 'youtube.com']);
  assert.equal(harness.alarms.get('focus-state-refresh').when, Date.parse(snapshot.activeStudySession.endsAt));

  await refreshFocusState(harness.chromeApi, new Date(snapshot.activeStudySession.endsAt));
  assert.deepEqual(harness.rules, []);
});

test('the background worker lifts timer rules when Android syncs an early stop', async () => {
  const now = new Date('2026-11-05T10:30:00.000Z');
  const active = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [],
    activeStudySession: {
      id: 'phone-session-1', taskName: 'Read chapter four',
      startedAt: '2026-11-05T10:15:00.000Z', endsAt: '2026-11-05T11:15:00.000Z',
      accessProfile: 'ESSENTIALS_ONLY',
    },
  };
  const stopped = { ...active, activeStudySession: null };
  const harness = createHarness({
    apiUrl: 'https://sync.example', apiToken: 'token', lastValidSnapshot: active, lastSnapshotRevision: 1,
  });
  await refreshFocusState(harness.chromeApi, now);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);

  await refreshRemoteSnapshot(harness.chromeApi, async () => ({
    ok: true, json: async () => ({ revision: 2, updatedAt: now.toISOString(), snapshot: stopped }),
  }), now);

  assert.deepEqual(harness.rules, []);
});

test('the background worker publishes automatic expiry and preserves the session review gate', async () => {
  const now = new Date('2026-11-05T11:15:00.000Z');
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'],
    blocks: [{ id: 'planned', date: '2026-11-05', name: 'Study', startTime: '06:00', endTime: '07:00', accessProfile: 'ESSENTIALS_ONLY', completionStatus: 'NOT_STARTED' }],
    activeStudySession: {
      id: 'phone-session-expiring', taskName: 'Read chapter four',
      startedAt: '2026-11-05T10:15:00.000Z', endsAt: now.toISOString(), accessProfile: 'ESSENTIALS_ONLY',
    },
    pendingStudyReviewSessionId: null,
  };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', lastSnapshotRevision: 4 });
  const requests = [];
  await refreshRemoteSnapshot(harness.chromeApi, async (_url, options) => {
    requests.push(options);
    if (options.method === 'GET') return { ok: true, json: async () => ({ revision: 4, updatedAt: null, snapshot }) };
    const body = JSON.parse(options.body);
    return { ok: true, json: async () => ({ revision: 5, updatedAt: now.toISOString(), snapshot: body.snapshot }) };
  }, now);

  assert.equal(requests.length, 2);
  assert.equal(requests[1].method, 'PUT');
  const published = JSON.parse(requests[1].body).snapshot;
  assert.equal(published.activeStudySession, null);
  assert.equal(published.pendingStudyReviewSessionId, 'phone-session-expiring');
  assert.deepEqual(published.blocks, snapshot.blocks);
  assert.deepEqual(harness.rules, []);
  assert.equal(harness.storage.lastValidSnapshot.activeStudySession, null);
  assert.equal(harness.storage.lastSnapshotRevision, 5);
});

test('background sync publishes an offline Chrome timer without changing its start time', async () => {
  const startedAt = '2026-11-16T12:00:00.000Z';
  const session = { id: 'offline-1', startedAt, endsAt: '2026-11-16T12:30:00.000Z',
    accessProfile: 'ESSENTIALS_ONLY', taskName: 'Study session' };
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [] };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token',
    pendingSessionStart: session, lastValidSnapshot: { ...snapshot, activeStudySession: session } });
  let published;
  const fetchImpl = async (_url, options = {}) => {
    if (options.method === 'PUT') {
      published = JSON.parse(options.body).snapshot;
      return { ok: true, json: async () => ({ revision: 2, updatedAt: null, snapshot: published }) };
    }
    return { ok: true, json: async () => ({ revision: 1, updatedAt: null, snapshot }) };
  };
  await refreshRemoteSnapshot(harness.chromeApi, fetchImpl, new Date('2026-11-16T12:05:00.000Z'));
  assert.equal(published.activeStudySession.startedAt, startedAt);
  assert.equal(harness.storage.pendingSessionStart, null);
  assert.deepEqual(harness.rules.map(rule => rule.condition.requestDomains[0]), ['reddit.com']);
});

test('background sync preserves both conflicting offline timers', async () => {
  const local = { id: 'chrome-session', startedAt: '2026-11-16T12:00:00.000Z',
    endsAt: '2026-11-16T12:30:00.000Z', accessProfile: 'ESSENTIALS_ONLY', taskName: 'Study session' };
  const remote = { ...local, id: 'android-session' };
  const snapshot = { version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [], activeStudySession: remote };
  const harness = createHarness({ apiUrl: 'https://sync.example', apiToken: 'token', pendingSessionStart: local });
  let published;
  const fetchImpl = async (_url, options = {}) => {
    if (options.method === 'PUT') {
      published = JSON.parse(options.body).snapshot;
      return { ok: true, json: async () => ({ revision: 2, updatedAt: null, snapshot: published }) };
    }
    return { ok: true, json: async () => ({ revision: 1, updatedAt: null, snapshot }) };
  };
  await refreshRemoteSnapshot(harness.chromeApi, fetchImpl, new Date('2026-11-16T12:05:00.000Z'));
  assert.equal(published.activeStudySession.id, 'android-session');
  assert.deepEqual(published.sessionStartCandidates.map(session => session.id), ['android-session', 'chrome-session']);
  assert.equal(harness.storage.pendingSessionStart, null);
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

test('the worker reminds from cached data offline without installing browsing blocks', async () => {
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
  assert.match(harness.notifications.shown[0].options.message, /Start a focus session/);
  assert.equal(harness.alarms.get('study-reminder-repeat').periodInMinutes, 30);
  assert.deepEqual(harness.rules, []);
});

test('the Chrome reminder invites a study session without requiring planned blocks', async () => {
  const snapshot = {
    version: 1, settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['reddit.com'], blocks: [],
  };
  const harness = createHarness({ lastValidSnapshot: snapshot });
  await refreshFocusState(harness.chromeApi, new Date(2026, 10, 18, 21, 0));
  assert.equal(harness.notifications.shown.length, 1);
  assert.match(harness.notifications.shown[0].options.title, /study session/i);
  assert.deepEqual(harness.rules, []);
});

