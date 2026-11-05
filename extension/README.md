# Chrome focus blocklist

The Manifest V3 extension reads the phone's versioned `/v1/snapshot` endpoint.
It keeps the latest successful snapshot in `chrome.storage.local` and uses
persistent `declarativeNetRequest` rules for top-level navigations during an
active Planned block. It does not write snapshots or edit plans, completion
statuses, or the desktop site blocklist.

## Load the unpacked extension

1. Open `chrome://extensions` in Chrome and enable **Developer mode**.
2. Choose **Load unpacked** and select this `extension` directory.
3. Open the extension's **Settings** and enter the sync API read endpoint
   (ending in `/v1/snapshot`) and `SYNC_API_READ_TOKEN`. The token is entered at
   runtime and stored only in this Chrome profile; it is not part of the
   extension package.
4. Choose **Save and connect**. Grant access to the sync API host when Chrome
   asks, then use **Sync now** if the initial request did not complete.
5. Open the extension popup. If it lists missing site permissions, choose
   **Allow site access** and grant access to the configured domains.

The phone remains the only place to edit the plan and desktop site blocklist.
The extension requests permissions for only the endpoint and domains in use.

## Planning reminders

At the configured evening cutoff, Chrome checks the cached phone snapshot for
the next required day plan. If it has fewer planned blocks than the configured
minimum, Chrome shows a notification that asks you to finish the plan on your
phone. It repeats every 30 minutes while that plan is short, stops when the
cached plan meets the minimum, and starts again if the count falls below the
minimum. Saturday has no required Sunday plan; Sunday evening checks Monday.

The reminder uses the cached snapshot and keeps working without a network
connection. A later successful sync updates the plan count. Notifications do
not block or redirect desktop browsing; the phone remains where plans are
edited.

## Chrome UI validation

Run these checks with an unpacked extension and a phone snapshot available.

1. On the phone, create a Planned block that starts now and ends a few minutes
   later. Sync it, grant the requested site permissions, and open the extension
   popup. Confirm it names the current block and shows the next block when one
   is scheduled.
2. Navigate to one configured domain and one of its subdomains, such as
   `facebook.com` and `www.facebook.com`. Both top-level navigations should
   open the extension's focus page. A domain outside the configured list and
   WhatsApp Web should remain available.
3. Open the popup after the planned end time. It should show no active focus
   block, and the configured sites should open normally. Repeat after marking
   the block **Finished** on the phone and syncing; Chrome should release the
   sites as soon as it receives that status.
4. With no active Planned block, trigger the phone's Evening restriction.
   Confirm that desktop browsing stays available.
5. While a block is active and cached, disconnect Chrome from the network.
   Confirm the sites remain blocked through the planned end. Reconnect and use
   **Sync now** (or wait for the five-minute sync alarm) after marking the
   block **Finished** on the phone; confirm the sites become available.
6. In Settings, temporarily enter an invalid token or deny a site permission.
   Confirm the popup shows a recoverable error and offers **Sync now** or
   **Allow site access** without clearing the last cached rules.
7. With an incomplete next day plan in the cached snapshot, disconnect Chrome
before the selected cutoff. Confirm Chrome shows a planning reminder at the
cutoff and repeats it every 30 minutes. Meet the required count in the phone
plan and sync; confirm the repeat stops. Reduce the count below the minimum
and sync again; confirm reminders resume. Desktop browsing should remain
available throughout.

The Node tests cover the policy, cached reminder scheduler, and read-only API
seams. These manual Chrome checks require a Chrome profile and a connected
phone/API; they are not run by the repository test command.
