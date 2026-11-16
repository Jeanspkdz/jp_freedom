import { applySnapshotRules } from './enforcement.js';
import { getActiveStudySession, getCurrentAndNext } from './policy.js';
import { readSnapshot, snapshotEndpoint, validSnapshotResponse } from './sync-client.js';
import { isValidDomain } from './domain.js';

const STORAGE_KEYS = ['apiUrl', 'apiToken', 'lastValidSnapshot', 'lastSuccessfulSync', 'lastSnapshotRevision', 'pendingSessionStart', 'pendingSessionStop'];
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

function withFieldValue(snapshot, conflict, value) {
  const result = { ...snapshot };
  if (conflict.path.startsWith('settings.')) {
    const field = conflict.path.slice('settings.'.length);
    return { ...result, settings: { ...snapshot.settings, [field]: field === 'requiredBlockCount' ? Number(value) : value } };
  }
  if (conflict.path.startsWith('desktopBlockedDomains.')) {
    const domain = conflict.path.slice('desktopBlockedDomains.'.length);
    const domains = new Set(snapshot.desktopBlockedDomains);
    if (value === 'included') domains.add(domain);
    else domains.delete(domain);
    return { ...result, desktopBlockedDomains: [...domains] };
  }
  if (conflict.path.startsWith('blocks.')) {
    const [, id, field] = conflict.path.split('.', 3);
    return { ...result, blocks: snapshot.blocks.map(block => block.id !== id ? block : {
      ...block, [field]: value === '<null>' ? null : value,
    }) };
  }
  if (conflict.path.startsWith('studyHistory.')) {
    const keepId = String(value);
    const rejectedId = conflict.remoteValue === keepId ? conflict.localValue : conflict.remoteValue;
    return { ...result, studyHistory: (snapshot.studyHistory ?? []).filter(session => session.id !== rejectedId) };
  }
  return result;
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

function planTimeInSeconds(value) {
  const [hours, minutes] = value.split(':').map(Number);
  return (hours * 60 + minutes) * 60;
}

function renderFocusMeter(documentRef, current, now) {
  const remaining = documentRef.getElementById('focus-remaining');
  const remainingValue = documentRef.getElementById('focus-remaining-value');
  const progress = documentRef.getElementById('focus-progress');
  const fill = documentRef.getElementById('focus-progress-fill');
  if (!current) {
    remaining.hidden = true;
    progress.hidden = true;
    remainingValue.textContent = '';
    fill.style.width = '0%';
    progress.setAttribute('aria-valuenow', '0');
    return;
  }

  const start = current.studySession ? Date.parse(current.startedAt) / 1000 : planTimeInSeconds(current.startTime);
  const end = current.studySession ? Date.parse(current.endsAt) / 1000 : planTimeInSeconds(current.endTime);
  let currentTime = current.studySession
    ? now.getTime() / 1000
    : now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  let adjustedEnd = end;
  if (!current.studySession && end <= start) adjustedEnd += 24 * 3600;
  if (!current.studySession && currentTime < start && adjustedEnd > 24 * 3600) currentTime += 24 * 3600;
  const duration = adjustedEnd - start;
  const elapsed = Math.min(Math.max(currentTime - start, 0), duration);
  const secondsLeft = Math.max(adjustedEnd - currentTime, 0);
  const percent = duration > 0 ? elapsed / duration * 100 : 0;
  remainingValue.textContent = `${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, '0')}`;
  remaining.hidden = false;
  progress.hidden = false;
  fill.style.width = `${percent.toFixed(2)}%`;
  progress.setAttribute('aria-valuenow', String(Math.round(percent)));
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
  sync.setAttribute?.('data-state', connected && state.online && !error ? 'synced' : 'offline');
  const lastSync = documentRef.getElementById('last-sync');
  const errorMessage = documentRef.getElementById('sync-error');
  const currentBlock = documentRef.getElementById('current-block');
  const plannedEnd = documentRef.getElementById('planned-end');
  const session = snapshot ? getActiveStudySession(snapshot, now) : null;
  const candidates = snapshot?.sessionStartCandidates ?? [];
  const plannedBlock = snapshot ? getCurrentAndNext(snapshot, now).current : null;
  const current = session
    ? { id: session.id, name: session.taskName, studySession: true, startedAt: session.startedAt, endsAt: session.endsAt }
    : plannedBlock;
  const active = Boolean(current);
  const sharedSessionPending = Boolean(snapshot?.activeStudySession);
  const startControls = documentRef.getElementById('session-start-controls');
  if (startControls) startControls.hidden = active;
  const startButton = documentRef.getElementById('start-session');
  if (startButton) startButton.disabled = !snapshot || !state.connected || state.saving || sharedSessionPending || Boolean(snapshot.pendingStudyReviewSessionId) || candidates.length > 1;
  const stopButton = documentRef.getElementById('stop-session');
  if (stopButton) {
    stopButton.hidden = !sharedSessionPending;
    stopButton.disabled = !state.connected || state.saving;
  }
  const startStatus = documentRef.getElementById('session-start-status');
  if (startStatus) startStatus.textContent = state.sessionMessage
    ?? (!snapshot || !state.connected ? 'Connect to the sync API at least once before starting a study session.'
      : snapshot.pendingStudyReviewSessionId ? 'Finish the pending phone review before starting another session.'
        : snapshot.activeStudySession
          ? Date.parse(snapshot.activeStudySession.endsAt) > now.getTime()
            ? 'A study session is already active.'
            : 'Wait for the phone to sync this session and its review before starting another.'
          : '');
  const receipt = documentRef.getElementById('phone-receipt-state');
  if (receipt) {
    const sharedSession = snapshot?.activeStudySession;
    receipt.textContent = !sharedSession
      ? 'No shared study session is waiting for the phone.'
      : snapshot.phoneReceivedStudySessionId === sharedSession.id
        ? 'Phone received this study session.'
        : 'Chrome is blocking selected sites locally. Waiting for phone to receive this session.';
  }
  const conflict = documentRef.getElementById('session-conflict-state');
  if (conflict) {
    conflict.hidden = candidates.length < 2;
    conflict.textContent = candidates.length < 2
      ? ''
      : `Two offline study sessions need a choice: ${candidates.map(item => `${item.taskName} (${new Date(item.startedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })})`).join(' or ')}.`;
  }
  const choice = documentRef.getElementById('session-start-choice');
  if (choice && candidates.length) {
    choice.replaceChildren?.(...candidates.map(candidate => {
      const option = documentRef.createElement('option');
      option.value = candidate.id;
      option.textContent = `${candidate.taskName} · ${new Date(candidate.startedAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`;
      return option;
    }));
  }
  const chooseSession = documentRef.getElementById('choose-session-start');
  if (chooseSession) chooseSession.hidden = candidates.length < 2;
  const conflictControls = documentRef.getElementById('session-conflict-controls');
  if (conflictControls) conflictControls.hidden = candidates.length < 2;
  const fieldConflicts = snapshot?.fieldConflicts ?? [];
  const fieldConflictControls = documentRef.getElementById('field-conflict-controls');
  if (fieldConflictControls) fieldConflictControls.hidden = fieldConflicts.length === 0;
  const fieldConflictState = documentRef.getElementById('field-conflict-state');
  if (fieldConflictState) fieldConflictState.textContent = fieldConflicts.length
    ? `Choose which value to keep for ${fieldConflicts[0].path}.` : '';
  const fieldChoice = documentRef.getElementById('field-conflict-choice');
  if (fieldChoice && fieldConflicts.length) {
    const conflict = fieldConflicts[0];
    const historyById = new Map((snapshot?.studyHistory ?? []).map(session => [session.id, session]));
    const label = (source, id) => conflict.path.startsWith('studyHistory.')
      ? `${source === 'remote' ? 'Synced history' : 'Phone history'}: ${historyById.get(id)?.taskName ?? id}`
      : `${source === 'remote' ? 'Synced value' : 'This device'}: ${id}`;
    fieldChoice.replaceChildren?.(...[
      ['remote', label('remote', conflict.remoteValue)],
      ['local', label('local', conflict.localValue)],
    ].map(([source, label]) => {
      const option = documentRef.createElement('option');
      option.value = source;
      option.textContent = label;
      return option;
    }));
  }
  const resolveFieldButton = documentRef.getElementById('resolve-field-conflict');
  if (resolveFieldButton) resolveFieldButton.disabled = !state.online || state.saving;
  documentRef.getElementById('focus-heading').textContent = active ? 'In focus' : 'Focus plan';
  documentRef.getElementById('focus-heading').classList?.toggle('is-active', active);
  documentRef.getElementById('focus-live').hidden = !active;
  documentRef.querySelector?.('.focus-board')?.classList?.toggle('is-active', active);
  state.focusBlockId = current?.id ?? null;
  renderFocusMeter(documentRef, current, now);
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

  protection.textContent = 'Protection is active during a study session or scheduled focus block.';
  sync.textContent = error || !connected ? 'Offline. Using cached plan.' : 'Synced. Using latest plan.';
  currentBlock.textContent = current?.name ?? 'No active focus block';
  plannedEnd.hidden = !current;
  plannedEnd.textContent = !current ? '' : current.studySession
    ? `Planned end: ${new Date(current.endsAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}`
    : `Planned end: ${current.endTime}`;
}

