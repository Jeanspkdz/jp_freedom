import { getPlanningReminderStatus, getRequiredPlanDate } from './policy.js';

export const PLANNING_REMINDER_CUTOFF_ALARM = 'planning-reminder-cutoff';
export const PLANNING_REMINDER_ALARM = 'planning-reminder-repeat';
export const PLANNING_REMINDER_NOTIFICATION = 'planning-reminder';
export const PLANNING_REMINDER_PERIOD_MINUTES = 30;

function localDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function nextCutoff(snapshot, now) {
  const cutoff = snapshot?.settings?.eveningCutoff;
  if (!['21:00', '22:00', '23:00'].includes(cutoff)) return null;
  const [hour, minute] = cutoff.split(':').map(Number);
  for (let offset = 0; offset <= 7; offset += 1) {
    const candidate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset, hour, minute);
    if (candidate <= now || candidate.getDay() === 6) continue;
    return candidate;
  }
  return null;
}

function notificationFor(status, iconUrl) {
  return {
    type: 'basic',
    iconUrl,
    title: 'Incomplete day plan',
    message: `Your plan for ${status.planDate} has ${status.plannedCount} of ${status.requiredBlockCount} required blocks. Open Tally on your phone to finish planning.`,
  };
}

function cutoffPassed(snapshot, now) {
  const cutoff = snapshot?.settings?.eveningCutoff;
  if (!['21:00', '22:00', '23:00'].includes(cutoff)) return false;
  const [hour, minute] = cutoff.split(':').map(Number);
  return now.getHours() * 60 + now.getMinutes() >= hour * 60 + minute;
}

export function createPlanningReminderScheduler({
  getSnapshot,
  getReminderDate = async () => null,
  setReminderDate = async () => {},
  alarms,
  notifications,
  iconUrl,
  now = () => new Date(),
}) {
  async function showReminder(status) {
    await notifications.create(PLANNING_REMINDER_NOTIFICATION, notificationFor(status, iconUrl));
  }

  async function reconcile({ repeatAlarmFired = false } = {}) {
    const currentTime = now();
    const snapshot = await getSnapshot();
    let reminderDate = await getReminderDate();
    const shouldSelectNextPlan = cutoffPassed(snapshot, currentTime)
      && (!reminderDate || reminderDate <= localDateKey(currentTime));
    let targetChanged = false;

    if (shouldSelectNextPlan) {
      const nextPlanDate = getRequiredPlanDate(currentTime);
      if (reminderDate !== nextPlanDate) {
        reminderDate = nextPlanDate;
        targetChanged = true;
        await setReminderDate(reminderDate);
      }
    } else if (reminderDate && reminderDate < localDateKey(currentTime)) {
      reminderDate = null;
      targetChanged = true;
      await setReminderDate(null);
    }

    const status = reminderDate
      ? getPlanningReminderStatus(snapshot, currentTime, reminderDate)
      : null;

    await alarms.clear(PLANNING_REMINDER_CUTOFF_ALARM);
    const next = nextCutoff(snapshot, currentTime);
    if (next) await alarms.create(PLANNING_REMINDER_CUTOFF_ALARM, { when: next.getTime() });

    if (!status) {
      await alarms.clear(PLANNING_REMINDER_ALARM);
      await notifications.clear(PLANNING_REMINDER_NOTIFICATION);
      return;
    }

    const repeatAlarm = await alarms.get(PLANNING_REMINDER_ALARM);
    if (!repeatAlarm) {
      await showReminder(status);
      await alarms.create(PLANNING_REMINDER_ALARM, {
        delayInMinutes: PLANNING_REMINDER_PERIOD_MINUTES,
        periodInMinutes: PLANNING_REMINDER_PERIOD_MINUTES,
      });
    } else if (repeatAlarmFired || targetChanged) {
      await showReminder(status);
    }
  }

  async function onAlarm(name) {
    if (name === PLANNING_REMINDER_ALARM) await reconcile({ repeatAlarmFired: true });
    else if (name === PLANNING_REMINDER_CUTOFF_ALARM) await reconcile();
  }

  return { reconcile, onAlarm };
}
