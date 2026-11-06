import { applySnapshotRules } from './enforcement.js';
import { getCurrentAndNext } from './policy.js';
import { readSnapshot, snapshotEndpoint, validSnapshotResponse } from './sync-client.js';
import { isValidDomain } from './domain.js';

const STORAGE_KEYS = ['apiUrl', 'apiToken', 'lastValidSnapshot', 'lastSuccessfulSync', 'lastSnapshotRevision'];
const DEFAULT_SYNC_API_URL = 'https://br-floral-block-bddjl0rs-jpfreedomsync.compute.c-8.us-east-2.aws.neon.tech/';
const DEFAULT_DESKTOP_DOMAINS = ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com'];
const COMMON_DOMAINS = [...DEFAULT_DESKTOP_DOMAINS, 'youtube.com', 'web.whatsapp.com'];
const COMMON_LABELS = new Map([
  ['facebook.com', 'Facebook'], ['instagram.com', 'Instagram'], ['tiktok.com', 'TikTok'],
  ['x.com', 'X'], ['reddit.com', 'Reddit'], ['youtube.com', 'YouTube'], ['web.whatsapp.com', 'WhatsApp Web'],
]);

function formatLocalTime(value) {
  if (!value) return 'Never';
  return new Date(value).toLocaleString();
}

function connectionError(apiUrl) {
  try {
    const api = new URL(apiUrl);
    if (['localhost', '127.0.0.1', '[::1]'].includes(api.hostname)) {
      return `The local sync API at ${api.host} is not responding. Start the local test server, or enter the deployed Sync API URL from Android settings.`;
    }
  } catch {
    // The regular URL validation message is more useful for malformed values.
  }
  return 'Could not reach the sync API. Check your connection and API URL.';
}

function localDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatPlanTime(value) {
  const [hours, minutes] = value.split(':').map(Number);
  return new Date(2000, 0, 1, hours, minutes).toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  });
}

function renderDayPlan(documentRef, snapshot, date, prefix, currentBlockId) {
  const dateElement = documentRef.getElementById(`${prefix}-plan-date`);
  const emptyElement = documentRef.getElementById(`${prefix}-plan-empty`);
  const art = documentRef.getElementById(`${prefix}-plan-art`);
  const list = documentRef.getElementById(`${prefix}-plan-list`);
  dateElement.textContent = date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  list.replaceChildren();

  if (!snapshot) {
    art.hidden = false;
    emptyElement.hidden = false;
    emptyElement.textContent = 'Sync to load the plan from your phone.';
    return;
  }

  const dateKey = localDateKey(date);
  const blocks = snapshot.blocks
    .filter(block => block.date === dateKey)
    .sort((first, second) => first.startTime.localeCompare(second.startTime));
  emptyElement.hidden = blocks.length > 0;
  art.hidden = blocks.length > 0;
  emptyElement.textContent = `No blocks planned for ${prefix}.`;

  for (const block of blocks) {
    const row = documentRef.createElement('li');
    row.className = `day-plan-row${block.id === currentBlockId ? ' is-current' : ''}`;
    const time = documentRef.createElement('time');
    time.className = 'day-plan-time';
    time.textContent = `${formatPlanTime(block.startTime)}–${formatPlanTime(block.endTime)}`;
    const name = documentRef.createElement('span');
    name.className = 'day-plan-name';
    name.textContent = block.name;
    const status = documentRef.createElement('span');
    status.className = 'day-plan-status';
    status.textContent = block.id === currentBlockId
      ? 'In progress'
      : block.completionStatus === 'FINISHED' ? 'Finished'
        : block.completionStatus === 'PARTLY_DONE' ? 'Partly done'
          : block.completionStatus === 'NOT_DONE' ? 'Not done' : 'Planned';
    row.append(time, name, status);
    list.append(row);
  }
}

