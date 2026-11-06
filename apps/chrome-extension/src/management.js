import { applySnapshotRules } from './enforcement.js';
import { getCurrentAndNext } from './policy.js';

const STORAGE_KEYS = ['apiUrl', 'apiToken', 'lastValidSnapshot', 'lastSuccessfulSync', 'lastSnapshotRevision'];
const DOMAIN_PATTERN = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i;
const DEFAULT_DESKTOP_DOMAINS = ['facebook.com', 'instagram.com', 'tiktok.com', 'x.com', 'reddit.com'];
const COMMON_DOMAINS = [...DEFAULT_DESKTOP_DOMAINS, 'youtube.com', 'web.whatsapp.com'];
const COMMON_LABELS = new Map([
  ['facebook.com', 'Facebook'], ['instagram.com', 'Instagram'], ['tiktok.com', 'TikTok'],
  ['x.com', 'X'], ['reddit.com', 'Reddit'], ['youtube.com', 'YouTube'], ['web.whatsapp.com', 'WhatsApp Web'],
]);
const ACCESS_PROFILES = ['ESSENTIALS_ONLY', 'ESSENTIALS_WITH_CHATGPT', 'ESSENTIALS_WITH_CHATGPT_AND_SEARCH'];
const COMPLETION_STATUSES = ['NOT_STARTED', 'PARTLY_DONE', 'NOT_DONE', 'FINISHED'];

function endpointFor(apiUrl) {
  let url;
  try {
    url = new URL(apiUrl);
  } catch {
    throw new Error('Enter a valid sync API URL.');
  }
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Use an HTTP or HTTPS sync API URL.');
  const localHttp = url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !localHttp) throw new Error('Use HTTPS for the sync API. HTTP is allowed for localhost development.');
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('Remove URL credentials, query parameters, and fragments; enter the bearer token separately.');
  }
  const base = url.href.replace(/\/+$/, '');
  return base.endsWith('/v1/snapshot') ? base : `${base}/v1/snapshot`;
}

function validDateKey(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? '')) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

function validTime(value) {
  const match = /^(\d{2}):(\d{2})$/.exec(value ?? '');
  return Boolean(match && Number(match[1]) < 24 && Number(match[2]) < 60);
}

function validSnapshot(snapshot) {
  return snapshot?.version === 1
    && Number.isInteger(snapshot.settings?.requiredBlockCount)
    && snapshot.settings.requiredBlockCount > 0
    && ['21:00', '22:00', '23:00'].includes(snapshot.settings?.eveningCutoff)
    && Array.isArray(snapshot.desktopBlockedDomains)
    && snapshot.desktopBlockedDomains.length <= 100
    && snapshot.desktopBlockedDomains.every(domain => typeof domain === 'string' && DOMAIN_PATTERN.test(domain))
    && Array.isArray(snapshot.blocks)
    && snapshot.blocks.length <= 1000
    && snapshot.blocks.every(block => typeof block.id === 'string'
      && block.id.length > 0
      && validDateKey(block.date)
      && typeof block.name === 'string'
      && validTime(block.startTime)
      && validTime(block.endTime)
      && ACCESS_PROFILES.includes(block.accessProfile)
      && COMPLETION_STATUSES.includes(block.completionStatus));
}

function validResponse(payload) {
  return Number.isInteger(payload?.revision)
    && payload.revision >= 0
    && (payload.updatedAt === null || typeof payload.updatedAt === 'string')
    && validSnapshot(payload.snapshot);
}

