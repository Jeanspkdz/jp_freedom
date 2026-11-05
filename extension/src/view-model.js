export function getCurrentBlockMessage(status) {
  if (!status?.configured) {
    return 'Connect this extension in Settings to load the phone schedule.';
  }
  if (!status.current && !status.next && status.revision == null) {
    return 'No synced snapshot yet. Sync the phone schedule to get started.';
  }
  if (status.current) {
    if (status.missingDomains?.length > 0) {
      return `${status.current.name} is active. Grant site access to enforce the synced site blocklist.`;
    }
    return `${status.current.name} is active. Configured sites are blocked until this Planned block ends.`;
  }
  return 'No active focus block. Your configured sites are available.';
}
