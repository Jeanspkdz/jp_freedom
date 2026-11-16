import { getActiveBlock, getActiveStudySession, makeDesktopBlockRules } from './policy.js';

export async function applySnapshotRules(chromeApi, snapshot, now = new Date()) {
  const existingRules = await chromeApi.declarativeNetRequest.getDynamicRules();
  const activeBlockId = getActiveBlock(snapshot, now);
  const activeSession = getActiveStudySession(snapshot, now);
  const rules = makeDesktopBlockRules(snapshot, activeBlockId || activeSession);
  await chromeApi.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existingRules.map(rule => rule.id),
    addRules: rules,
  });
  return activeBlockId;
}
