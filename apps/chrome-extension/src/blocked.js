import { getCurrentAndNext } from './policy.js';

export async function initializeBlockedPage({
  chromeApi = globalThis.chrome,
  documentRef = globalThis.document,
  now = () => new Date(),
}) {
  const { lastValidSnapshot } = await chromeApi.storage.local.get(['lastValidSnapshot']);
  const block = getCurrentAndNext(lastValidSnapshot, now()).current;
  documentRef.getElementById('blocked-activity').textContent = block?.name ?? 'No active focus block';
  documentRef.getElementById('blocked-end').textContent = block ? `Planned end: ${block.endTime}` : 'Planned end: —';
}

if (globalThis.document && globalThis.chrome) void initializeBlockedPage();
