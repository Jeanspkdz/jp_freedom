import { getActiveBlock, makeDesktopBlockRules } from './policy.js';
import { activeStudySession } from './study-session.js';

export async function applySnapshotRules(chromeApi, snapshot, now = new Date(), session = null) {
  const existingRules = await chromeApi.declarativeNetRequest.getDynamicRules();
  const activeBlockId = getActiveBlock(snapshot, now) ?? (activeStudySession(session, now) ? session.id : null);
  const rules = makeDesktopBlockRules(snapshot, activeBlockId);
  await chromeApi.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existingRules.map(rule => rule.id),
    addRules: rules,
  });
  return activeBlockId;
}