function render(documentRef, state, now) {
  const { snapshot, lastSuccessfulSync, error, connected } = state;
  const protection = documentRef.getElementById('protection-state');
  const sync = documentRef.getElementById('sync-state');
  const lastSync = documentRef.getElementById('last-sync');
  const errorMessage = documentRef.getElementById('sync-error');
  const currentBlock = documentRef.getElementById('current-block');
  const plannedEnd = documentRef.getElementById('planned-end');
  const permissionState = documentRef.getElementById('permission-state');
  const grantSiteAccess = documentRef.getElementById('grant-site-access');
  const current = snapshot ? getCurrentAndNext(snapshot, now).current : null;
  const active = Boolean(current);
  documentRef.querySelector?.('.focus-board')?.classList?.toggle('is-active', active);
  const currentBlockId = current?.id ?? null;
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  documentRef.getElementById('today-intro-date').textContent = now.toLocaleDateString(undefined, {
    weekday: 'long', month: 'long', day: 'numeric',
  });
  renderDayPlan(documentRef, snapshot, now, 'today', currentBlockId);
  renderDayPlan(documentRef, snapshot, tomorrow, 'tomorrow', currentBlockId);
  const displayedDomains = snapshot
    ? (state.newEmptyBlocklist ? [...DEFAULT_DESKTOP_DOMAINS] : [...new Set(snapshot.desktopBlockedDomains)])
    : [...DEFAULT_DESKTOP_DOMAINS];
  state.displayedDomains = displayedDomains;
  const canEdit = Boolean(state.connected && state.online && snapshot && !state.saving);
  for (const domain of COMMON_DOMAINS) {
    const checkbox = documentRef.getElementById(`domain-${domain}`);
    checkbox.checked = displayedDomains.includes(domain);
    checkbox.disabled = !canEdit || (active && checkbox.checked);
  }
  for (const domain of displayedDomains.filter(value => !COMMON_DOMAINS.includes(value))) {
    ensureCustomCheckbox(documentRef, state, domain);
  }
  for (const [domain, checkbox] of state.customCheckboxes ?? []) {
    if (!displayedDomains.includes(domain)) {
      checkbox.parentElement?.remove();
      state.customCheckboxes.delete(domain);
      continue;
    }
    checkbox.checked = displayedDomains.includes(domain);
    checkbox.disabled = !canEdit || (active && checkbox.checked);
  }
  const customDomain = documentRef.getElementById('custom-domain');
  const addButton = documentRef.getElementById('add-domain');
  customDomain.disabled = !canEdit;
  addButton.disabled = !canEdit;
  documentRef.getElementById('blocklist-status').textContent = state.blocklistMessage
    ?? (!state.connected || !state.online ? 'Connect Chrome to the internet and sync to save blocklist changes.' : '');

  lastSync.textContent = `Last successful sync: ${formatLocalTime(lastSuccessfulSync)}`;
  errorMessage.hidden = !error;
  errorMessage.textContent = error ?? '';
  const missingDomains = state.missingDomains ?? [];
  permissionState.textContent = missingDomains.length
    ? `Chrome site access is needed for ${missingDomains.join(', ')} before these sites can be blocked.`
    : 'Chrome site access is ready for selected sites.';
  grantSiteAccess.hidden = missingDomains.length === 0;

  if (!snapshot) {
    protection.textContent = 'Protection inactive. No valid plan has synced.';
    sync.textContent = !connected
      ? 'Not connected. Enter your sync API details.'
      : error ? 'Offline. No plan has synced yet.' : 'Connected. Waiting for first sync.';
    currentBlock.textContent = 'No active focus block';
    plannedEnd.hidden = true;
    plannedEnd.textContent = '';
    return;
  }

  protection.textContent = 'Protection is active when a focus block is scheduled.';
  sync.textContent = error || !connected ? 'Offline. Using cached plan.' : 'Synced. Using latest plan.';
  currentBlock.textContent = current?.name ?? 'No active focus block';
  plannedEnd.hidden = !current;
  plannedEnd.textContent = current ? `Planned end: ${current.endTime}` : '';
}

