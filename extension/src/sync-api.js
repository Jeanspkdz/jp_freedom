const domainPattern = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;
const completionStatuses = new Set(['NOT_STARTED', 'FINISHED', 'PARTLY_DONE', 'NOT_DONE']);

export class SyncApiError extends Error {
  constructor(message, code = 'sync_failed') {
    super(message);
    this.name = 'SyncApiError';
    this.code = code;
  }
}

export function normalizeEndpoint(value) {
  let url;
  try {
    url = new URL(value.trim());
  } catch {
    throw new SyncApiError('Enter a valid sync API URL.', 'invalid_endpoint');
  }

  const localHttp = url.protocol === 'http:'
    && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !localHttp) {
    throw new SyncApiError('Use an HTTPS URL. HTTP is available only for a local development server.', 'invalid_endpoint');
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new SyncApiError('The sync API URL cannot contain credentials, a query, or a fragment.', 'invalid_endpoint');
  }
  if (url.pathname === '/') url.pathname = '/v1/snapshot';
  if (url.pathname !== '/v1/snapshot') {
    throw new SyncApiError('The sync API URL must end with /v1/snapshot.', 'invalid_endpoint');
  }
  return url.toString();
}

export function getApiOriginPattern(endpoint) {
  const url = new URL(normalizeEndpoint(endpoint));
  return `${url.protocol}//${url.host}/*`;
}

function isValidSnapshot(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || value.version !== 1) return false;
  if (!value.settings || !Number.isInteger(value.settings.requiredBlockCount)
    || value.settings.requiredBlockCount < 1
    || !['21:00', '22:00', '23:00'].includes(value.settings.eveningCutoff)) return false;
  if (!Array.isArray(value.desktopBlockedDomains) || value.desktopBlockedDomains.length > 100
    || value.desktopBlockedDomains.some(domain => typeof domain !== 'string' || !domainPattern.test(domain))
    || new Set(value.desktopBlockedDomains).size !== value.desktopBlockedDomains.length) return false;
  return Array.isArray(value.blocks) && value.blocks.length <= 1000
    && value.blocks.every(block => block && typeof block === 'object'
      && typeof block.id === 'string'
      && /^\d{4}-\d{2}-\d{2}$/.test(block.date)
      && typeof block.name === 'string'
      && /^([01]\d|2[0-3]):[0-5]\d$/.test(block.startTime)
      && /^([01]\d|2[0-3]):[0-5]\d$/.test(block.endTime)
      && completionStatuses.has(block.completionStatus));
}

function isValidResponse(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
    && Number.isInteger(value.revision) && value.revision >= 0
    && (value.updatedAt === null || typeof value.updatedAt === 'string')
    && isValidSnapshot(value.snapshot);
}

async function errorCode(response) {
  try {
    return (await response.json())?.error ?? '';
  } catch {
    return '';
  }
}

export async function fetchLatestSnapshot(endpoint, readToken, { fetchImpl = fetch } = {}) {
  if (typeof readToken !== 'string' || readToken.trim().length === 0) {
    throw new SyncApiError('Enter the sync API read token.', 'missing_token');
  }
  const url = normalizeEndpoint(endpoint);
  let response;
  try {
    response = await fetchImpl(url, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${readToken}`,
      },
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
    });
  } catch {
    throw new SyncApiError(
      'Could not reach the sync API. The cached snapshot remains active; check the connection and retry.',
      'connection_failed',
    );
  }

  if (!response.ok) {
    const code = await errorCode(response);
    if (response.status === 401 || response.status === 403) {
      throw new SyncApiError('The sync API rejected this read token. Check the token in Settings.', 'unauthorized');
    }
    if (response.status >= 500) {
      throw new SyncApiError(
        'The sync API is unavailable. The cached snapshot remains active; try again later.',
        'server_unavailable',
      );
    }
    throw new SyncApiError(`The sync API returned an unexpected response (${code || response.status}).`, 'api_error');
  }

  let result;
  try {
    result = await response.json();
  } catch {
    throw new SyncApiError('The sync API returned data that could not be read.', 'invalid_response');
  }
  if (!isValidResponse(result)) {
    throw new SyncApiError('The sync API returned an unsupported snapshot. Update the phone app and try again.', 'invalid_response');
  }
  return result;
}
