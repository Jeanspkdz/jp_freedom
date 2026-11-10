function localDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function minuteOfDay(value) {
  const [hours, minutes] = value.split(':').map(Number);
  return hours * 60 + minutes;
}

function nowMinute(date) {
  return date.getHours() * 60 + date.getMinutes();
}

function activeOnDate(snapshot, date, minute) {
  const dateKey = localDateKey(date);
  return snapshot.blocks.find(block => block.date === dateKey
    && block.completionStatus !== 'FINISHED'
    && minuteOfDay(block.startTime) <= minute
    && minute < minuteOfDay(block.endTime)) ?? null;
}

export function getActiveBlock(snapshot, now = new Date()) {
  if (!snapshot) return null;
  const block = activeOnDate(snapshot, now, nowMinute(now));
  return block?.id ?? null;
}

export function getNextBlock(snapshot, now = new Date()) {
  if (!snapshot) return null;
  const dateKey = localDateKey(now);
  const currentMinute = nowMinute(now);
  return snapshot.blocks
    .filter(block => block.date === dateKey
      && block.completionStatus !== 'FINISHED'
      && minuteOfDay(block.startTime) > currentMinute)
    .sort((first, second) => minuteOfDay(first.startTime) - minuteOfDay(second.startTime))[0] ?? null;
}

export function isEveningPlanIncomplete(snapshot, now = new Date()) {
  if (!snapshot || !snapshot.settings) return false;
  if (now.getDay() === 6) return false;
  if (nowMinute(now) < minuteOfDay(snapshot.settings.eveningCutoff)) return false;

  const requiredDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const requiredDateKey = localDateKey(requiredDate);
  const plannedCount = snapshot.blocks.filter(block => block.date === requiredDateKey).length;
  return plannedCount < snapshot.settings.requiredBlockCount;
}

export function getRequiredPlanDate(eveningDate) {
  if (eveningDate.getDay() === 6) return null;
  return localDateKey(new Date(
    eveningDate.getFullYear(),
    eveningDate.getMonth(),
    eveningDate.getDate() + 1,
  ));
}

export function getPlanningReminderStatus(snapshot, now = new Date(), requiredPlanDate = null) {
  const settings = snapshot?.settings;
  if (!Array.isArray(snapshot?.blocks)
    || !Number.isInteger(settings?.requiredBlockCount)
    || settings.requiredBlockCount < 1
    || !['21:00', '22:00', '23:00'].includes(settings.eveningCutoff)) return null;

  let planDate = requiredPlanDate;
  if (!planDate) {
    const [hours, minutes] = settings.eveningCutoff.split(':').map(Number);
    if (now.getHours() * 60 + now.getMinutes() < hours * 60 + minutes) return null;
    planDate = getRequiredPlanDate(now);
  }
  if (!planDate) return null;

  const plannedCount = snapshot.blocks.filter(block => block.date === planDate).length;
  if (plannedCount >= settings.requiredBlockCount) return null;
  return { planDate, plannedCount, requiredBlockCount: settings.requiredBlockCount };
}

export function makeDesktopBlockRules(snapshot, activeBlockId) {
  if (!snapshot || !activeBlockId) return [];
  const domains = [...new Set(snapshot.desktopBlockedDomains ?? [])];
  return domains.map((domain, index) => ({
    id: index + 1,
    priority: 1,
    action: { type: 'block' },
    condition: {
      requestDomains: [domain],
      resourceTypes: ['main_frame'],
    },
  }));
}

export function getCurrentAndNext(snapshot, now = new Date()) {
  if (!snapshot) return { current: null, next: null };
  const current = activeOnDate(snapshot, now, nowMinute(now));
  const next = getNextBlock(snapshot, now);
  return { current, next };
}
