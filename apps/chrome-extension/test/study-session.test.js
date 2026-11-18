import assert from 'node:assert/strict';
import test from 'node:test';
import { readStudySession, startStudySession, stopStudySession, finishStudySessionIfDue, resolveStudyConflict } from '../src/study-session.js';

test('Chrome starts a timed study session and stops the same session through the sync API', async () => {
  let record = { revision: 0, session: null };
  const fetchImpl = async (_url, init) => {
    if (init.method === 'GET') return new Response(JSON.stringify(record), { status: 200 });
    const request = JSON.parse(init.body);
    assert.equal(request.expectedRevision, record.revision);
    record = { revision: record.revision + 1, session: request.session };
    return new Response(JSON.stringify(record), { status: 200 });
  };
  const started = await startStudySession('https://sync.example', 'token', 30, fetchImpl,
    new Date('2026-11-16T12:00:00.000Z'), () => 'study-1');
  assert.equal(started.session.durationMillis, 1_800_000);
  assert.equal(started.session.state, 'ACTIVE');
  assert.equal((await readStudySession('https://sync.example', 'token', fetchImpl)).session.id, 'study-1');
  const stopped = await stopStudySession('https://sync.example', 'token', fetchImpl,
    new Date('2026-11-16T12:10:00.000Z'));
  assert.equal(stopped.session.state, 'PENDING_REVIEW');
  assert.equal(stopped.session.actualMillis, 600_000);
});

test('an elapsed timer finishes at its original deadline and requires Android review', async () => {
  let record = { revision: 2, session: {
    id: 'study-1', startedAt: '2026-11-16T12:00:00.000Z', durationMillis: 30 * 60_000,
    state: 'ACTIVE', accessProfile: 'ESSENTIALS_ONLY', taskName: '',
  } };
  const fetchImpl = async (_url, init) => {
    if (init.method === 'PUT') {
      const body = JSON.parse(init.body);
      assert.equal(body.expectedRevision, 2);
      record = { revision: 3, session: body.session };
    }
    return new Response(JSON.stringify(record), { status: 200 });
  };
  const finished = await finishStudySessionIfDue('https://sync.example', 'token', record, fetchImpl,
    new Date('2026-11-16T12:31:00.000Z'));
  assert.equal(finished.session.endedAt, '2026-11-16T12:30:00.000Z');
  assert.equal(finished.session.actualMillis, 1_800_000);
  assert.equal(finished.session.state, 'PENDING_REVIEW');
  await assert.rejects(() => startStudySession('https://sync.example', 'token', 20, fetchImpl), /Review the previous/);
});

test('choosing the Chrome timer preserves the Android attempt and records the choice', async () => {
  const chromeSession = { id: 'chrome-1', startedAt: '2026-11-16T12:00:00.000Z', durationMillis: 1_800_000,
    state: 'ACTIVE', accessProfile: 'ESSENTIALS_ONLY', taskName: '' };
  const androidSession = { ...chromeSession, id: 'android-1' };
  const calls = [];
  const fetchImpl = async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/v1/session') && options.method === 'GET') {
      return new Response(JSON.stringify({ revision: 3, session: androidSession }));
    }
    return new Response(JSON.stringify({ revision: 4, session: JSON.parse(options.body).session }));
  };
  const result = await resolveStudyConflict('https://sync.example', 'token', chromeSession, 'chrome', fetchImpl);
  assert.equal(result.session.id, 'chrome-1');
  assert.deepEqual(result.session.supersededAttemptIds, ['android-1']);
  assert.equal(JSON.parse(calls[1].options.body).expectedRevision, 3);
});

test('choosing the Android timer keeps the Chrome attempt in shared history', async () => {
  const chromeSession = { id: 'chrome-1', startedAt: '2026-11-16T12:00:00.000Z', durationMillis: 1_800_000,
    state: 'ACTIVE', accessProfile: 'ESSENTIALS_ONLY', taskName: '' };
  const androidSession = { ...chromeSession, id: 'android-1' };
  let history;
  const fetchImpl = async (url, options) => {
    if (url.endsWith('/v1/history')) {
      history = JSON.parse(options.body).sessions;
      return new Response(JSON.stringify({ sessions: history }));
    }
    return new Response(JSON.stringify({ revision: 3, session: androidSession }));
  };
  const selected = await resolveStudyConflict('https://sync.example', 'token', chromeSession, 'android', fetchImpl,
    new Date('2026-11-16T12:05:00.000Z'));
  assert.equal(selected.session.id, 'android-1');
  assert.equal(history[0].id, 'chrome-1');
  assert.equal(history[0].state, 'REVIEWED');
  assert.equal(history[0].actualMillis, 300_000);
});
