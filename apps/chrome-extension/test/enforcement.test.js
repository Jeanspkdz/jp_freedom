import assert from 'node:assert/strict';
import test from 'node:test';
import { applySnapshotRules } from '../src/enforcement.js';

test('active desktop blocks use DNR block rules even without Facebook host permission', async () => {
  const updates = [];
  const chromeApi = {
    declarativeNetRequest: {
      getDynamicRules: async () => [{ id: 1 }, { id: 8 }],
      updateDynamicRules: async update => updates.push(update),
    },
    permissions: {
      contains: async () => false,
    },
  };
  const snapshot = {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['facebook.com'],
    blocks: [{
      id: 'focus',
      date: '2026-11-08',
      name: 'Study',
      startTime: '19:30',
      endTime: '21:00',
      completionStatus: 'NOT_STARTED',
    }],
  };

  await applySnapshotRules(chromeApi, snapshot, new Date(2026, 10, 8, 20, 0));

  assert.equal(updates.length, 1);
  assert.deepEqual(updates[0].removeRuleIds, [1, 8]);
  assert.deepEqual(updates[0].addRules, [{
    id: 1,
    priority: 1,
    action: { type: 'block' },
    condition: { requestDomains: ['facebook.com'], resourceTypes: ['main_frame'] },
  }]);
});
