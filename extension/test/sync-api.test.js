import assert from 'node:assert/strict';
import test from 'node:test';
import { fetchLatestSnapshot, getApiOriginPattern, normalizeEndpoint, SyncApiError } from '../src/sync-api.js';

const serverSnapshot = {
  revision: 7,
  updatedAt: '2026-11-05T12:00:00.000Z',
  snapshot: {
    version: 1,
    settings: { requiredBlockCount: 3, eveningCutoff: '21:00' },
    desktopBlockedDomains: ['facebook.com'],
    blocks: [],
  },
};

test('the extension reads the versioned snapshot with its read-only bearer token', async () => {
  let request;
  const result = await fetchLatestSnapshot(
    'https://sync.example/v1/snapshot',
    'runtime-read-token',
    {
      fetchImpl: async (url, options) => {
        request = { url, options };
        return { ok: true, async json() { return serverSnapshot; } };
      },
    },
  );

  assert.equal(request.url, 'https://sync.example/v1/snapshot');
  assert.equal(request.options.method, 'GET');
  assert.equal(request.options.headers.Authorization, 'Bearer runtime-read-token');
  assert.equal('body' in request.options, false);
  assert.deepEqual(result, serverSnapshot);
});

test('the connection URL resolves to the snapshot route and an exact API host permission', () => {
  assert.equal(normalizeEndpoint('https://sync.example'), 'https://sync.example/v1/snapshot');
  assert.equal(getApiOriginPattern('https://sync.example/v1/snapshot'), 'https://sync.example/*');
  assert.equal(normalizeEndpoint('http://localhost:8787'), 'http://localhost:8787/v1/snapshot');
  assert.throws(() => normalizeEndpoint('http://sync.example/v1/snapshot'), SyncApiError);
});

test('an invalid read token returns a recoverable error without contacting the API', async () => {
  let requests = 0;
  await assert.rejects(
    fetchLatestSnapshot('https://sync.example/v1/snapshot', '', {
      fetchImpl: async () => { requests += 1; },
    }),
    error => error.code === 'missing_token',
  );
  assert.equal(requests, 0);
});
