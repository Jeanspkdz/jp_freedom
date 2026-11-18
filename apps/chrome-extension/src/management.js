import { applySnapshotRules } from './enforcement.js';
import { getCurrentAndNext } from './policy.js';
import { readSnapshot, snapshotEndpoint, validSnapshotResponse } from './sync-client.js';
import { isValidDomain } from './domain.js';
import { readStudySession, startStudySession, stopStudySession, activeStudySession, makeLocalStudySession, publishPendingStudySession, resolveStudyConflict } from './study-session.js';
import { setClockTime } from './flip-clock.js';

const STORAGE_KEYS = ['apiUrl', 'apiToken', 'lastValidSnapshot', 'lastSuccessfulSync', 'lastSnapshotRevision', 'lastStudySession', 'studyPending', 'studyConflict'];
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

  let start = planTimeInSeconds(current.startTime);
  let end = planTimeInSeconds(current.endTime);
  let currentTime = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  if (end <= start) end += 24 * 3600;
  if (currentTime < start && end > 24 * 3600) currentTime += 24 * 3600;
  const duration = end - start;
  const elapsed = Math.min(Math.max(currentTime - start, 0), duration);
  const secondsLeft = Math.max(end - currentTime, 0);
  const percent = duration > 0 ? elapsed / duration * 100 : 0;
  remainingValue.textContent = `${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, '0')}`;
  remaining.hidden = false;
  progress.hidden = false;
  fill.style.width = `${percent.toFixed(2)}%`;
  progress.setAttribute('aria-valuenow', String(Math.round(percent)));
}

