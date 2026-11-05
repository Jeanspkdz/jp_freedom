import { timingSafeEqual } from 'node:crypto';

const accessProfiles = new Set([
  'ESSENTIALS_ONLY',
  'ESSENTIALS_WITH_CHATGPT',
  'ESSENTIALS_WITH_CHATGPT_AND_SEARCH',
]);
const completionStatuses = new Set(['NOT_STARTED', 'FINISHED', 'PARTLY_DONE', 'NOT_DONE']);
const supportedCutoffs = new Set(['21:00', '22:00', '23:00']);
const domainPattern = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;
const corsHeaders = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET, PUT, OPTIONS',
  'access-control-allow-headers': 'authorization, content-type',
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      ...corsHeaders,
    },
  });
}

function isAuthorized(request, token) {
  const supplied = request.headers.get('authorization')?.startsWith('Bearer ')
    ? request.headers.get('authorization').slice(7)
    : '';
  const expectedBytes = Buffer.from(token ?? '');
  const suppliedBytes = Buffer.from(supplied);
  return expectedBytes.length > 0
    && expectedBytes.length === suppliedBytes.length
    && timingSafeEqual(expectedBytes, suppliedBytes);
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

function hasValidSettings(snapshot) {
  const settings = snapshot?.settings;
  return settings && typeof settings === 'object' && !Array.isArray(settings)
    && Number.isInteger(settings.requiredBlockCount)
    && settings.requiredBlockCount >= 1
    && settings.requiredBlockCount <= 2_147_483_647
    && supportedCutoffs.has(settings.eveningCutoff);
}

function hasValidBlocklist(snapshot) {
  const domains = snapshot?.desktopBlockedDomains;
  return Array.isArray(domains) && domains.length <= 100
    && domains.every(domain => typeof domain === 'string' && domainPattern.test(domain))
    && new Set(domains).size === domains.length;
}

function validateBlocks(snapshot) {
  if (!Array.isArray(snapshot?.blocks) || snapshot.blocks.length > 1000) return 'invalid_blocks';

  const ids = new Set();
  const byDate = new Map();
  for (const block of snapshot.blocks) {
    if (!block || typeof block !== 'object' || Array.isArray(block)
      || typeof block.id !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(block.id)
      || ids.has(block.id)
      || !isCalendarDate(block.date)
      || typeof block.name !== 'string' || block.name.trim().length === 0 || block.name.length > 200
      || (block.note !== undefined && (typeof block.note !== 'string' || block.note.length > 2000))) {
      return 'invalid_blocks';
    }
    const start = minutes(block.startTime);
    const end = minutes(block.endTime);
    if (start === null || end === null || start >= end
      || !accessProfiles.has(block.accessProfile)
      || !completionStatuses.has(block.completionStatus)) return 'invalid_blocks';

    ids.add(block.id);
    const sameDayBlocks = byDate.get(block.date) ?? [];
    sameDayBlocks.push({ start, end });
    byDate.set(block.date, sameDayBlocks);
  }

  for (const sameDayBlocks of byDate.values()) {
    sameDayBlocks.sort((a, b) => a.start - b.start);
    for (let index = 1; index < sameDayBlocks.length; index += 1) {
      if (sameDayBlocks[index].start < sameDayBlocks[index - 1].end) return 'overlapping_blocks';
    }
  }
  return null;
}

export function createSyncApi({ store, readToken, writeToken }) {
  if (readToken && writeToken && readToken === writeToken) {
    throw new Error('Read and write tokens must be different.');
  }

  return async function handle(request) {
    const url = new URL(request.url);
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: corsHeaders });
    if (url.pathname === '/health' && request.method === 'GET') return json({ ok: true });
    if (url.pathname !== '/v1/snapshot') return json({ error: 'not_found' }, 404);

    if (request.method === 'GET') {
      if (!isAuthorized(request, readToken) && !isAuthorized(request, writeToken)) {
        return json({ error: 'unauthorized' }, 401);
      }
      try {
        return json(await store.read());
      } catch {
        return json({ error: 'storage_unavailable' }, 503);
      }
    }

    if (request.method !== 'PUT') return json({ error: 'method_not_allowed' }, 405);
    if (!isAuthorized(request, writeToken)) {
      if (isAuthorized(request, readToken)) return json({ error: 'read_only' }, 403);
      return json({ error: 'unauthorized' }, 401);
    }
    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: 'invalid_json' }, 400);
    }
    if (!Number.isInteger(body?.expectedRevision) || body.expectedRevision < 0) {
      return json({ error: 'invalid_revision' }, 400);
    }
    if (!body.snapshot || typeof body.snapshot !== 'object'
      || Array.isArray(body.snapshot) || body.snapshot.version !== 1) {
      return json({ error: 'invalid_snapshot' }, 422);
    }
    if (!hasValidSettings(body.snapshot)) return json({ error: 'invalid_settings' }, 422);
    if (!hasValidBlocklist(body.snapshot)) return json({ error: 'invalid_blocklist' }, 422);
    const blockError = validateBlocks(body.snapshot);
    if (blockError) return json({ error: blockError }, 422);

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
