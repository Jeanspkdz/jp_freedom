import { timingSafeEqual } from 'node:crypto';

export const DEFAULT_SNAPSHOT = Object.freeze({
  version: 1,
  settings: Object.freeze({ requiredBlockCount: 3, eveningCutoff: '21:00' }),
  desktopBlockedDomains: Object.freeze([
    'facebook.com',
    'instagram.com',
    'tiktok.com',
    'x.com',
    'reddit.com',
  ]),
  blocks: Object.freeze([]),
  activeStudySession: null,
  phoneReceivedStudySessionId: null,
  pendingStudyReviewSessionId: null,
  sessionStartCandidates: Object.freeze([]),
  studyHistory: Object.freeze([]),
});

const accessProfiles = new Set([
  'ESSENTIALS_ONLY',
  'ESSENTIALS_WITH_CHATGPT',
  'ESSENTIALS_WITH_CHATGPT_AND_SEARCH',
]);
const packageNamePattern = /^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/;
const completionStatuses = new Set(['NOT_STARTED', 'FINISHED', 'PARTLY_DONE', 'NOT_DONE']);
const studySessionStates = new Set(['ACTIVE', 'PENDING_REVIEW', 'BREAKING', 'REVIEWED']);
const supportedCutoffs = new Set(['21:00', '22:00', '23:00']);
const domainPattern = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;
const blockIdPattern = /^[A-Za-z0-9_-]{1,80}$/;
const packagePattern = /^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+$/;
function validProfileSelection(session) {
  return (session.profileId == null || (typeof session.profileId === 'string' && session.profileId.length <= 80))
    && (session.profileName === undefined || (typeof session.profileName === 'string' && session.profileName.length <= 200))
    && (session.allowedPackages === undefined || (Array.isArray(session.allowedPackages) && session.allowedPackages.length <= 500
      && session.allowedPackages.every(value => typeof value === 'string' && packagePattern.test(value))));
}
const corsHeaders = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET, PUT, OPTIONS',
  'access-control-allow-headers': 'authorization, content-type',
};

function json(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      ...corsHeaders,
      ...extraHeaders,
    },
  });
}

function isAuthorized(request, configuredToken) {
  const header = request.headers.get('authorization') ?? '';
  const supplied = header.startsWith('Bearer ') ? header.slice(7) : '';
  const expectedBytes = Buffer.from(configuredToken ?? '');
  const suppliedBytes = Buffer.from(supplied);
  return expectedBytes.length > 0
    && suppliedBytes.length === expectedBytes.length
    && timingSafeEqual(suppliedBytes, expectedBytes);
}

function isCalendarDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00.000Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function minutes(value) {
  if (typeof value !== 'string' || !/^([01]\d|2[0-3]):[0-5]\d$/.test(value)) return null;
  const [hour, minute] = value.split(':').map(Number);
  return hour * 60 + minute;
}

