import { getDomainPermissionOrigins } from './src/policy.js';
import { getCurrentBlockMessage } from './src/view-model.js';

const elements = {
  current: document.querySelector('#current-block'),
  nextRow: document.querySelector('#next-row'),
  next: document.querySelector('#next-block'),
  lastSync: document.querySelector('#last-sync'),
  sitePermissionPanel: document.querySelector('#site-permission-panel'),
  sitePermissionMessage: document.querySelector('#site-permission-message'),
  apiPermissionPanel: document.querySelector('#api-permission-panel'),
  syncErrorPanel: document.querySelector('#sync-error-panel'),
  syncError: document.querySelector('#sync-error'),
  enforcementErrorPanel: document.querySelector('#enforcement-error-panel'),
  enforcementError: document.querySelector('#enforcement-error'),
  status: document.querySelector('#status-message'),
  syncButton: document.querySelector('#sync-now'),
  grantButton: document.querySelector('#grant-site-access'),
};

let currentStatus = null;

function formatTime(value) {
  const [hours, minutes] = value.split(':').map(Number);
  const date = new Date();
  date.setHours(hours, minutes, 0, 0);
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

function formatLocalDateTime(value) {
  return new Date(value).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' });
}

function formatNextBlock(block) {
  const time = formatTime(block.startTime);
  const today = new Date();
  const todayKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  if (block.date === todayKey) return `${block.name} · ${time}`;
  const [year, month, day] = block.date.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return `${block.name} · ${date.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })} · ${time}`;
}

function render(status) {
  currentStatus = status;
  elements.current.textContent = getCurrentBlockMessage(status);

  elements.nextRow.hidden = !status?.next;
  if (status?.next) {
    elements.next.textContent = formatNextBlock(status.next);
  }

  elements.lastSync.textContent = status?.lastSyncAt
    ? `Last sync: ${formatLocalDateTime(status.lastSyncAt)}`
    : 'Last sync: not yet';

  const missingDomains = status?.missingDomains ?? [];
  elements.sitePermissionPanel.hidden = missingDomains.length === 0;
  elements.sitePermissionMessage.textContent = missingDomains.length === 1
    ? `Allow access to ${missingDomains[0]} and its subdomains so Chrome can enforce the phone blocklist.`
    : `Allow access to ${missingDomains.slice(0, 3).join(', ')}${missingDomains.length > 3 ? `, and ${missingDomains.length - 3} more` : ''} and their subdomains so Chrome can enforce the phone blocklist.`;
  elements.apiPermissionPanel.hidden = !status?.apiPermissionMissing;

  elements.syncErrorPanel.hidden = !status?.syncError;
  elements.syncError.textContent = status?.syncError?.message ?? '';
  elements.enforcementErrorPanel.hidden = !status?.enforcementError;
  elements.enforcementError.textContent = status?.enforcementError?.message ?? '';
}

async function refresh() {
  const status = await chrome.runtime.sendMessage({ type: 'get-status' });
  render(status);
}

document.querySelector('#settings').addEventListener('click', () => chrome.runtime.openOptionsPage());
document.querySelector('#open-settings').addEventListener('click', () => chrome.runtime.openOptionsPage());

elements.syncButton.addEventListener('click', async () => {
  elements.syncButton.disabled = true;
  elements.status.textContent = 'Syncing…';
  try {
    const status = await chrome.runtime.sendMessage({ type: 'sync-now' });
    render(status);
    elements.status.textContent = status?.syncError ? 'Sync could not finish. See the message above.' : 'Schedule updated.';
  } catch {
    elements.status.textContent = 'Could not reach the extension service worker. Try again.';
  } finally {
    elements.syncButton.disabled = false;
  }
});

elements.grantButton.addEventListener('click', async () => {
  const origins = getDomainPermissionOrigins(currentStatus?.missingDomains ?? []);
  if (origins.length === 0) return;
  elements.grantButton.disabled = true;
  try {
    const granted = await chrome.permissions.request({ origins });
    if (!granted) {
      elements.status.textContent = 'Chrome did not grant site access. Use Allow site access to try again.';
      return;
    }
    const status = await chrome.runtime.sendMessage({ type: 'permissions-updated' });
    render(status);
    elements.status.textContent = status?.missingDomains?.length
      ? 'Some site permissions are still missing.'
      : 'Site access granted. Chrome can enforce the synced blocklist.';
  } catch {
    elements.status.textContent = 'Chrome could not update site access. Try again from this button.';
  } finally {
    elements.grantButton.disabled = false;
  }
});

void refresh().catch(() => {
  elements.current.textContent = 'The extension could not load its status. Try reopening this popup.';
});