// The countdown updates without rebuilding schedule rows or restarting their entry animations.
function renderStudyTimer(documentRef, state, now) {
  const timer = documentRef.getElementById('study-timer-status');
  const remaining = documentRef.getElementById('study-timer-remaining');
  const running = activeStudySession(state.studySession, now);
  const seconds = running ? Math.ceil((Date.parse(state.studySession.startedAt) + state.studySession.durationMillis - now.getTime()) / 1000) : 0;
  const durationInput = documentRef.getElementById('study-duration');
  setClockTime(documentRef, running ? Math.max(0, seconds) : Number(durationInput.value) * 60,
    documentRef.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
  const date = documentRef.getElementById('timer-date');
  if (date) date.textContent = now.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  remaining.hidden = !running;
  remaining.textContent = running ? `${Math.floor(seconds / 3600)}:${String(Math.floor(seconds / 60) % 60).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}` : '';
  timer.textContent = state.studyTimerMessage ?? (state.studyConflict
    ? 'A different timer started on Android while Chrome was offline. Both attempts were kept.'
    : running ? 'Study session in progress.'
    : state.studySession?.state === 'ACTIVE' ? 'Timer finished. Review this session on Android.'
    : state.studySession?.state === 'PENDING_REVIEW' ? 'Review the previous session on Android before starting another.'
    : state.studyPending ? 'Timer saved here. Connect to share it with Android.'
    : state.connected ? 'Choose a duration and start a study session.' : 'Start a timer here. Connect to share it with Android.');
  documentRef.getElementById('study-timer-form').hidden = running;
  documentRef.getElementById('start-study-timer').disabled = state.studyTimerBusy
    || Boolean(state.studySession && state.studySession.state !== 'REVIEWED');
  documentRef.getElementById('stop-study-timer').hidden = !running;
  documentRef.getElementById('stop-study-timer').disabled = state.studyTimerBusy;
  documentRef.getElementById('study-conflict-actions').hidden = !state.studyConflict;
  const stateLabel = documentRef.querySelector?.('.timer-state-label');
  if (stateLabel) stateLabel.textContent = running ? 'Focus session active' : state.connected ? 'Connected' : 'Not connected';
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
    row.setAttribute?.('data-list-item', '');
    row.classList?.add('is-entering');
    row.addEventListener?.('animationend', () => row.classList?.remove('is-entering'), { once: true });
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
  const page = documentRef.documentElement.dataset.page ?? 'all';
  const sync = documentRef.getElementById('sync-state');
  sync.setAttribute?.('data-state', connected && state.online && !error ? 'synced' : 'offline');
  const current = snapshot ? getCurrentAndNext(snapshot, now).current : null;
  const active = Boolean(current);
  state.focusBlockId = current?.id ?? null;
  if (page === 'plan' || page === 'all') {
    renderStudyTimer(documentRef, state, now);
    const protection = documentRef.getElementById('protection-state');
    const plannedEnd = documentRef.getElementById('planned-end');
    const currentBlock = documentRef.getElementById('current-block');
    const focusHeading = documentRef.getElementById('focus-heading');
    focusHeading.textContent = 'Focus session';
    focusHeading.classList?.toggle('is-active', active);
    documentRef.getElementById('focus-live').hidden = !active;
    documentRef.querySelector?.('.focus-board')?.classList?.toggle('is-active', active);
    renderFocusMeter(documentRef, current, now);
    const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    documentRef.getElementById('today-intro-date').textContent = now.toLocaleDateString(undefined, {
      weekday: 'long', month: 'long', day: 'numeric',
    });
    renderDayPlan(documentRef, snapshot, now, 'today', current?.id ?? null);
    renderDayPlan(documentRef, snapshot, tomorrow, 'tomorrow', current?.id ?? null);
    if (!snapshot) {
      protection.textContent = 'Protection inactive. No valid plan has synced.';
      currentBlock.textContent = 'No active focus block';
      plannedEnd.hidden = true;
      plannedEnd.textContent = '';
    } else {
      protection.textContent = 'Protection is active when a focus block is scheduled.';
      currentBlock.textContent = current?.name ?? 'No active focus block';
      plannedEnd.hidden = !current;
      plannedEnd.textContent = current ? `Planned end: ${current.endTime}` : '';
    }
  }
  if (page === 'sites' || page === 'all') {
    const displayedDomains = snapshot
      ? (state.newEmptyBlocklist ? [...DEFAULT_DESKTOP_DOMAINS] : [...new Set(snapshot.desktopBlockedDomains)])
      : [...DEFAULT_DESKTOP_DOMAINS];
    state.displayedDomains = displayedDomains;
    const count = String(displayedDomains.length).padStart(2, '0');
    documentRef.querySelectorAll?.('.sites-digit')?.forEach((digit, index) => {
      digit.textContent = count[index] ?? '';
      digit.dataset.digit = count[index] ?? '';
    });
    const countLive = documentRef.getElementById('sites-count-live');
    if (countLive?.textContent !== undefined) countLive.textContent = `${displayedDomains.length} sites selected`;
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
        const row = checkbox.parentElement;
        if (row?.classList?.add) {
          row.classList.add('is-removing');
          row.addEventListener?.('animationend', () => row.remove(), { once: true });
        } else row?.remove?.();
        state.customCheckboxes.delete(domain);
        continue;
      }
      checkbox.checked = displayedDomains.includes(domain);
      checkbox.disabled = !canEdit || (active && checkbox.checked);
    }
    documentRef.getElementById('custom-domain').disabled = !canEdit;
    documentRef.getElementById('add-domain').disabled = !canEdit;
    documentRef.getElementById('blocklist-status').textContent = state.blocklistMessage
      ?? (!state.connected || !state.online ? 'Connect Chrome to the internet and sync to save blocklist changes.' : '');
  }
  if (page === 'connection' || page === 'all') {
    documentRef.getElementById('last-sync').textContent = `Last successful sync: ${formatLocalTime(lastSuccessfulSync)}`;
    const errorMessage = documentRef.getElementById('sync-error');
    errorMessage.hidden = !error;
    errorMessage.textContent = error ?? '';
  }
  if (!snapshot) {
    sync.textContent = !connected
      ? 'Not connected. Enter your sync API details.'
      : error ? 'Offline. No plan has synced yet.' : 'Connected. Waiting for first sync.';
    return;
  }
  sync.textContent = error || !connected ? 'Offline. Using cached plan.' : 'Synced. Using latest plan.';
}

