import { applySnapshotRules } from './enforcement.js';
import { getCurrentAndNext } from './policy.js';

const STORAGE_KEYS = ['apiUrl', 'apiToken', 'lastValidSnapshot', 'lastSuccessfulSync'];
const DOMAIN_PATTERN = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i;
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

  lastSync.textContent = `Last successful sync: ${formatLocalTime(lastSuccessfulSync)}`;
  errorMessage.hidden = !error;
  errorMessage.textContent = error ?? '';

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
  };

  documentRef.getElementById('api-url').value = state.apiUrl;
  documentRef.getElementById('api-token').value = state.apiToken;

  const show = () => render(documentRef, state, now());
  const updateRules = async () => applySnapshotRules(chromeApi, state.snapshot, now());
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
      state.lastSuccessfulSync = syncedAt;
      await chromeApi.storage.local.set({ lastValidSnapshot: state.snapshot, lastSuccessfulSync: syncedAt });
      await updateRules();
      await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
    } catch (cause) {
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
    await chromeApi.storage.local.set({ apiUrl: state.apiUrl, apiToken: state.apiToken });
    await sync();
  });
  documentRef.getElementById('sync-now').addEventListener('click', sync);

  await updateRules();
  show();
  if (state.connected) await sync();
  return { sync };
}

if (globalThis.document && globalThis.chrome) {
  void initializeManagement();
}