export function validateSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot) || snapshot.version !== 1) {
    return 'invalid_snapshot';
  }
  const { settings, desktopBlockedDomains, blocks } = snapshot;
  if (!settings || !Number.isInteger(settings.requiredBlockCount)
    || settings.requiredBlockCount < 1 || settings.requiredBlockCount > 100
    || !supportedCutoffs.has(settings.eveningCutoff)) return 'invalid_settings';
  if (!Array.isArray(desktopBlockedDomains) || desktopBlockedDomains.length > 100
    || desktopBlockedDomains.some(domain => typeof domain !== 'string' || !domainPattern.test(domain))
    || new Set(desktopBlockedDomains).size !== desktopBlockedDomains.length) return 'invalid_blocklist';
  if (!Array.isArray(blocks) || blocks.length > 1000) return 'invalid_blocks';
  const session = snapshot.activeStudySession;
  if (session !== undefined && session !== null) {
    const startedAt = typeof session.startedAt === 'string' ? Date.parse(session.startedAt) : NaN;
    const endsAt = typeof session.endsAt === 'string' ? Date.parse(session.endsAt) : NaN;
    if (!session || typeof session !== 'object' || Array.isArray(session)
      || typeof session.id !== 'string' || session.id.length === 0 || session.id.length > 80
      || typeof session.taskName !== 'string' || session.taskName.length > 200
      || !accessProfiles.has(session.accessProfile)
      || !validProfileSelection(session)
      || !Number.isFinite(startedAt) || !Number.isFinite(endsAt) || startedAt >= endsAt) {
      return 'invalid_study_session';
    }
  }
  const sessionStartCandidates = snapshot.sessionStartCandidates ?? [];
  if (!Array.isArray(sessionStartCandidates) || sessionStartCandidates.length > 2
    || sessionStartCandidates.some(candidate => {
      const startedAt = typeof candidate?.startedAt === 'string' ? Date.parse(candidate.startedAt) : NaN;
      const endsAt = typeof candidate?.endsAt === 'string' ? Date.parse(candidate.endsAt) : NaN;
      return !candidate || typeof candidate !== 'object' || Array.isArray(candidate)
        || typeof candidate.id !== 'string' || candidate.id.length === 0 || candidate.id.length > 80
        || typeof candidate.taskName !== 'string' || candidate.taskName.length > 200
        || !accessProfiles.has(candidate.accessProfile)
        || !validProfileSelection(candidate)
        || !Number.isFinite(startedAt) || !Number.isFinite(endsAt) || startedAt >= endsAt;
    }) || new Set(sessionStartCandidates.map(candidate => candidate.id)).size !== sessionStartCandidates.length) {
    return 'invalid_session_start_candidates';
  }
  const fieldConflicts = snapshot.fieldConflicts ?? [];
  if (!Array.isArray(fieldConflicts) || fieldConflicts.length > 1000
    || fieldConflicts.some(conflict => !conflict || typeof conflict !== 'object' || Array.isArray(conflict)
      || typeof conflict.path !== 'string' || conflict.path.length === 0 || conflict.path.length > 240
      || typeof conflict.remoteValue !== 'string' || conflict.remoteValue.length > 2000
      || typeof conflict.localValue !== 'string' || conflict.localValue.length > 2000)
    || new Set(fieldConflicts.map(conflict => conflict.path)).size !== fieldConflicts.length) {
    return 'invalid_field_conflicts';
  }
  const receivedSessionId = snapshot.phoneReceivedStudySessionId;
  if (receivedSessionId !== undefined && receivedSessionId !== null
    && (typeof receivedSessionId !== 'string' || receivedSessionId.length === 0 || receivedSessionId.length > 80)) {
    return 'invalid_study_session_receipt';
  }
  const pendingReviewId = snapshot.pendingStudyReviewSessionId;
  if (pendingReviewId !== undefined && pendingReviewId !== null
    && (typeof pendingReviewId !== 'string' || pendingReviewId.length === 0 || pendingReviewId.length > 80)) {
    return 'invalid_pending_study_review';
  }

  const studyHistory = snapshot.studyHistory ?? [];
  if (!Array.isArray(studyHistory) || studyHistory.length > 10000) return 'invalid_study_history';
  const historyIds = new Set();
  for (const entry of studyHistory) {
    const startedAt = typeof entry?.startedAt === 'string' ? Date.parse(entry.startedAt) : NaN;
    const endedAt = entry?.endedAt == null ? null : Date.parse(entry.endedAt);
    const breakStartedAt = entry?.breakStartedAt == null ? null : Date.parse(entry.breakStartedAt);
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)
      || typeof entry.id !== 'string' || entry.id.length === 0 || entry.id.length > 80
      || historyIds.has(entry.id)
      || typeof entry.taskName !== 'string' || entry.taskName.length > 200
      || !Number.isSafeInteger(entry.durationMillis) || entry.durationMillis < 0
      || !accessProfiles.has(entry.accessProfile)
      || !validProfileSelection(entry)
      || !Number.isFinite(startedAt)
      || (endedAt !== null && (!Number.isFinite(endedAt) || endedAt < startedAt))
      || !Number.isSafeInteger(entry.actualMillis) || entry.actualMillis < 0
      || !studySessionStates.has(entry.state)
      || (entry.completionStatus !== null && entry.completionStatus !== undefined && !completionStatuses.has(entry.completionStatus))
      || typeof (entry.note ?? '') !== 'string' || (entry.note ?? '').length > 2000
      || typeof (entry.remaining ?? '') !== 'string' || (entry.remaining ?? '').length > 2000
      || (breakStartedAt !== null && !Number.isFinite(breakStartedAt))
      || !Number.isSafeInteger(entry.breakDurationMillis ?? 0) || (entry.breakDurationMillis ?? 0) < 0
      || typeof (entry.nextTask ?? '') !== 'string' || (entry.nextTask ?? '').length > 200) {
      return 'invalid_study_history';
    }
    historyIds.add(entry.id);
  }

  const ids = new Set();
  const byDate = new Map();
  for (const block of blocks) {
    if (!block || typeof block !== 'object' || !blockIdPattern.test(block.id ?? '')
      || ids.has(block.id) || !isCalendarDate(block.date)
      || typeof block.name !== 'string' || block.name.trim().length === 0 || block.name.length > 200
      || minutes(block.startTime) === null || minutes(block.endTime) === null
      || minutes(block.startTime) >= minutes(block.endTime)
      || (block.accessProfile !== undefined && !accessProfiles.has(block.accessProfile))
      || !completionStatuses.has(block.completionStatus)
      || (block.note !== undefined && (typeof block.note !== 'string' || block.note.length > 2000))) {
      return 'invalid_blocks';
    }
    ids.add(block.id);
    const day = byDate.get(block.date) ?? [];
    day.push(block);
    byDate.set(block.date, day);
  }

  for (const sameDayBlocks of byDate.values()) {
    sameDayBlocks.sort((a, b) => minutes(a.startTime) - minutes(b.startTime));
    for (let index = 1; index < sameDayBlocks.length; index += 1) {
      if (minutes(sameDayBlocks[index].startTime) < minutes(sameDayBlocks[index - 1].endTime)) {
        return 'overlapping_blocks';
      }
    }
  }
  return null;
}

