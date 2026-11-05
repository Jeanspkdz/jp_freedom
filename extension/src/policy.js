const DNR_RULE_ID_START = 1000;
const domainPattern = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;

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

function localTimestamp(date, time) {
  const [year, month, day] = date.split('-').map(Number);
  const [hours, minutes] = time.split(':').map(Number);
  return new Date(year, month - 1, day, hours, minutes).getTime();
}

function activeOnDate(snapshot, date, minute) {
  const dateKey = localDateKey(date);
  return snapshot?.blocks?.find(block => block.date === dateKey
    && block.completionStatus !== 'FINISHED'
    && minuteOfDay(block.startTime) <= minute
    && minute < minuteOfDay(block.endTime)) ?? null;
}

export function getActiveBlock(snapshot, now = new Date()) {
  return activeOnDate(snapshot, now, now.getHours() * 60 + now.getMinutes())?.id ?? null;
}

export function getNextBlock(snapshot, now = new Date()) {
  if (!snapshot?.blocks) return null;
  const nowTime = now.getTime();
  return snapshot.blocks
    .filter(block => block.completionStatus !== 'FINISHED'
      && localTimestamp(block.date, block.startTime) > nowTime)
    .sort((first, second) => localTimestamp(first.date, first.startTime) - localTimestamp(second.date, second.startTime))[0] ?? null;
}

export function getNextBoundary(snapshot, now = new Date()) {
  if (!snapshot?.blocks) return null;
  const nowTime = now.getTime();
  const candidates = snapshot.blocks
    .filter(block => block.completionStatus !== 'FINISHED')
    .flatMap(block => [block.startTime, block.endTime].map(time => localTimestamp(block.date, time)))
    .filter(time => time > nowTime)
    .sort((first, second) => first - second);
  return candidates.length > 0 ? new Date(candidates[0]) : null;
}

export function getDomainPermissionOrigins(domains) {
  if (!Array.isArray(domains)) return [];
  return [...new Set(domains.filter(domain => typeof domain === 'string' && domainPattern.test(domain)))]
    .flatMap(domain => [
      `http://${domain}/*`,
      `http://*.${domain}/*`,
      `https://${domain}/*`,
      `https://*.${domain}/*`,
    ]);
}

export function makeDesktopBlockRules(snapshot, activeBlockId, allowedDomains = snapshot?.desktopBlockedDomains) {
  if (!snapshot || !activeBlockId || !Array.isArray(allowedDomains)) return [];
  const domains = [...new Set(allowedDomains.filter(domain => snapshot.desktopBlockedDomains?.includes(domain)))];
  return domains.map((domain, index) => ({
    id: DNR_RULE_ID_START + index,
    priority: 1,
    action: { type: 'redirect', redirect: { extensionPath: '/blocked.html' } },
    condition: {
      requestDomains: [domain],
      resourceTypes: ['main_frame'],
    },
  }));
}
