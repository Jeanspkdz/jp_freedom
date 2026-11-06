import { getActiveBlock, makeDesktopBlockRules } from './policy.js';

export async function applySnapshotRules(chromeApi, snapshot, now = new Date()) {
  const existingRules = await chromeApi.declarativeNetRequest.getDynamicRules();
  const activeBlockId = getActiveBlock(snapshot, now);
  let enforceableSnapshot = snapshot;
  if (activeBlockId && snapshot && chromeApi.permissions?.contains) {
    const domains = [];
    for (const domain of new Set(snapshot.desktopBlockedDomains ?? [])) {
      const origins = [`http://${domain}/*`, `https://${domain}/*`];
      if (await chromeApi.permissions.contains({ origins })) domains.push(domain);
    }
    enforceableSnapshot = { ...snapshot, desktopBlockedDomains: domains };
  }
  const rules = makeDesktopBlockRules(enforceableSnapshot, activeBlockId);
  await chromeApi.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existingRules.map(rule => rule.id),
    addRules: rules,
  });
  return activeBlockId;
}
