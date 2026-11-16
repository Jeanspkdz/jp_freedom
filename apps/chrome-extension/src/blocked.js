import { getActiveStudySession, getCurrentAndNext } from './policy.js';

export async function initializeBlockedPage({
  chromeApi = globalThis.chrome,
  documentRef = globalThis.document,
  now = () => new Date(),
} = {}) {
  const { lastValidSnapshot } = await chromeApi.storage.local.get(['lastValidSnapshot']);
  const currentTime = now();
  const session = getActiveStudySession(lastValidSnapshot, currentTime);
  const block = session ? null : getCurrentAndNext(lastValidSnapshot, currentTime).current;
  documentRef.getElementById('blocked-activity').textContent = session?.taskName ?? block?.name ?? 'No active focus block';
  const plannedEnd = documentRef.getElementById('blocked-end');
  plannedEnd.hidden = !session && !block;
  plannedEnd.textContent = session
    ? `Planned end: ${new Date(session.endsAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}`
    : block ? `Planned end: ${block.endTime}` : '';
}

if (globalThis.document && globalThis.chrome) void initializeBlockedPage();
