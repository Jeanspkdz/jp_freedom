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
});

const accessProfiles = new Set([
  'ESSENTIALS_ONLY',
  'ESSENTIALS_WITH_CHATGPT',
  'ESSENTIALS_WITH_CHATGPT_AND_SEARCH',
]);
const completionStatuses = new Set(['NOT_STARTED', 'FINISHED', 'PARTLY_DONE', 'NOT_DONE']);
const supportedCutoffs = new Set(['21:00', '22:00', '23:00']);
const domainPattern = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;
const blockIdPattern = /^[A-Za-z0-9_-]{1,80}$/;
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

  const ids = new Set();
  const byDate = new Map();
  for (const block of blocks) {
    if (!block || typeof block !== 'object' || !blockIdPattern.test(block.id ?? '')
      || ids.has(block.id) || !isCalendarDate(block.date)
      || typeof block.name !== 'string' || block.name.trim().length === 0 || block.name.length > 200
      || minutes(block.startTime) === null || minutes(block.endTime) === null
      || minutes(block.startTime) >= minutes(block.endTime)
      || !accessProfiles.has(block.accessProfile)
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

export function createSyncApi({ store, token }) {
  return async function handle(request) {
    const url = new URL(request.url);
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders });
    }
    if (url.pathname === '/health' && request.method === 'GET') return json({ ok: true });
    if (url.pathname !== '/v1/snapshot') return json({ error: 'not_found' }, 404);
    if (!isAuthorized(request, token)) return json({ error: 'unauthorized' }, 401);

    if (request.method === 'GET') {
      try {
        return json(await store.read());
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
      const result = await store.replace(body.expectedRevision, body.snapshot);
      if (result.conflict) {
        return json({ error: 'revision_conflict', currentRevision: result.revision }, 409);
      }
      return json(result);
    } catch {
      return json({ error: 'storage_unavailable' }, 503);
    }
  };
}
