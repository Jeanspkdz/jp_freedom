import { snapshotEndpoint } from './sync-client.js';

function endpoint(apiUrl) { return snapshotEndpoint(apiUrl).replace(/\/v1\/snapshot$/, '/v1/session'); }
function headers(token) { return { Accept: 'application/json', Authorization: `Bearer ${token}` }; }

// The API is the shared session record; every write uses its revision.
export async function readStudySession(apiUrl, token, fetchImpl = globalThis.fetch) {
  const response = await fetchImpl(endpoint(apiUrl), {
    method: 'GET', headers: headers(token), cache: 'no-store', credentials: 'omit', redirect: 'error',
  });
  if (!response.ok) throw new Error(`Could not read the study session (HTTP ${response.status}).`);
  const value = await response.json();
  if (!Number.isInteger(value.revision) || !(value.session === null || typeof value.session === 'object')) {
    throw new Error('The sync API returned an invalid study session.');
  }
  return value;
}

async function writeStudySession(apiUrl, token, revision, session, fetchImpl) {
  const response = await fetchImpl(endpoint(apiUrl), {
    method: 'PUT', headers: { ...headers(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ expectedRevision: revision, session }),
    cache: 'no-store', credentials: 'omit', redirect: 'error',
  });
  if (response.status === 409) throw new Error('The study session changed on another device. Sync and try again.');
  if (!response.ok) throw new Error(`Could not save the study session (HTTP ${response.status}).`);
  return response.json();
}

export function activeStudySession(session, now = new Date()) {
  return session?.state === 'ACTIVE' && Date.parse(session.startedAt) + session.durationMillis > now.getTime();
}

export async function startStudySession(apiUrl, token, durationMinutes, fetchImpl = globalThis.fetch,
  now = new Date(), makeId = () => crypto.randomUUID()) {
  if (!Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 1440) throw new Error('Choose a duration from 1 to 1440 minutes.');
  const current = await readStudySession(apiUrl, token, fetchImpl);
  if (current.session && current.session.state !== 'REVIEWED') {
    throw new Error(current.session.state === 'PENDING_REVIEW'
      ? 'Review the previous study session on Android before starting another.'
      : 'A study session is already active.');
  }
  const session = {
    id: makeId(), startedAt: now.toISOString(), durationMillis: durationMinutes * 60_000,
    state: 'ACTIVE', accessProfile: 'ESSENTIALS_ONLY', taskName: '',
  };
  return writeStudySession(apiUrl, token, current.revision, session, fetchImpl);
}

export async function stopStudySession(apiUrl, token, fetchImpl = globalThis.fetch, now = new Date()) {
  const current = await readStudySession(apiUrl, token, fetchImpl);
  if (!activeStudySession(current.session, now)) throw new Error('No active study session to stop.');
  const actualMillis = Math.max(0, Math.min(now.getTime() - Date.parse(current.session.startedAt), current.session.durationMillis));
  return writeStudySession(apiUrl, token, current.revision, {
    ...current.session, endedAt: now.toISOString(), actualMillis, state: 'PENDING_REVIEW',
  }, fetchImpl);
}

export async function finishStudySessionIfDue(apiUrl, token, current, fetchImpl = globalThis.fetch, now = new Date()) {
  const session = current?.session;
  if (session?.state !== 'ACTIVE' || activeStudySession(session, now)) return current;
  const endedAt = new Date(Date.parse(session.startedAt) + session.durationMillis).toISOString();
  return writeStudySession(apiUrl, token, current.revision, {
    ...session, endedAt, actualMillis: session.durationMillis, state: 'PENDING_REVIEW',
  }, fetchImpl);
}

export function makeLocalStudySession(durationMinutes, now = new Date(), makeId = () => crypto.randomUUID()) {
  if (!Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 1440) throw new Error('Choose a duration from 1 to 1440 minutes.');
  return {
    id: makeId(), startedAt: now.toISOString(), durationMillis: durationMinutes * 60_000,
    state: 'ACTIVE', accessProfile: 'ESSENTIALS_ONLY', taskName: '',
  };
}

export async function publishPendingStudySession(apiUrl, token, pending, fetchImpl = globalThis.fetch) {
  const current = await readStudySession(apiUrl, token, fetchImpl);
  if (current.session && current.session.id !== pending.id && current.session.state !== 'REVIEWED') {
    throw new Error('Another study session was started on a different device. Choose which session to keep.');
  }
  return writeStudySession(apiUrl, token, current.revision, pending, fetchImpl);
}

export async function resolveStudyConflict(apiUrl, token, localSession, choice, fetchImpl = globalThis.fetch, now = new Date()) {
  const current = await readStudySession(apiUrl, token, fetchImpl);
  if (choice === 'chrome') {
    const supersededAttemptIds = current.session && current.session.id !== localSession.id
      ? [current.session.id] : [];
    return writeStudySession(apiUrl, token, current.revision,
      { ...localSession, supersededAttemptIds }, fetchImpl);
  }
  if (choice === 'android') {
    const preserved = {
      ...localSession, state: 'REVIEWED', endedAt: now.toISOString(), outcome: 'NOT_FINISHED',
      note: 'Ended after choosing another offline timer',
      actualMillis: Math.max(0, Math.min(now.getTime() - Date.parse(localSession.startedAt), localSession.durationMillis)),
    };
    const response = await fetchImpl(endpoint(apiUrl).replace(/\/v1\/session$/, '/v1/history'), {
      method: 'PUT', headers: { ...headers(token), 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessions: [preserved] }),
      cache: 'no-store', credentials: 'omit', redirect: 'error',
    });
    if (!response.ok) throw new Error(`Could not preserve the Chrome timer attempt (HTTP ${response.status}).`);
    return current;
  }
  throw new Error('Choose which timer to continue.');
}
