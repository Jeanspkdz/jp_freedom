import { getApiOriginPattern, normalizeEndpoint } from './src/sync-api.js';
import { readExtensionState, updateExtensionState } from './src/state.js';

const endpointInput = document.querySelector('#endpoint');
const tokenInput = document.querySelector('#read-token');
const statusElement = document.querySelector('#settings-status');
const saveButton = document.querySelector('#save-connection');
const grantButton = document.querySelector('#grant-api-access');
const retryButton = document.querySelector('#retry-sync');

let savedSettings = null;

async function refreshStatus() {
  const status = await chrome.runtime.sendMessage({ type: 'get-status' });
  if (status?.lastSyncAt) {
    statusElement.textContent = `Connected. Last sync: ${new Date(status.lastSyncAt).toLocaleString()}.`;
  } else if (status?.syncError) {
    statusElement.textContent = status.syncError.message;
  }
  grantButton.hidden = !status?.apiPermissionMissing;
  retryButton.hidden = !status?.configured;
}

document.querySelector('#connection-form').addEventListener('submit', async event => {
  event.preventDefault();
  saveButton.disabled = true;
  statusElement.textContent = '';

  try {
    const endpoint = normalizeEndpoint(endpointInput.value);
    const token = tokenInput.value.trim() || savedSettings?.readToken;
    if (!token) throw new Error('Enter the sync API read token.');
    const settings = { endpoint, readToken: token };
    const origin = getApiOriginPattern(endpoint);
    const accessRequest = chrome.permissions.request({ origins: [origin] });

    await updateExtensionState({ settings });
    savedSettings = settings;
    const granted = await accessRequest;
    if (!granted) {
      statusElement.textContent = 'Connection saved. Grant access to the sync API host to sync the phone snapshot.';
      grantButton.hidden = false;
      retryButton.hidden = false;
      return;
    }

    const status = await chrome.runtime.sendMessage({ type: 'sync-now' });
    if (status?.syncError) {
      statusElement.textContent = status.syncError.message;
    } else {
      statusElement.textContent = 'Connected. The phone snapshot is synced and cached for offline enforcement.';
    }
    grantButton.hidden = !status?.apiPermissionMissing;
    retryButton.hidden = false;
  } catch (error) {
    statusElement.textContent = error.message || 'Could not save sync settings.';
  } finally {
    saveButton.disabled = false;
  }
});

grantButton.addEventListener('click', async () => {
  if (!savedSettings) return;
  try {
    const granted = await chrome.permissions.request({
      origins: [getApiOriginPattern(savedSettings.endpoint)],
    });
    if (!granted) {
      statusElement.textContent = 'Chrome did not grant sync API access. Use this button to try again.';
      return;
    }
    const status = await chrome.runtime.sendMessage({ type: 'sync-now' });
    statusElement.textContent = status?.syncError?.message
      ?? 'Connected. The phone snapshot is synced and cached for offline enforcement.';
    grantButton.hidden = true;
  } catch (error) {
    statusElement.textContent = error.message || 'Could not request sync API access.';
  }
});

retryButton.addEventListener('click', async () => {
  retryButton.disabled = true;
  try {
    const status = await chrome.runtime.sendMessage({ type: 'sync-now' });
    statusElement.textContent = status?.syncError?.message
      ?? 'The phone snapshot is synced and cached for offline enforcement.';
    grantButton.hidden = !status?.apiPermissionMissing;
  } catch {
    statusElement.textContent = 'Could not reach the extension service worker. Try again.';
  } finally {
    retryButton.disabled = false;
  }
});

void readExtensionState().then(state => {
  savedSettings = state.settings;
  if (savedSettings) {
    endpointInput.value = savedSettings.endpoint;
    tokenInput.value = savedSettings.readToken;
  }
  return refreshStatus();
}).catch(() => {
  statusElement.textContent = 'Could not load saved sync settings.';
});
