export function getActiveStudySession(snapshot, now = new Date()) {
  const session = snapshot?.activeStudySession;
  if (!session || typeof session.startedAt !== 'string' || typeof session.endsAt !== 'string') return null;
  const startsAt = Date.parse(session.startedAt);
  const endsAt = Date.parse(session.endsAt);
  if (!Number.isFinite(startsAt) || !Number.isFinite(endsAt) || startsAt >= endsAt) return null;
  return startsAt <= now.getTime() && now.getTime() < endsAt ? session : null;
}

export function makeDesktopBlockRules(snapshot, activeSession) {
  if (!snapshot || !activeSession) return [];
  const domains = [...new Set(snapshot.desktopBlockedDomains ?? [])];
  return domains.map((domain, index) => ({
    id: index + 1,
    priority: 1,
    action: { type: 'block' },
    condition: { requestDomains: [domain], resourceTypes: ['main_frame'] },
  }));
}
