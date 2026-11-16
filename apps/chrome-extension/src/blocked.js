import { getCurrentAndNext } from './policy.js';

export async function initializeBlockedPage({
  chromeApi = globalThis.chrome,
  documentRef = globalThis.document,
  now = () => new Date(),
} = {}) {
  const { lastValidSnapshot, blockedVariant } = await chromeApi.storage.local.get(['lastValidSnapshot', 'blockedVariant']);
  const block = getCurrentAndNext(lastValidSnapshot, now()).current;
  documentRef.getElementById('blocked-activity').textContent = block?.name ?? 'No active focus block';
  const plannedEnd = documentRef.getElementById('blocked-end');
  documentRef.getElementById('blocked-end-row').hidden = !block;
  plannedEnd.textContent = block ? block.endTime : '';
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