function ensureCustomCheckbox(documentRef, state, domain) {
  if (state.customCheckboxes?.has(domain)) return state.customCheckboxes.get(domain);
  const wrapper = documentRef.createElement('label');
  wrapper.className = 'site-row';
  wrapper.setAttribute?.('data-list-item', '');
  wrapper.classList?.add('is-entering');
  wrapper.addEventListener?.('animationend', () => wrapper.classList?.remove('is-entering'), { once: true });
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
    studySession: saved.lastStudySession ?? null,
    studyPending: Boolean(saved.studyPending),
    studyConflict: Boolean(saved.studyConflict),
    studyTimerMessage: null,
    studyTimerBusy: false,
  };
  const page = documentRef.documentElement.dataset.page ?? 'all';
  const pageOrder = ['plan', 'sites', 'connection'];
  const setNavigationDirection = target => {
    const currentIndex = pageOrder.indexOf(page);
    const targetIndex = pageOrder.indexOf(target);
    documentRef.documentElement.dataset.nav = targetIndex < currentIndex ? 'back' : 'forward';
    try {
      const sessionStorage = documentRef.defaultView?.sessionStorage ?? globalThis.sessionStorage;
      sessionStorage?.setItem('Tally-nav-direction', documentRef.documentElement.dataset.nav);
    } catch {
      // The new page defaults to a forward transition when session storage is unavailable.
    }
  };
  let leaving = false;
  const openPage = (target, href) => {
    if (leaving) return;
    setNavigationDirection(target);
    const view = documentRef.defaultView;
    const location = view?.location ?? documentRef.location ?? globalThis.location;
    if (!location) return;
    if (view?.location?.protocol !== 'chrome-extension:' || target === page) {
      location.href = href;
      return;
    }
    // Extension URLs currently skip cross-document View Transitions in Chromium.
    leaving = true;
    try { view.sessionStorage?.setItem('Tally-nav-fallback', '1'); } catch { /* Direct navigation remains available. */ }
    documentRef.documentElement.classList.add('is-leaving');
    const duration = view.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 100 : 200;
    globalThis.setTimeout(() => { location.href = href; }, duration);
  };
  const navigateToPage = target => {
    openPage(target, `${target === 'plan' ? 'management' : target}.html`);
  };
  let switchingTab = false;
  const slideTabThenOpen = (target, href) => {
    if (switchingTab || leaving) return;
    switchingTab = true;
    const nav = documentRef.querySelector?.('.view-nav');
    const currentTab = documentRef.querySelector?.('.view-nav [aria-current="page"]');
    const nextTab = [...(documentRef.querySelectorAll?.('.view-nav a') ?? [])]
      .find(tab => tab.getAttribute('href') === href);
    const from = currentTab?.getBoundingClientRect?.();
    const to = nextTab?.getBoundingClientRect?.();
    const activate = () => {
      for (const tab of documentRef.querySelectorAll?.('.view-nav a') ?? []) {
        const selected = tab.getAttribute('href') === href;
        tab.classList.toggle('is-selected', selected);
        if (selected) tab.setAttribute('aria-current', 'page');
        else tab.removeAttribute('aria-current');
      }
    };
    const openTabDocument = () => {
      setNavigationDirection(target);
      documentRef.documentElement.classList.add('tab-pill-only');
      try { documentRef.defaultView?.sessionStorage?.setItem('Tally-tab-pill-only', '1'); } catch { /* Direct navigation still works. */ }
      (documentRef.defaultView?.location ?? documentRef.location ?? globalThis.location).href = href;
    };
    if (!nav || !from || !to || documentRef.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      activate();
      openTabDocument();
      return;
    }
    const pill = documentRef.createElement('span');
    pill.className = 'nav-pill';
    pill.setAttribute('aria-hidden', 'true');
    const navLeft = nav.getBoundingClientRect().left;
    const fromX = from.left - navLeft + nav.scrollLeft;
    const toX = to.left - navLeft + nav.scrollLeft;
    pill.style.width = `${from.width}px`;
    pill.style.transform = `translateX(${fromX}px)`;
    nav.prepend(pill);
    documentRef.documentElement.classList.add('nav-pill-ready');
    activate();
    const finishNavigation = () => {
      pill.style.width = `${to.width}px`;
      pill.style.transform = `translateX(${toX}px)`;
      openTabDocument();
    };
    if (!pill.animate) return finishNavigation();
    pill.animate([
      { width: `${from.width}px`, transform: `translateX(${fromX}px)` },
      { width: `${to.width}px`, transform: `translateX(${toX}px)` },
    ], { duration: 300, easing: 'cubic-bezier(.4, 0, .2, 1)', fill: 'forwards' }).finished
      .then(finishNavigation, finishNavigation);
  };
  for (const link of documentRef.querySelectorAll?.('.view-nav a') ?? []) {
    link.addEventListener('click', event => {
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button > 0) return;
      const href = link.getAttribute('href');
      const target = href === 'management.html' ? 'plan' : href?.replace('.html', '');
      if (pageOrder.includes(target)) {
        if (documentRef.defaultView?.location?.protocol === 'chrome-extension:' && target !== page) {
          event.preventDefault();
          slideTabThenOpen(target, href);
        } else setNavigationDirection(target);
      }
    });
  }
  if (typeof globalThis.IntersectionObserver === 'function') {
    const observer = new globalThis.IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList?.add('is-visible');
        observer.unobserve(entry.target);
      }
    }, { threshold: 0.15 });
    for (const element of documentRef.querySelectorAll?.('[data-reveal]') ?? []) observer.observe(element);
  } else {
    for (const element of documentRef.querySelectorAll?.('[data-reveal]') ?? []) element.classList?.add('is-visible');
  }
  if (page === 'connection' || page === 'all') {
    documentRef.getElementById('api-url').value = state.apiUrl;
    documentRef.getElementById('api-token').value = state.apiToken;
  }

  const show = () => render(documentRef, state, now());
  const focusClock = globalThis.setInterval(() => {
    if (documentRef.visibilityState === 'hidden') return;
    const current = state.snapshot ? getCurrentAndNext(state.snapshot, now()).current : null;
    if ((current?.id ?? null) !== state.focusBlockId) {
      show();
      return;
    }
    if (page === 'plan' || page === 'all') {
      renderFocusMeter(documentRef, current, now());
      renderStudyTimer(documentRef, state, now());
    }
  }, 1000);
  focusClock?.unref?.();
  const updateRules = async () => {
    const rulesSnapshot = state.snapshot ?? (state.studySession
      ? { desktopBlockedDomains: DEFAULT_DESKTOP_DOMAINS, blocks: [] } : null);
    await applySnapshotRules(chromeApi, rulesSnapshot, now(), state.studySession);
    show();
  };
  // Background sync can receive a session started or stopped on Android while Plan is open.
  chromeApi.storage.onChanged?.addListener?.((changes, area) => {
    if (area !== 'local') return;
    if ('lastStudySession' in changes) state.studySession = changes.lastStudySession.newValue ?? null;
    if ('studyConflict' in changes) state.studyConflict = Boolean(changes.studyConflict.newValue);
    show();
  });
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
  if (page === 'sites' || page === 'all') {
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
  }
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
      const study = state.studyPending
        ? await publishPendingStudySession(state.apiUrl, state.apiToken, state.studySession, fetchImpl)
        : await readStudySession(state.apiUrl, state.apiToken, fetchImpl).catch(() => null);
      if (study) {
        state.studySession = study.session;
        state.studyPending = false;
        state.studyConflict = false;
        await chromeApi.storage.local.set({ lastStudySession: study.session, lastStudyRevision: study.revision, studyPending: false, studyConflict: false });
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
      if (/Another study session/.test(state.error)) {
        state.studyConflict = true;
        await chromeApi.storage.local.set({ studyConflict: true });
      }
      await updateRules();
    } finally {
      syncButton.classList?.remove('is-syncing');
      syncButton.removeAttribute?.('aria-busy');
      show();
    }
  };

  // Manual timer actions use the shared session record and refresh site rules immediately.
  if (page === 'plan' || page === 'all') {
    for (const [id, choice] of [['keep-chrome-timer', 'chrome'], ['keep-android-timer', 'android']]) {
      documentRef.getElementById(id).addEventListener('click', async () => {
        state.studyTimerBusy = true;
        show();
        try {
          const result = await resolveStudyConflict(state.apiUrl, state.apiToken, state.studySession, choice, fetchImpl);
          state.studySession = result.session;
          state.studyPending = false;
          state.studyConflict = false;
          state.studyTimerMessage = null;
          await chromeApi.storage.local.set({ lastStudySession: result.session,
            lastStudyRevision: result.revision, studyPending: false, studyConflict: false });
          await updateRules();
          await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
        } catch (cause) { state.studyTimerMessage = cause.message ?? 'Could not resolve the timer conflict.'; }
        finally { state.studyTimerBusy = false; show(); }
      });
    }
    const runTimerAction = async (action, localAction) => {
      state.studyTimerBusy = true;
      state.studyTimerMessage = null;
      show();
      try {
        let result;
        try { result = state.connected ? await action() : null; }
        catch (cause) { if (!(cause instanceof TypeError)) throw cause; }
        if (result) {
          state.studySession = result.session;
          state.studyPending = false;
        } else {
          state.studySession = localAction();
          state.studyPending = true;
        }
        await chromeApi.storage.local.set({ lastStudySession: state.studySession,
          ...(result ? { lastStudyRevision: result.revision } : {}), studyPending: state.studyPending });
        await updateRules();
        await chromeApi.runtime?.sendMessage?.({ type: 'refresh-focus-state' });
      } catch (cause) { state.studyTimerMessage = cause.message ?? 'Could not update the study timer.'; }
      finally { state.studyTimerBusy = false; show(); }
    };
    documentRef.getElementById('study-timer-form').addEventListener('submit', event => {
      event.preventDefault();
      const duration = Number(documentRef.getElementById('study-duration').value);
      return runTimerAction(() => startStudySession(state.apiUrl, state.apiToken, duration, fetchImpl, now()),
        () => makeLocalStudySession(duration, now()));
    });
    documentRef.querySelectorAll?.('[data-duration]').forEach(button => button.addEventListener('click', () => {
      const duration = Number(button.dataset.duration);
      documentRef.getElementById('study-duration').value = String(duration);
      documentRef.querySelectorAll('[data-duration]').forEach(option =>
        option.setAttribute('aria-pressed', String(option === button)));
      setClockTime(documentRef, duration * 60,
        documentRef.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
    }));
    if (documentRef.getElementById('study-timer-form').dataset) documentRef.getElementById('study-timer-form').dataset.ready = 'true';
    documentRef.getElementById('stop-study-timer').addEventListener('click', () =>
      runTimerAction(() => state.studyPending ? null : stopStudySession(state.apiUrl, state.apiToken, fetchImpl, now()),
        () => ({ ...state.studySession, endedAt: now().toISOString(),
          actualMillis: Math.max(0, Math.min(now().getTime() - Date.parse(state.studySession.startedAt), state.studySession.durationMillis)),
          state: 'PENDING_REVIEW' })));
  }

  if (page === 'connection' || page === 'all') {
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
  }
  documentRef.getElementById('sync-now').addEventListener('click', async () => {
    await sync();
    if ((!state.connected || state.error) && page !== 'connection') navigateToPage('connection');
  });

  await updateRules();
  if (state.connected) await sync();
  return { sync };
}

if (globalThis.document && globalThis.chrome) {
  void initializeManagement();
}
