import { getActiveStudySession } from './policy.js';
import { activeStudySession } from './study-session.js';

export async function initializeBlockedPage({
  chromeApi = globalThis.chrome,
  documentRef = globalThis.document,
  now = () => new Date(),
} = {}) {
  const { lastValidSnapshot, lastStudySession, blockedVariant } = await chromeApi.storage.local.get(['lastValidSnapshot', 'lastStudySession', 'blockedVariant']);
  const currentTime = now();
  const snapshotSession = getActiveStudySession(lastValidSnapshot, currentTime);
  const localSession = activeStudySession(lastStudySession, currentTime) ? lastStudySession : null;
  const session = snapshotSession ?? (localSession ? {
    taskName: localSession.taskName || 'Study timer',
    endsAt: new Date(Date.parse(localSession.startedAt) + localSession.durationMillis).toISOString(),
  } : null);
  documentRef.getElementById('blocked-activity').textContent = session?.taskName ?? 'No active focus session';
  const plannedEnd = documentRef.getElementById('blocked-end');
  const endRow = documentRef.getElementById('blocked-end-row');
  if (endRow) endRow.hidden = !session;
  else plannedEnd.hidden = !session;
  plannedEnd.textContent = session
    ? new Date(session.endsAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
    : '';
  const variant = documentRef.getElementById('blocked-variant');
  const scene = documentRef.getElementById('blocked-scene');
  if (variant && scene) {
    variant.value = blockedVariant === 'water' ? 'water' : 'particles';
    scene.src = variant.value === 'water' ? 'water-puddle.html' : 'focus-terrain.html?blocked';
    variant.addEventListener('change', async () => {
      scene.src = variant.value === 'water' ? 'water-puddle.html' : 'focus-terrain.html?blocked';
      await chromeApi.storage.local.set({ blockedVariant: variant.value });
    });
  }
}

if (globalThis.document && globalThis.chrome) void initializeBlockedPage();