function formatLocalTime(value) {
  if (!value) return 'Never';
  return new Date(value).toLocaleString();
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
  const active = Boolean(snapshot && getCurrentAndNext(snapshot, now).current);
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
    protection.textContent = 'Protection inactive — no valid plan has synced.';
    sync.textContent = connected ? 'Connected — waiting for first sync.' : 'Not connected — enter your sync API details.';
    currentBlock.textContent = 'No active focus block';
    plannedEnd.textContent = 'Planned end: —';
    return;
  }

  protection.textContent = 'Protection is active when a focus block is scheduled.';
  sync.textContent = error || !connected ? 'Offline — using cached plan.' : 'Synced — using latest plan.';
  const { current } = getCurrentAndNext(snapshot, now);
  currentBlock.textContent = current?.name ?? 'No active focus block';
  plannedEnd.textContent = current ? `Planned end: ${current.endTime}` : 'Planned end: —';
}

function ensureCustomCheckbox(documentRef, state, domain) {
  if (state.customCheckboxes?.has(domain)) return state.customCheckboxes.get(domain);
  const wrapper = documentRef.createElement('label');
  const checkbox = documentRef.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.dataset.domain = domain;
  checkbox.id = `domain-${domain}`;
  checkbox.addEventListener('change', () => state.changeDomain(domain, checkbox.checked));
  wrapper.append(checkbox, documentRef.createTextNode(` ${domain}`));
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
}) {
  const saved = await chromeApi.storage.local.get(STORAGE_KEYS);
  const cachedSnapshot = validSnapshot(saved.lastValidSnapshot) ? saved.lastValidSnapshot : null;
  const connected = Boolean(saved.apiUrl && saved.apiToken);
  const state = {
    apiUrl: saved.apiUrl ?? '',
    apiToken: saved.apiToken ?? '',
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
  const readLatest = async endpoint => {
    const response = await fetchImpl(endpoint, {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}` },
      cache: 'no-store', credentials: 'omit', redirect: 'error',
    });
    if (!response.ok) throw new Error(`Sync failed with HTTP ${response.status}. Check the API and try again.`);
    const payload = await response.json();
    if (!validResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
    return payload;
  };
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
      const endpoint = endpointFor(state.apiUrl);
      let response = await fetchImpl(endpoint, {
        method: 'PUT',
        headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ expectedRevision: state.revision, snapshot }),
        cache: 'no-store', credentials: 'omit', redirect: 'error',
      });
      if (response.status === 409) {
        const latest = await readLatest(endpoint);
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
      if (!validResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
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
        ? 'Could not reach the sync API. Check your connection and API URL.'
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
    if (!DOMAIN_PATTERN.test(domain) || domain.includes('://') || domain.includes('/')) {
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
    show();
    try {
      const endpoint = endpointFor(state.apiUrl);
      const response = await fetchImpl(endpoint, {
        method: 'GET',
        headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}` },
        cache: 'no-store',
        credentials: 'omit',
        redirect: 'error',
      });
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) throw new Error('Sync rejected the token. Check the bearer token and try again.');
        if (response.status === 404) throw new Error('Sync API not found. Check the API base URL.');
        throw new Error(`Sync failed with HTTP ${response.status}. Check the API and try again.`);
      }
      const payload = await response.json();
      if (!validResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
      const syncedAt = now().toISOString();
      state.snapshot = payload.snapshot;
      state.revision = payload.revision;
      state.newEmptyBlocklist = payload.revision === 0 && payload.updatedAt === null && payload.snapshot.desktopBlockedDomains.length === 0;
      state.online = true;
      state.lastSuccessfulSync = syncedAt;
      await chromeApi.storage.local.set({ lastValidSnapshot: state.snapshot, lastSuccessfulSync: syncedAt, lastSnapshotRevision: payload.revision });
      await updateRules();
      await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
    } catch (cause) {
      state.online = false;
      state.error = cause instanceof TypeError
        ? 'Could not reach the sync API. Check your connection and API URL.'
        : (cause?.message || 'Sync failed. Check the API URL and bearer token.');
      await updateRules();
    }
    show();
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
  documentRef.getElementById('sync-now').addEventListener('click', sync);

  await updateRules();
  if (state.connected) await sync();
  return { sync };
}

if (globalThis.document && globalThis.chrome) {
  void initializeManagement();
}
