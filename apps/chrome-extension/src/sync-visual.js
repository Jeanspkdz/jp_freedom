const states = new Set(['idle', 'connecting', 'syncing', 'synced', 'error']);
const relativeClocks = new WeakMap();
const motionObservers = new WeakSet();

function relativeTime(value) {
  const elapsed = Math.max(0, Date.now() - value.getTime());
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} d ago`;
  return value.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function updateLastSync(container) {
  const timestamp = container.querySelector('#last-sync');
  if (!timestamp) return;
  const value = new Date(timestamp.dateTime || container.dataset.lastSyncAt || '');
  timestamp.textContent = Number.isNaN(value.getTime())
    ? 'Last successful sync: Never'
    : `Last successful sync: ${relativeTime(value)}`;
}

// One state switch controls the route, progress cue, and available recovery action.
export function setSyncState(state) {
  const container = globalThis.document?.getElementById('connection-visual');
  if (!container || !states.has(state)) return;
  const view = container.ownerDocument?.defaultView ?? globalThis;
  const documentRef = container.ownerDocument ?? globalThis.document;
  const stateChanged = container.dataset.syncState !== state;
  container.dataset.syncState = state;
  if (stateChanged && view.CustomEvent) {
    container.dispatchEvent(new view.CustomEvent('syncvisualstatechange', { detail: { state } }));
  }
  const progress = container.querySelector('.sync-progress');
  if (progress) progress.hidden = state !== 'syncing';
  const status = container.querySelector('#phone-receipt-state');
  if (status && status.dataset.visualMessage !== status.textContent) {
    status.dataset.visualMessage = status.textContent;
    status.classList.remove('is-decoding');
    void status.offsetWidth;
    status.classList.add('is-decoding');
    status.addEventListener('animationend', () => status.classList.remove('is-decoding'), { once: true });
  }
  const retry = container.querySelector('.sync-retry');
  if (retry) retry.hidden = state !== 'error' || container.dataset.hasSyncConfig !== 'true';
  updateLastSync(container);

  if (!relativeClocks.has(container) && container.dataset.lastSyncAt) {
    const clock = view.setInterval(() => updateLastSync(container), 30_000);
    clock?.unref?.();
    relativeClocks.set(container, clock);
  }

  if (!motionObservers.has(container)) {
    motionObservers.add(container);
    container.dataset.syncVisible = 'true';
    container.dataset.pageVisible = String(!documentRef?.hidden);
    if (view.IntersectionObserver) {
      const observer = new view.IntersectionObserver(([entry]) => {
        container.dataset.syncVisible = String(entry.isIntersecting);
      }, { threshold: 0.01 });
      observer.observe(container);
    }
    documentRef?.addEventListener('visibilitychange', () => {
      container.dataset.pageVisible = String(!documentRef.hidden);
    });
  }

  if (retry && retry.dataset.bound !== 'true') {
    retry.dataset.bound = 'true';
    retry.addEventListener('click', () => {
      const syncNow = container.ownerDocument?.getElementById('sync-now');
      if (syncNow) {
        syncNow.click();
        return;
      }
      if (container.dataset.demo === 'true') {
        container.dataset.lastSyncAt = new Date().toISOString();
        setSyncState('connecting');
        view.setTimeout(() => setSyncState('synced'), 900);
      }
    });
  }
}