function ensureCustomCheckbox(documentRef, state, domain) {
  if (state.customCheckboxes?.has(domain)) return state.customCheckboxes.get(domain);
  const wrapper = documentRef.createElement('label');
  wrapper.className = 'site-row';
  wrapper.setAttribute?.('data-monogram', domain[0].toUpperCase());
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
    pendingSessionStart: saved.pendingSessionStart ?? null,
    pendingSessionStop: saved.pendingSessionStop ?? null,
  };
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
  const focusClock = globalThis.setInterval(() => {
    if (documentRef.visibilityState === 'hidden') return;
    const current = state.snapshot ? getCurrentAndNext(state.snapshot, now()).current : null;
    if ((current?.id ?? null) !== state.focusBlockId) {
      show();
      return;
    }
    renderFocusMeter(documentRef, current, now());
  }, 1000);
  focusClock?.unref?.();
  const updateRules = async () => {
    await applySnapshotRules(chromeApi, state.snapshot, now());
    show();
  };
  const readLatest = () => readSnapshot(state.apiUrl, state.apiToken, fetchImpl);
  documentRef.getElementById('start-session').addEventListener('click', async () => {
    const duration = Number(documentRef.getElementById('session-duration').value);
    if (!Number.isSafeInteger(duration) || duration <= 0) {
      state.sessionMessage = 'Enter a positive whole number of minutes.';
      show();
      return;
    }
    if (!state.snapshot || !state.connected || state.saving) {
      state.sessionMessage = 'Connect to the sync API at least once before starting a study session.';
      show();
      return;
    }
    if (state.snapshot.pendingStudyReviewSessionId) {
      state.sessionMessage = 'Finish the pending phone review before starting another session.';
      show();
      return;
    }
    if (state.snapshot.activeStudySession) {
      state.sessionMessage = Date.parse(state.snapshot.activeStudySession.endsAt) > now().getTime()
        ? 'A study session is already active.'
        : 'Wait for the phone to sync this session and its review before starting another.';
      show();
      return;
    }
    const startedAt = now();
    const session = {
      id: `chrome-${globalThis.crypto?.randomUUID?.() ?? `${startedAt.getTime()}-${Math.random().toString(36).slice(2, 10)}`}`,
      taskName: 'Study session',
      startedAt: startedAt.toISOString(),
      endsAt: new Date(startedAt.getTime() + duration * 60_000).toISOString(),
      accessProfile: 'ESSENTIALS_ONLY',
    };
    const previousSnapshot = state.snapshot;
    state.saving = true;
    state.sessionMessage = null;
    state.snapshot = { ...state.snapshot, activeStudySession: session, phoneReceivedStudySessionId: null };
    state.pendingSessionStart = session;
    show();
    try {
      await chromeApi.storage.local.set({ lastValidSnapshot: state.snapshot, lastSnapshotRevision: state.revision });
      await chromeApi.storage.local.set({ pendingSessionStart: session });
      await updateRules();
      await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
      const endpoint = snapshotEndpoint(state.apiUrl);
      let base = state.snapshot;
      let revision = state.revision;
      let snapshot = { ...base, activeStudySession: session, phoneReceivedStudySessionId: null };
      let response = await fetchImpl(endpoint, {
        method: 'PUT',
        headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ expectedRevision: revision, snapshot }),
        cache: 'no-store', credentials: 'omit', redirect: 'error',
      });
      if (response.status === 409) {
        const latest = await readLatest();
        const remoteSession = latest.snapshot.activeStudySession;
        if (remoteSession || latest.snapshot.pendingStudyReviewSessionId) {
          state.snapshot = latest.snapshot;
          state.revision = latest.revision;
          state.sessionMessage = latest.snapshot.pendingStudyReviewSessionId
            ? 'Finish the pending phone review before starting another session.'
            : Date.parse(remoteSession.endsAt) > startedAt.getTime()
              ? 'A study session is already active on a connected device.'
              : 'Wait for the phone to sync this session and its review before starting another.';
          await chromeApi.storage.local.set({ lastValidSnapshot: latest.snapshot, lastSnapshotRevision: latest.revision });
          await updateRules();
          return;
        }
        base = latest.snapshot;
        revision = latest.revision;
        snapshot = { ...base, activeStudySession: session, phoneReceivedStudySessionId: null };
        response = await fetchImpl(endpoint, {
          method: 'PUT',
          headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ expectedRevision: revision, snapshot }),
          cache: 'no-store', credentials: 'omit', redirect: 'error',
        });
      }
      if (!response.ok) throw new Error(response.status === 409
        ? 'Another session started. Sync and check the active timer.'
        : `Could not start the study session (HTTP ${response.status}).`);
      const payload = await response.json();
      if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
      state.snapshot = payload.snapshot;
      state.revision = payload.revision;
      state.pendingSessionStart = null;
      state.newEmptyBlocklist = false;
      state.online = true;
      state.error = null;
      state.lastSuccessfulSync = now().toISOString();
      await chromeApi.storage.local.set({ lastValidSnapshot: payload.snapshot, lastSuccessfulSync: state.lastSuccessfulSync, lastSnapshotRevision: payload.revision });
      await updateRules();
      await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
    } catch (cause) {
      if (cause instanceof TypeError) {
        state.online = false;
        state.error = connectionError(state.apiUrl);
        state.sessionMessage = 'Study session is running on this computer. Waiting to send it to the phone.';
      } else {
        state.snapshot = previousSnapshot;
        state.pendingSessionStart = null;
        await chromeApi.storage.local.set({ lastValidSnapshot: previousSnapshot, lastSnapshotRevision: state.revision, pendingSessionStart: null });
        state.error = cause?.message || 'Could not start the study session.';
        state.sessionMessage = state.error;
      }
      await updateRules();
    } finally {
      state.saving = false;
      show();
    }
  });
  documentRef.getElementById('choose-session-start')?.addEventListener('click', async () => {
    const chosenId = documentRef.getElementById('session-start-choice')?.value;
    const candidates = state.snapshot?.sessionStartCandidates ?? [];
    const chosen = candidates.find(candidate => candidate.id === chosenId);
    if (!chosen || !state.online || state.saving) return;
    state.saving = true;
    show();
    try {
      const snapshot = { ...state.snapshot, activeStudySession: chosen, sessionStartCandidates: [], phoneReceivedStudySessionId: null };
      const response = await fetchImpl(snapshotEndpoint(state.apiUrl), {
        method: 'PUT', headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ expectedRevision: state.revision, snapshot }), cache: 'no-store', credentials: 'omit', redirect: 'error',
      });
      if (!response.ok) throw new Error(`Could not share the chosen study session (HTTP ${response.status}).`);
      const payload = await response.json();
      if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
      state.snapshot = payload.snapshot;
      state.revision = payload.revision;
      state.pendingSessionStart = null;
      state.sessionMessage = null;
      await chromeApi.storage.local.set({ lastValidSnapshot: payload.snapshot, lastSnapshotRevision: payload.revision, pendingSessionStart: null });
      await updateRules();
    } catch (cause) {
      state.sessionMessage = cause?.message || 'Could not share the chosen study session.';
    } finally {
      state.saving = false;
      show();
    }
  });
  documentRef.getElementById('resolve-field-conflict')?.addEventListener('click', async () => {
    const conflict = state.snapshot?.fieldConflicts?.[0];
    const choice = documentRef.getElementById('field-conflict-choice')?.value;
    if (!conflict || !['remote', 'local'].includes(choice) || !state.online || state.saving) return;
    state.saving = true;
    show();
    try {
      const value = choice === 'remote' ? conflict.remoteValue : conflict.localValue;
      const snapshot = {
        ...withFieldValue(state.snapshot, conflict, value),
        fieldConflicts: state.snapshot.fieldConflicts.filter(item => item.path !== conflict.path),
      };
      const response = await fetchImpl(snapshotEndpoint(state.apiUrl), {
        method: 'PUT', headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ expectedRevision: state.revision, snapshot }), cache: 'no-store', credentials: 'omit', redirect: 'error',
      });
      if (!response.ok) throw new Error(`Could not save the chosen value (HTTP ${response.status}). Sync again and retry.`);
      const payload = await response.json();
      if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
      state.snapshot = payload.snapshot;
      state.revision = payload.revision;
      state.lastSuccessfulSync = now().toISOString();
      await chromeApi.storage.local.set({ lastValidSnapshot: payload.snapshot, lastSnapshotRevision: payload.revision, lastSuccessfulSync: state.lastSuccessfulSync });
      await updateRules();
    } catch (cause) {
      state.sessionMessage = cause?.message || 'Could not resolve the conflicting value.';
    } finally {
      state.saving = false;
      show();
    }
  });
  documentRef.getElementById('stop-session').addEventListener('click', async () => {
    const session = state.snapshot?.activeStudySession;
    if (!session || !state.connected || state.saving) return;
    state.pendingSessionStop = session;
    state.snapshot = {
      ...state.snapshot,
      activeStudySession: null,
      pendingStudyReviewSessionId: state.snapshot.pendingStudyReviewSessionId ?? session.id,
    };
    state.sessionMessage = 'Study session stop pending. It will sync when Chrome reconnects.';
    await chromeApi.storage.local.set({ lastValidSnapshot: state.snapshot, pendingSessionStop: session });
    await updateRules();
    await sync();
  });
  const persistSessionStop = async (payload) => {
    state.snapshot = payload.snapshot;
    state.revision = payload.revision;
    state.pendingSessionStop = null;
    state.lastSuccessfulSync = now().toISOString();
    state.online = true;
    state.error = null;
    state.sessionMessage = null;
    await chromeApi.storage.local.set({
      lastValidSnapshot: payload.snapshot, lastSuccessfulSync: state.lastSuccessfulSync,
      lastSnapshotRevision: payload.revision, pendingSessionStop: null,
    });
  };
  const publishSessionStop = async (remote) => {
    const pending = state.pendingSessionStop;
    if (!pending) return remote;
    if (!remote.snapshot.activeStudySession && remote.snapshot.pendingStudyReviewSessionId === pending.id) {
      state.pendingSessionStop = null;
      await chromeApi.storage.local.set({ pendingSessionStop: null });
      return remote;
    }
    if (!remote.snapshot.activeStudySession
      && !remote.snapshot.pendingStudyReviewSessionId
      && remote.snapshot.studyHistory?.some(session => session.id === pending.id && session.state === 'REVIEWED')) {
      state.pendingSessionStop = null;
      await chromeApi.storage.local.set({ pendingSessionStop: null });
      return remote;
    }
    if (remote.snapshot.activeStudySession?.id !== pending.id) {
      throw new Error('The shared study session changed before the pending stop synced.');
    }
    const snapshot = {
      ...remote.snapshot,
      activeStudySession: null,
      pendingStudyReviewSessionId: remote.snapshot.pendingStudyReviewSessionId ?? pending.id,
    };
    const response = await fetchImpl(snapshotEndpoint(state.apiUrl), {
      method: 'PUT', headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ expectedRevision: remote.revision, snapshot }),
      cache: 'no-store', credentials: 'omit', redirect: 'error',
    });
    if (!response.ok) throw new Error(`Could not sync the pending study session stop (HTTP ${response.status}).`);
    return response.json();
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
      if (state.pendingSessionStop) {
        payload = await publishSessionStop(payload);
        if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
        await persistSessionStop(payload);
      }
      if (state.pendingSessionStart) {
        const pending = state.pendingSessionStart;
        if (Date.parse(pending.endsAt) <= now().getTime()) {
          const durationMillis = Date.parse(pending.endsAt) - Date.parse(pending.startedAt);
          const ended = {
            id: pending.id,
            taskName: pending.taskName,
            durationMillis,
            accessProfile: pending.accessProfile,
            startedAt: pending.startedAt,
            endedAt: pending.endsAt,
            actualMillis: durationMillis,
            state: 'PENDING_REVIEW',
            completionStatus: null,
            note: '', remaining: '', breakStartedAt: null, breakDurationMillis: 0, nextTask: '',
          };
          const snapshot = {
            ...payload.snapshot,
            activeStudySession: payload.snapshot.activeStudySession?.id === pending.id ? null : payload.snapshot.activeStudySession,
            phoneReceivedStudySessionId: payload.snapshot.activeStudySession?.id === pending.id
              ? null : payload.snapshot.phoneReceivedStudySessionId,
            studyHistory: [...(payload.snapshot.studyHistory ?? []).filter(item => item.id !== ended.id), ended],
          };
          const response = await fetchImpl(snapshotEndpoint(state.apiUrl), {
            method: 'PUT', headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ expectedRevision: payload.revision, snapshot }),
            cache: 'no-store', credentials: 'omit', redirect: 'error',
          });
          if (!response.ok) throw new Error(`Could not record the expired study session (HTTP ${response.status}).`);
          payload = await response.json();
          if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
          state.pendingSessionStart = null;
          await chromeApi.storage.local.set({ pendingSessionStart: null });
        } else if (payload.snapshot.activeStudySession && payload.snapshot.activeStudySession.id !== pending.id) {
          const candidates = [...(payload.snapshot.sessionStartCandidates ?? []), payload.snapshot.activeStudySession, pending]
            .filter((candidate, index, all) => all.findIndex(value => value.id === candidate.id) === index)
            .slice(0, 2);
          const conflicted = { ...payload.snapshot, sessionStartCandidates: candidates, phoneReceivedStudySessionId: null };
          const response = await fetchImpl(snapshotEndpoint(state.apiUrl), {
            method: 'PUT', headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ expectedRevision: payload.revision, snapshot: conflicted }),
            cache: 'no-store', credentials: 'omit', redirect: 'error',
          });
          if (!response.ok) throw new Error(`Could not preserve both offline study sessions (HTTP ${response.status}).`);
          payload = await response.json();
          state.pendingSessionStart = null;
          await chromeApi.storage.local.set({ pendingSessionStart: null });
        } else if (!payload.snapshot.activeStudySession && !payload.snapshot.pendingStudyReviewSessionId) {
          const resumed = { ...payload.snapshot, activeStudySession: pending, phoneReceivedStudySessionId: null };
          const response = await fetchImpl(snapshotEndpoint(state.apiUrl), {
            method: 'PUT', headers: { Accept: 'application/json', Authorization: `Bearer ${state.apiToken}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ expectedRevision: payload.revision, snapshot: resumed }),
            cache: 'no-store', credentials: 'omit', redirect: 'error',
          });
          if (!response.ok) throw new Error(`Could not send the pending study session (HTTP ${response.status}).`);
          payload = await response.json();
          state.pendingSessionStart = null;
          await chromeApi.storage.local.set({ pendingSessionStart: null });
        } else if (payload.snapshot.activeStudySession?.id === pending.id) {
          state.pendingSessionStart = null;
          await chromeApi.storage.local.set({ pendingSessionStart: null });
        }
      }
      if (state.pendingSessionStart === null && payload.snapshot.sessionStartCandidates?.length > 1) {
        state.sessionMessage = 'Choose which offline study session to share.';
      }
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