// A session is versioned separately so timer writes cannot replace plan edits.
function validateSession(session) {
  if (!session || typeof session !== 'object' || Array.isArray(session)) return false;
  if (typeof session.id !== 'string' || !blockIdPattern.test(session.id)) return false;
  if (!Number.isFinite(Date.parse(session.startedAt)) || !Number.isInteger(session.durationMillis)
    || session.durationMillis < 60_000 || session.durationMillis > 24 * 60 * 60_000) return false;
  const hasLegacyProfile = accessProfiles.has(session.accessProfile);
  const hasAllowedAppsList = typeof session.allowedAppsListId === 'string'
    && session.allowedAppsListId.length > 0 && session.allowedAppsListId.length <= 80
    && typeof session.allowedAppsListName === 'string'
    && session.allowedAppsListName.trim().length > 0 && session.allowedAppsListName.length <= 40
    && Array.isArray(session.allowedAppPackages) && session.allowedAppPackages.length <= 500
    && session.allowedAppPackages.every(name => typeof name === 'string'
      && name.length <= 160 && packageNamePattern.test(name))
    && new Set(session.allowedAppPackages).size === session.allowedAppPackages.length;
  if (!['ACTIVE', 'PENDING_REVIEW', 'BREAKING', 'REVIEWED'].includes(session.state)
    || (!hasLegacyProfile && !hasAllowedAppsList) || typeof session.taskName !== 'string'
    || session.taskName.length > 200 || (session.note !== undefined && (typeof session.note !== 'string' || session.note.length > 2000))
    || (session.actualMillis !== undefined && (!Number.isInteger(session.actualMillis)
      || session.actualMillis < 0 || session.actualMillis > session.durationMillis))) return false;
  if (session.supersededAttemptIds !== undefined
    && (!Array.isArray(session.supersededAttemptIds) || session.supersededAttemptIds.length > 10
      || session.supersededAttemptIds.some(id => !blockIdPattern.test(id)))) return false;
  if (session.outcome !== undefined && !['FINISHED', 'NOT_FINISHED'].includes(session.outcome)) return false;
  return session.endedAt == null || Number.isFinite(Date.parse(session.endedAt));
}

