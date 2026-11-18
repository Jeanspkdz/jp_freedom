import { getActiveStudySession } from './policy.js';

export const PLANNING_REMINDER_CUTOFF_ALARM = 'study-reminder-time';
export const PLANNING_REMINDER_ALARM = 'study-reminder-repeat';
export const PLANNING_REMINDER_NOTIFICATION = 'study-reminder';
export const PLANNING_REMINDER_PERIOD_MINUTES = 30;

function localDateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function studiedToday(snapshot, now) {
  const today = localDateKey(now);
  const startedToday = value => {
    const startedAt = Date.parse(value);
    return Number.isFinite(startedAt) && localDateKey(new Date(startedAt)) === today;
  };
  return Boolean(getActiveStudySession(snapshot, now)
    || startedToday(snapshot?.activeStudySession?.startedAt)
    || snapshot?.studyHistory?.some(session => startedToday(session.startedAt)));
}

export function createPlanningReminderScheduler({ getSnapshot, alarms, notifications, iconUrl, now = () => new Date() }) {
  async function reconcile({ repeatAlarmFired = false } = {}) {
    const currentTime = now();
    const snapshot = await getSnapshot();
    const next = new Date(currentTime.getFullYear(), currentTime.getMonth(), currentTime.getDate(), 21);
    if (next <= currentTime) next.setDate(next.getDate() + 1);
    await alarms.clear(PLANNING_REMINDER_CUTOFF_ALARM);
    await alarms.create(PLANNING_REMINDER_CUTOFF_ALARM, { when: next.getTime() });
    if (!snapshot || currentTime.getHours() < 21 || studiedToday(snapshot, currentTime)) {
      await alarms.clear(PLANNING_REMINDER_ALARM);
      await notifications.clear(PLANNING_REMINDER_NOTIFICATION);
      return;
    }
    const repeat = await alarms.get(PLANNING_REMINDER_ALARM);
    if (!repeat || repeatAlarmFired) {
      await notifications.create(PLANNING_REMINDER_NOTIFICATION, {
        type: 'basic', iconUrl, title: 'Time for a study session',
        message: 'Start a focus session in Tally when you are ready.',
      });
    }
    if (!repeat) await alarms.create(PLANNING_REMINDER_ALARM, {
      delayInMinutes: PLANNING_REMINDER_PERIOD_MINUTES,
      periodInMinutes: PLANNING_REMINDER_PERIOD_MINUTES,
    });
  }
  async function onAlarm(name) {
    if (name === PLANNING_REMINDER_ALARM) await reconcile({ repeatAlarmFired: true });
    else if (name === PLANNING_REMINDER_CUTOFF_ALARM) await reconcile();
  }
  return { reconcile, onAlarm };
}