function ensureCustomCheckbox(documentRef, state, domain) {
  if (state.customCheckboxes?.has(domain)) return state.customCheckboxes.get(domain);
  const wrapper = documentRef.createElement('label');
  wrapper.className = 'site-row';
  const checkbox = documentRef.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.dataset.domain = domain;
  checkbox.id = `domain-${domain}`;
  checkbox.addEventListener('change', () => state.changeDomain(domain, checkbox.checked));
  const name = documentRef.createElement('span');
  name.className = 'site-name';
  name.textContent = domain;
  const action = documentRef.createElement('span');
  action.className = 'site-action';
  action.setAttribute?.('aria-hidden', 'true');
  const off = documentRef.createElement('span');
  off.className = 'when-off';
  off.textContent = 'Block';
  const on = documentRef.createElement('span');
  on.className = 'when-on';
  on.textContent = 'Blocking';
  action.append(off, on);
  wrapper.append(checkbox, name, action);
  documentRef.getElementById('custom-sites').append(wrapper);
  state.customCheckboxes ??= new Map();
  state.customCheckboxes.set(domain, checkbox);
  return checkbox;
}

export async function initializeManagement({
  chromeApi = globalThis.chrome,
  documentRef = globalThis.document,
  fetchImpl = globalThis.fetch,
  now = () => new Date(),
} = {}) {
  const saved = await chromeApi.storage.local.get(STORAGE_KEYS);
  const cachedSnapshot = validSnapshotResponse({ revision: saved.lastSnapshotRevision ?? 0, updatedAt: saved.lastSuccessfulSync ?? null, snapshot: saved.lastValidSnapshot })
    ? saved.lastValidSnapshot
    : null;
  const oldTestSetup = saved.apiUrl === 'http://127.0.0.1:18787' && saved.apiToken === 'test-token';
  const apiUrl = !saved.apiUrl || oldTestSetup ? DEFAULT_SYNC_API_URL : saved.apiUrl;
  const apiToken = oldTestSetup ? '' : saved.apiToken ?? '';
  const connected = Boolean(apiUrl && apiToken);
  const state = {
    apiUrl,
    apiToken,
    snapshot: cachedSnapshot,
    lastSuccessfulSync: saved.lastSuccessfulSync ?? null,
    error: cachedSnapshot && !connected
      ? 'Enter the sync API URL and bearer token to refresh this cached plan.'
      : null,
    connected,
    online: false,
    revision: Number.isInteger(saved.lastSnapshotRevision) ? saved.lastSnapshotRevision : null,
    newEmptyBlocklist: Boolean(cachedSnapshot && cachedSnapshot.desktopBlockedDomains.length === 0 && saved.lastSnapshotRevision === 0),
    saving: false,
    blocklistMessage: null,
  };
  const permissionState = documentRef.getElementById('permission-state');
  const grantSiteAccess = documentRef.getElementById('grant-site-access');
  const views = ['plan', 'sites', 'connection'];
  const showView = selected => {
    for (const view of views) {
      const active = view === selected;
      const tab = documentRef.getElementById(`tab-${view}`);
      documentRef.getElementById(`view-${view}`).hidden = !active;
      tab.classList?.toggle('is-selected', active);
      if (active) tab.setAttribute?.('aria-current', 'page');
      else tab.removeAttribute?.('aria-current');
    }
  };
  for (const view of views) {
    documentRef.getElementById(`tab-${view}`).addEventListener('click', () => showView(view));
  }
  documentRef.getElementById('api-url').value = state.apiUrl;
  documentRef.getElementById('api-token').value = state.apiToken;

  const show = () => render(documentRef, state, now());
  const refreshSiteAccess = async () => {
    state.missingDomains = [];
    if (state.snapshot && chromeApi.permissions?.contains) {
      for (const domain of new Set(state.snapshot.desktopBlockedDomains ?? [])) {
        const origins = [`http://${domain}/*`, `https://${domain}/*`];
        if (!(await chromeApi.permissions.contains({ origins }))) state.missingDomains.push(domain);
      }
    }
  };
  const updateRules = async () => {
    await refreshSiteAccess();
    await applySnapshotRules(chromeApi, state.snapshot, now());
    show();
  };
  const readLatest = () => readSnapshot(state.apiUrl, state.apiToken, fetchImpl);
  const persistDomainIntent = async (domain, included, initialDomains = state.displayedDomains) => {
    if (!state.connected || !state.online || !state.snapshot || state.saving) return;
    const current = getCurrentAndNext(state.snapshot, now()).current;
    if (current && !included && (state.snapshot.desktopBlockedDomains ?? []).includes(domain)) {
      state.blocklistMessage = 'Selected sites cannot be removed during an active focus block.';
      show();
      return;
    }
    state.saving = true;
    state.blocklistMessage = null;
    show();
    const setDomain = domains => {
      const next = new Set(domains);
      if (included) next.add(domain);
      else next.delete(domain);
      return [...next];
    };
    let snapshot = { ...state.snapshot, desktopBlockedDomains: setDomain(initialDomains) };
    try {
      const endpoint = snapshotEndpoint(state.apiUrl);
      let response = await fetchImpl(endpoint, {
        method: 'PUT',
        headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ expectedRevision: state.revision, snapshot }),
        cache: 'no-store', credentials: 'omit', redirect: 'error',
      });
      if (response.status === 409) {
        const latest = await readLatest();
        const latestCurrent = getCurrentAndNext(latest.snapshot, now()).current;
        if (latestCurrent && !included && latest.snapshot.desktopBlockedDomains.includes(domain)) {
          state.snapshot = latest.snapshot;
          state.revision = latest.revision;
          state.blocklistMessage = 'Selected sites cannot be removed during an active focus block.';
          state.newEmptyBlocklist = latest.revision === 0 && latest.updatedAt === null && latest.snapshot.desktopBlockedDomains.length === 0;
          await chromeApi.storage.local.set({ lastValidSnapshot: latest.snapshot, lastSnapshotRevision: latest.revision });
          await updateRules();
          return;
        }
        snapshot = { ...latest.snapshot, desktopBlockedDomains: setDomain(latest.snapshot.desktopBlockedDomains) };
        response = await fetchImpl(endpoint, {
          method: 'PUT',
          headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ expectedRevision: latest.revision, snapshot }),
          cache: 'no-store', credentials: 'omit', redirect: 'error',
        });
      }
      if (!response.ok) throw new Error(response.status === 409
        ? 'The blocklist changed again. Sync and retry your change.'
        : `Could not save the blocklist (HTTP ${response.status}).`);
      const payload = await response.json();
      if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
      state.snapshot = payload.snapshot;
      state.revision = payload.revision;
      state.newEmptyBlocklist = false;
      state.online = true;
      state.error = null;
      state.lastSuccessfulSync = now().toISOString();
      await chromeApi.storage.local.set({ lastValidSnapshot: payload.snapshot, lastSuccessfulSync: state.lastSuccessfulSync, lastSnapshotRevision: payload.revision });
      await updateRules();
      await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
    } catch (cause) {
      const offline = cause instanceof TypeError;
      state.online = !offline;
      state.error = offline
        ? connectionError(state.apiUrl)
        : (cause?.message || 'Could not save the blocklist.');
      state.blocklistMessage = offline
        ? 'Connect Chrome to the internet to save blocklist changes.'
        : state.error;
      await updateRules();
    } finally {
      state.saving = false;
      show();
    }
  };
  state.changeDomain = (domain, included) => {
    const displayed = state.displayedDomains ?? DEFAULT_DESKTOP_DOMAINS;
    const base = state.newEmptyBlocklist ? displayed : (state.snapshot?.desktopBlockedDomains ?? displayed);
    return persistDomainIntent(domain, included, base);
  };
  for (const domain of COMMON_DOMAINS) {
    documentRef.getElementById(`domain-${domain}`).addEventListener('change', event => {
      return state.changeDomain(domain, event.target.checked);
    });
  }
  documentRef.getElementById('add-domain-form').addEventListener('submit', async event => {
    event.preventDefault();
    const field = documentRef.getElementById('custom-domain');
    const domain = field.value.trim().toLowerCase();
    if (!isValidDomain(domain)) {
      state.blocklistMessage = 'Enter a bare domain such as example.com. URLs and paths are not accepted.';
      show();
      return;
    }
    if (state.displayedDomains.includes(domain)) {
      state.blocklistMessage = 'That domain is already in the blocklist.';
      show();
      return;
    }
    await persistDomainIntent(domain, true);
    if (state.online && state.snapshot.desktopBlockedDomains.includes(domain)) field.value = '';
  });
  documentRef.getElementById('grant-site-access').addEventListener('click', async () => {
    if (!chromeApi.permissions?.request) return;
    const origins = state.missingDomains.flatMap(domain => [`http://${domain}/*`, `https://${domain}/*`]);
    if (origins.length && await chromeApi.permissions.request({ origins })) {
      await refreshSiteAccess();
      await applySnapshotRules(chromeApi, state.snapshot, now());
      permissionState.textContent = 'Site access granted.';
      grantSiteAccess.hidden = true;
    }
  });
  const sync = async () => {
    state.error = null;
    state.connected = Boolean(state.apiUrl && state.apiToken);
    if (!state.connected) {
      state.error = 'Enter the sync API URL and bearer token, then save settings.';
      show();
      await updateRules();
      return;
    }
    const syncButton = documentRef.getElementById('sync-now');
    syncButton.classList?.add('is-syncing');
    syncButton.setAttribute?.('aria-busy', 'true');
    show();
    try {
      let payload = await readSnapshot(state.apiUrl, state.apiToken, fetchImpl);
      const isNewEmptySnapshot = payload.revision === 0
        && payload.updatedAt === null
        && payload.snapshot.desktopBlockedDomains.length === 0;
      if (isNewEmptySnapshot) {
        const initializedSnapshot = { ...payload.snapshot, desktopBlockedDomains: [...DEFAULT_DESKTOP_DOMAINS] };
        const response = await fetchImpl(snapshotEndpoint(state.apiUrl), {
          method: 'PUT',
          headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ expectedRevision: payload.revision, snapshot: initializedSnapshot }),
          cache: 'no-store', credentials: 'omit', redirect: 'error',
        });
        if (response.status === 409) payload = await readSnapshot(state.apiUrl, state.apiToken, fetchImpl);
        else {
          if (!response.ok) throw new Error(`Could not initialize the desktop blocklist (HTTP ${response.status}).`);
          payload = await response.json();
          if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
        }
      }
      const syncedAt = now().toISOString();
      state.snapshot = payload.snapshot;
      state.revision = payload.revision;
      state.newEmptyBlocklist = false;
      state.online = true;
      state.lastSuccessfulSync = syncedAt;
      await chromeApi.storage.local.set({ lastValidSnapshot: state.snapshot, lastSuccessfulSync: syncedAt, lastSnapshotRevision: payload.revision });
      await updateRules();
      await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
    } catch (cause) {
      state.online = false;
      state.error = cause instanceof TypeError
        ? connectionError(state.apiUrl)
        : (cause?.message || 'Sync failed. Check the API URL and bearer token.');
      await updateRules();
    } finally {
      syncButton.classList?.remove('is-syncing');
      syncButton.removeAttribute?.('aria-busy');
      show();
    }
  };

  documentRef.getElementById('settings-form').addEventListener('submit', async event => {
    event.preventDefault();
    state.apiUrl = documentRef.getElementById('api-url').value.trim();
    state.apiToken = documentRef.getElementById('api-token').value.trim();
    state.connected = Boolean(state.apiUrl && state.apiToken);
    if (state.connected && chromeApi.permissions?.request) {
      try {
        const api = new URL(state.apiUrl);
        const localHttp = api.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(api.hostname);
        if (!localHttp) await chromeApi.permissions.request({ origins: [`${api.origin}/*`] });
      } catch {
        // endpointFor reports malformed or insecure URLs in the normal sync flow.
      }
    }
    await chromeApi.storage.local.set({ apiUrl: state.apiUrl, apiToken: state.apiToken });
    await sync();
  });
  documentRef.getElementById('sync-now').addEventListener('click', async () => {
    await sync();
    if (!state.connected || state.error) showView('connection');
  });

  await updateRules();
  if (state.connected) await sync();
  return { sync };
}

if (globalThis.document && globalThis.chrome) {
  void initializeManagement();
}
