import { fetchLatestSnapshot, getApiOriginPattern } from './sync-api.js';
import {
  getActiveBlock,
  getDomainPermissionOrigins,
  getNextBlock,
  getNextBoundary,
  makeDesktopBlockRules,
} from './policy.js';
import { readExtensionState, updateExtensionState } from './state.js';

const BOUNDARY_ALARM = 'focus-block-boundary';
const SYNC_ALARM = 'focus-snapshot-sync';
const SYNC_PERIOD_MINUTES = 5;
const RULE_ID_START = 1000;
const RULE_ID_END = RULE_ID_START + 100;

let operations = Promise.resolve();

function serialize(operation) {
  operations = operations.then(operation, operation);
  return operations;
}

async function hasOrigins(origins) {
  return origins.length === 0 || chrome.permissions.contains({ origins });
}

async function getAllowedAndMissingDomains(snapshot) {
  const domains = snapshot?.desktopBlockedDomains ?? [];
  const result = await Promise.all(domains.map(async domain => {
    const origins = getDomainPermissionOrigins([domain]);
    return { domain, allowed: await hasOrigins(origins) };
  }));
  return {
    allowed: result.filter(item => item.allowed).map(item => item.domain),
    missing: result.filter(item => !item.allowed).map(item => item.domain),
  };
}

async function removeAndInstallRules(snapshot, activeBlockId, allowedDomains) {
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  const removeRuleIds = existing
    .filter(rule => rule.id >= RULE_ID_START && rule.id < RULE_ID_END)
    .map(rule => rule.id);
  const addRules = makeDesktopBlockRules(snapshot, activeBlockId, allowedDomains);
  await chrome.declarativeNetRequest.updateDynamicRules({ removeRuleIds, addRules });
}

async function scheduleBoundary(snapshot, now = new Date()) {
  await chrome.alarms.clear(BOUNDARY_ALARM);
  const boundary = getNextBoundary(snapshot, now);
  if (boundary) {
    await chrome.alarms.create(BOUNDARY_ALARM, { when: Math.max(boundary.getTime(), now.getTime() + 1000) });
  }
}

async function applyCachedSnapshot() {
  const { snapshot } = await readExtensionState();
  if (!snapshot) {
    await removeAndInstallRules(null, null, []);
    await scheduleBoundary(null);
    await updateExtensionState({ enforcementError: null });
    return;
  }

  try {
    const activeBlockId = getActiveBlock(snapshot);
    const { allowed } = await getAllowedAndMissingDomains(snapshot);
    await removeAndInstallRules(snapshot, activeBlockId, allowed);
    await scheduleBoundary(snapshot);
    await updateExtensionState({ enforcementError: null });
  } catch (error) {
    await updateExtensionState({
      enforcementError: {
        message: `Chrome could not update site blocking rules: ${error.message}`,
        at: new Date().toISOString(),
      },
    });
  }
}

async function keepSyncAlarm() {
  const existing = await chrome.alarms.get(SYNC_ALARM);
  if (!existing) {
    await chrome.alarms.create(SYNC_ALARM, { periodInMinutes: SYNC_PERIOD_MINUTES });
  }
}

async function syncLatestSnapshot() {
  const { settings } = await readExtensionState();
  if (!settings) {
    await updateExtensionState({
      syncError: {
        message: 'Add the sync API endpoint and read token in Settings to connect this extension.',
        code: 'missing_settings',
        at: new Date().toISOString(),
      },
    });
    return;
  }

  try {
    const apiOrigin = getApiOriginPattern(settings.endpoint);
    if (!await hasOrigins([apiOrigin])) {
      await updateExtensionState({
        syncError: {
          message: 'Allow access to the configured sync API in Settings, then try syncing again.',
          code: 'missing_api_permission',
          at: new Date().toISOString(),
        },
      });
      return;
    }
    const result = await fetchLatestSnapshot(settings.endpoint, settings.readToken);
    await updateExtensionState({
      snapshot: result.snapshot,
      revision: result.revision,
      serverUpdatedAt: result.updatedAt,
      lastSyncAt: new Date().toISOString(),
      syncError: null,
    });
  } catch (error) {
    await updateExtensionState({
      syncError: {
        message: error.message,
        code: error.code ?? 'sync_failed',
        at: new Date().toISOString(),
      },
    });
  }
}

async function syncAndEvaluate() {
  await syncLatestSnapshot();
  await applyCachedSnapshot();
}

async function getUiStatus() {
  const state = await readExtensionState();
  const now = new Date();
  const activeBlockId = getActiveBlock(state.snapshot, now);
  const activeBlock = state.snapshot?.blocks?.find(block => block.id === activeBlockId) ?? null;
  const { allowed, missing } = await getAllowedAndMissingDomains(state.snapshot);
  const apiPermissionMissing = state.settings
    ? !await hasOrigins([getApiOriginPattern(state.settings.endpoint)])
    : false;
  return {
    configured: Boolean(state.settings),
    current: activeBlock,
    next: getNextBlock(state.snapshot, now),
    lastSyncAt: state.lastSyncAt,
    revision: state.revision,
    syncError: state.syncError,
    enforcementError: state.enforcementError,
    missingDomains: missing,
    allowedDomains: allowed,
    apiPermissionMissing,
  };
}

async function bootstrap() {
  await chrome.storage.local.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' });
  await keepSyncAlarm();
  await applyCachedSnapshot();
  await syncAndEvaluate();
}

chrome.runtime.onInstalled.addListener(() => {
  void serialize(bootstrap);
});

chrome.runtime.onStartup.addListener(() => {
  void serialize(bootstrap);
});

chrome.alarms.onAlarm.addListener(alarm => {
  if (alarm.name === SYNC_ALARM || alarm.name === BOUNDARY_ALARM) {
    void serialize(syncAndEvaluate);
  }
});

chrome.permissions.onAdded.addListener(() => {
  void serialize(applyCachedSnapshot);
});

chrome.permissions.onRemoved.addListener(() => {
  void serialize(applyCachedSnapshot);
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'get-status') {
    serialize(getUiStatus).then(sendResponse, error => sendResponse({ error: error.message }));
    return true;
  }
  if (message?.type === 'sync-now') {
    serialize(async () => {
      await syncAndEvaluate();
      return getUiStatus();
    }).then(sendResponse, error => sendResponse({ error: error.message }));
    return true;
  }
  if (message?.type === 'permissions-updated') {
    serialize(async () => {
      await applyCachedSnapshot();
      return getUiStatus();
    }).then(sendResponse, error => sendResponse({ error: error.message }));
    return true;
  }
  return undefined;
});

void serialize(bootstrap);
