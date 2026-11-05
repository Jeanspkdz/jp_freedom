import assert from 'node:assert/strict';
import test from 'node:test';
import { getPlanningReminderStatus } from '../src/policy.js';

const snapshot = {
  settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
  blocks: [],
};

test('the reminder becomes due at each configured evening cutoff', () => {
  for (const eveningCutoff of ['21:00', '22:00', '23:00']) {
    const [hour, minute] = eveningCutoff.split(':').map(Number);
    const configuredSnapshot = {
      ...snapshot,
      settings: { ...snapshot.settings, eveningCutoff },
    };

    assert.equal(
      getPlanningReminderStatus(configuredSnapshot, new Date(2026, 10, 5, hour, minute - 1)),
      null,
    );
    assert.deepEqual(
      getPlanningReminderStatus(configuredSnapshot, new Date(2026, 10, 5, hour, minute)),
      {
        planDate: '2026-11-06',
        plannedCount: 0,
        requiredBlockCount: 3,
      },
    );
  }
});

test('a next day plan with the configured minimum does not qualify for a reminder', () => {
  const sufficientPlan = {
    ...snapshot,
    settings: { ...snapshot.settings, requiredBlockCount: 2 },
    blocks: [
      { date: '2026-11-06', completionStatus: 'FINISHED' },
      { date: '2026-11-06', completionStatus: 'NOT_STARTED' },
    ],
  };

  assert.equal(getPlanningReminderStatus(sufficientPlan, new Date(2026, 10, 5, 21, 0)), null);
});

test('Saturday has no required plan and Sunday checks Monday', () => {
  assert.equal(getPlanningReminderStatus(snapshot, new Date(2026, 10, 7, 21, 0)), null);
  assert.deepEqual(getPlanningReminderStatus(snapshot, new Date(2026, 10, 8, 21, 0)), {
    planDate: '2026-11-09',
    plannedCount: 0,
    requiredBlockCount: 3,
  });
});
