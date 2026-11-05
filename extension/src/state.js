export const STATE_KEY = 'focusSyncState';

export const EMPTY_STATE = Object.freeze({
  settings: null,
  snapshot: null,
  revision: null,
  serverUpdatedAt: null,
  lastSyncAt: null,
  syncError: null,
  enforcementError: null,
});

export async function readExtensionState() {
  const stored = await chrome.storage.local.get(STATE_KEY);
  return { ...EMPTY_STATE, ...(stored[STATE_KEY] ?? {}) };
}

export async function updateExtensionState(patch) {
  const state = { ...await readExtensionState(), ...patch };
  await chrome.storage.local.set({ [STATE_KEY]: state });
  return state;
}
