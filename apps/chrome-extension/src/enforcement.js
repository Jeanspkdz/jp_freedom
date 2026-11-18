import { getActiveStudySession, makeDesktopBlockRules } from './policy.js';
import { activeStudySession } from './study-session.js';

export async function applySnapshotRules(chromeApi, snapshot, now = new Date(), session = null) {
  const existingRules = await chromeApi.declarativeNetRequest.getDynamicRules();
  const activeSession = getActiveStudySession(snapshot, now) || (activeStudySession(session, now) ? session : null);
  const rules = makeDesktopBlockRules(snapshot, activeSession);
  await chromeApi.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existingRules.map(rule => rule.id),
    addRules: rules,
  });
  return activeSession?.id ?? null;
}
