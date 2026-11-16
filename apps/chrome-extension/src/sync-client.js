import { isValidDomain } from './domain.js';

const ACCESS_PROFILES = ['ESSENTIALS_ONLY', 'ESSENTIALS_WITH_CHATGPT', 'ESSENTIALS_WITH_CHATGPT_AND_SEARCH'];
const COMPLETION_STATUSES = ['NOT_STARTED', 'PARTLY_DONE', 'NOT_DONE', 'FINISHED'];

function validDateKey(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? '')) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

function validTime(value) {
  const match = /^(\d{2}):(\d{2})$/.exec(value ?? '');
  return Boolean(match && Number(match[1]) < 24 && Number(match[2]) < 60);
}

function validActiveStudySession(session) {
  if (session === null || session === undefined) return true;
  if (!session || typeof session !== 'object' || Array.isArray(session)
    || typeof session.id !== 'string' || session.id.length === 0
    || typeof session.taskName !== 'string'
    || !ACCESS_PROFILES.includes(session.accessProfile)) return false;
  const startedAt = Date.parse(session.startedAt);
  const endsAt = Date.parse(session.endsAt);
  return typeof session.startedAt === 'string' && typeof session.endsAt === 'string'
    && Number.isFinite(startedAt) && Number.isFinite(endsAt) && startedAt < endsAt;
}

function validSessionStartCandidates(candidates) {
  if (candidates === undefined) return true;
  return Array.isArray(candidates) && candidates.length <= 2
    && candidates.every(candidate => candidate && validActiveStudySession(candidate))
    && new Set(candidates.map(candidate => candidate.id)).size === candidates.length;
}

function validFieldConflicts(conflicts) {
  return conflicts === undefined || (Array.isArray(conflicts) && conflicts.length <= 1000
    && conflicts.every(conflict => conflict && typeof conflict === 'object'
      && typeof conflict.path === 'string' && conflict.path.length > 0 && conflict.path.length <= 240
      && typeof conflict.remoteValue === 'string' && conflict.remoteValue.length <= 2000
      && typeof conflict.localValue === 'string' && conflict.localValue.length <= 2000)
    && new Set(conflicts.map(conflict => conflict.path)).size === conflicts.length);
}

function validSnapshot(snapshot) {
  return snapshot?.version === 1
    && Number.isInteger(snapshot.settings?.requiredBlockCount)
    && snapshot.settings.requiredBlockCount > 0
    && ['21:00', '22:00', '23:00'].includes(snapshot.settings?.eveningCutoff)
    && Array.isArray(snapshot.desktopBlockedDomains)
    && snapshot.desktopBlockedDomains.length <= 100
    && snapshot.desktopBlockedDomains.every(isValidDomain)
    && validActiveStudySession(snapshot.activeStudySession)
    && validSessionStartCandidates(snapshot.sessionStartCandidates)
    && validFieldConflicts(snapshot.fieldConflicts)
    && [snapshot.phoneReceivedStudySessionId, snapshot.pendingStudyReviewSessionId].every(value =>
      value === undefined || value === null || (typeof value === 'string' && value.length > 0 && value.length <= 80))
    && Array.isArray(snapshot.blocks)
    && snapshot.blocks.length <= 1000
    && snapshot.blocks.every(block => typeof block.id === 'string'
      && block.id.length > 0
      && validDateKey(block.date)
      && typeof block.name === 'string'
      && validTime(block.startTime)
      && validTime(block.endTime)
      && ACCESS_PROFILES.includes(block.accessProfile)
      && COMPLETION_STATUSES.includes(block.completionStatus));
}

export function validSnapshotResponse(payload) {
  return Number.isInteger(payload?.revision)
    && payload.revision >= 0
    && (payload.updatedAt === null || typeof payload.updatedAt === 'string')
    && validSnapshot(payload.snapshot);
}

export function snapshotEndpoint(apiUrl) {
  let url;
  try {
    url = new URL(apiUrl);
  } catch {
    throw new Error('Enter a valid sync API URL.');
  }
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Use an HTTP or HTTPS sync API URL.');
  const localHttp = url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !localHttp) throw new Error('Use HTTPS for the sync API. HTTP is allowed for localhost development.');
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('Remove URL credentials, query parameters, and fragments; enter the bearer token separately.');
  }
  const base = url.href.replace(/\/+$/, '');
  return base.endsWith('/v1/snapshot') ? base : `${base}/v1/snapshot`;
}

export async function readSnapshot(apiUrl, apiToken, fetchImpl = globalThis.fetch) {
  const response = await fetchImpl(snapshotEndpoint(apiUrl), {
    method: 'GET',
    headers: { Accept: 'application/json', Authorization: `Bearer ${apiToken}` },
    cache: 'no-store', credentials: 'omit', redirect: 'error',
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) throw new Error('Sync rejected the token. Check the bearer token and try again.');
    if (response.status === 404) throw new Error('Sync API not found. Check the API base URL.');
    throw new Error(`Sync failed with HTTP ${response.status}. Check the API and try again.`);
  }
  const payload = await response.json();
  if (!validSnapshotResponse(payload)) throw new Error('The sync API returned an unsupported or incomplete plan. Check the API version.');
  return payload;
}
