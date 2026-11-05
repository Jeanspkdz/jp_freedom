import assert from 'node:assert/strict';
import test from 'node:test';
import { getCurrentBlockMessage } from '../src/view-model.js';

test('an empty server snapshot at revision zero is shown as an available schedule', () => {
  assert.equal(getCurrentBlockMessage({ configured: true, current: null, next: null, revision: 0 }),
    'No active focus block. Your configured sites are available.');
});

test('an active block with missing site access points to the permission recovery action', () => {
  assert.equal(getCurrentBlockMessage({
    configured: true,
    current: { name: 'Study' },
    next: null,
    revision: 1,
    missingDomains: ['facebook.com'],
  }), 'Study is active. Grant site access to enforce the synced site blocklist.');
});