export function createSyncApi({ store, token }) {
  return async function handle(request) {
    const url = new URL(request.url);
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders });
    }
    if (url.pathname === '/health' && request.method === 'GET') return json({ ok: true });
    if (!['/v1/snapshot', '/v1/session', '/v1/history', '/v1/history/resolve'].includes(url.pathname)) return json({ error: 'not_found' }, 404);
    if (!isAuthorized(request, token)) return json({ error: 'unauthorized' }, 401);

    if (url.pathname === '/v1/history/resolve') {
      if (request.method !== 'PUT') return json({ error: 'method_not_allowed' }, 405);
      let body;
      try { body = await request.json(); } catch { return json({ error: 'invalid_json' }, 400); }
      if (!validateSession(body?.session) || body.session.state !== 'REVIEWED') return json({ error: 'invalid_review' }, 422);
      try { return json({ session: await store.resolveHistory(body.session) }); }
      catch { return json({ error: 'storage_unavailable' }, 503); }
    }

    if (url.pathname === '/v1/history') {
      try {
        if (request.method === 'GET') return json({ sessions: await store.readHistory() });
        if (request.method !== 'PUT') return json({ error: 'method_not_allowed' }, 405);
        let body;
        try { body = await request.json(); } catch { return json({ error: 'invalid_json' }, 400); }
        if (!Array.isArray(body?.sessions) || body.sessions.length > 1000
          || body.sessions.some(session => !validateSession(session))
          || new Set(body.sessions.map(session => session.id)).size !== body.sessions.length) {
          return json({ error: 'invalid_history' }, 422);
        }
        return json({ sessions: await store.importHistory(body.sessions) });
      } catch { return json({ error: 'storage_unavailable' }, 503); }
    }

    if (url.pathname === '/v1/session') {
      try {
        if (request.method === 'GET') return json(await store.readSession());
        if (request.method !== 'PUT') return json({ error: 'method_not_allowed' }, 405);
        let body;
        try { body = await request.json(); } catch { return json({ error: 'invalid_json' }, 400); }
        if (!Number.isInteger(body?.expectedRevision) || body.expectedRevision < 0) return json({ error: 'invalid_revision' }, 400);
        if (!validateSession(body.session)) return json({ error: 'invalid_session' }, 422);
        const result = await store.replaceSession(body.expectedRevision, body.session);
        return result.conflict
          ? json({ error: 'revision_conflict', currentRevision: result.revision }, 409)
          : json(result);
      } catch { return json({ error: 'storage_unavailable' }, 503); }
    }

    if (request.method === 'GET') {
      try {
        const current = await store.read();
        return json({ ...current, snapshot: { ...current.snapshot, blocks: [] } });
      } catch {
        return json({ error: 'storage_unavailable' }, 503);
      }
    }
    if (request.method !== 'PUT') return json({ error: 'method_not_allowed' }, 405);

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: 'invalid_json' }, 400);
    }
    if (!Number.isInteger(body?.expectedRevision) || body.expectedRevision < 0) {
      return json({ error: 'invalid_revision' }, 400);
    }
    const validationError = validateSnapshot(body.snapshot);
    if (validationError) return json({ error: validationError }, 422);

    try {
      const result = await store.replace(body.expectedRevision, { ...body.snapshot, blocks: [] });
      if (result.conflict) {
        return json({ error: 'revision_conflict', currentRevision: result.revision }, 409);
      }
      return json(result);
    } catch {
      return json({ error: 'storage_unavailable' }, 503);
    }
  };
}
