import { getCurrentAndNext } from './policy.js';
import { activeStudySession } from './study-session.js';

export async function initializeBlockedPage({
  chromeApi = globalThis.chrome,
  documentRef = globalThis.document,
  now = () => new Date(),
} = {}) {
  const { lastValidSnapshot, lastStudySession, blockedVariant } = await chromeApi.storage.local.get(['lastValidSnapshot', 'lastStudySession', 'blockedVariant']);
  const block = getCurrentAndNext(lastValidSnapshot, now()).current;
  const timer = activeStudySession(lastStudySession, now()) ? lastStudySession : null;
  documentRef.getElementById('blocked-activity').textContent = block?.name ?? (timer ? 'Study timer' : 'No active focus block');
  const plannedEnd = documentRef.getElementById('blocked-end');
  documentRef.getElementById('blocked-end-row').hidden = !block && !timer;
  plannedEnd.textContent = block ? block.endTime : timer
    ? new Date(Date.parse(timer.startedAt) + timer.durationMillis).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }) : '';
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
