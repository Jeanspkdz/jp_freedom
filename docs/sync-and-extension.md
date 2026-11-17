# Desktop extension and personal sync

This document describes the sync contract. The agreed behavior for shared study
timers is in [Chrome study sessions](specs/chrome-study-session.md) and ADR-0003.

## Study timer contract

`GET /v1/session` returns `{ revision, updatedAt, session }`. `session` is null
before the first timer is shared. `PUT /v1/session` accepts
`{ expectedRevision, session }` and returns `409 revision_conflict` if another
device wrote first. This resource is separate from `/v1/snapshot`, so a timer
update cannot replace plan or blocklist changes. A Chrome-started session uses
the `ESSENTIALS_ONLY` profile. Chrome measures its remaining time from
`startedAt` and `durationMillis`; stopping or reaching the deadline moves it
to `PENDING_REVIEW`, with review performed on Android.

`GET /v1/history` returns all recorded sessions; Android uses
`PUT /v1/history` to import existing local study history without overwriting
server records with the same ID. The `/v1/session` resource stores the latest
session and records each accepted update in shared history. Chrome asks which
timer to continue when both devices started one offline; the other attempt is
retained in history. Android shows a choice for divergent reviews of the same
session in sync settings.

The Android app remains the source of truth for plans, settings, and completion
states. The desktop site blocklist is shared and can be edited from Android or
the Chrome extension. Chrome applies that list during focus blocks. Evening
restriction never blocks desktop browsing.

## Initial desktop release

The first Chrome release is installed manually for personal use. Planned block
dates and times use each device's local clock; for this release, the phone and
computer are expected to use the same time zone.

## Snapshot contract

`GET /v1/snapshot` returns `{ revision, updatedAt, snapshot }`. Android writes
plans, settings, and completion states with `PUT /v1/snapshot`; Android and the
extension can update `desktopBlockedDomains` through that same revision-checked
route. A stale revision returns `409 revision_conflict`; the client must fetch
the current snapshot, preserve unrelated fields and its pending edits, and
retry instead of overwriting a newer copy. Chrome writes only
`desktopBlockedDomains`.

For a genuinely new snapshot (`revision: 0`, `updatedAt: null`) with an empty
`desktopBlockedDomains` list, Chrome initializes the shared list with Facebook,
Instagram, TikTok, X, and Reddit using the revision-checked route. Later empty
lists are intentional and stay empty.

Snapshots contain `version: 1`, `settings.requiredBlockCount`,
`settings.eveningCutoff` (`21:00`, `22:00`, or `23:00`),
`desktopBlockedDomains`, and dated blocks with their profile, status, and
optional note. The initial desktop list selects Facebook, Instagram, TikTok, X,
and Reddit. YouTube and WhatsApp Web are available unless selected by the user.

While Chrome is open, the service worker polls the snapshot API once per minute
and refreshes immediately on startup. This receives Android changes, including
Finished blocks, without requiring the management page to remain open. If the
API is unavailable, Chrome keeps enforcing its last valid cached snapshot.

Chrome requires an internet connection to edit the shared list. While offline,
it continues enforcing the last synchronized list. During a focus block,
selected sites cannot be removed from either client, but additional sites may
be added and take effect immediately. Outside a block, the list can be edited
freely. The custom domain field should show an example such as `example.com`;
entries also cover their subdomains.

Until the extension receives its first valid snapshot, it does not block
browsing and shows that synchronization is not active.

The extension opens a full management page when clicked. That page shows the
shared domain list, current block and its planned end time, and synchronization
status. A blocked page shows the activity and planned end time; it has no bypass
control. Marking the block Finished on Android releases it when the extension
receives the updated snapshot. Blocking applies to top-level page navigation,
not embedded content on unrelated sites.

Every route except `/health` requires `Authorization: Bearer <SYNC_API_TOKEN>`.
The token is supplied to the Neon Function at deploy time and entered into each
client's settings; the Neon database URL stays on the server. This is a
single-user installation, not an account or multi-user service.

## Local development

1. From `services/sync-api`, run `pnpm install`.
2. Create a Neon project and link it with the Neon CLI: `pnpm dlx neon link`.
3. Pull the branch database URL: `pnpm dlx neon env pull`.
4. Copy `.env.example` to `.env.local` and add a random `SYNC_API_TOKEN` (keep the file private).
5. Apply the schema: `pnpm run migrate`.
6. Deploy the function: `pnpm dlx neon deploy --env .env.local`.
7. Copy the function URL and token into Dimky on Android and the Chrome
   extension's connection settings.

The local API can run from `services/sync-api` with `pnpm run start`; it listens on port 8787
and uses `.env.local`. `pnpm run test` exercises the
HTTP seam without requiring Neon credentials or a live database.

## Security and behavior

- Do not commit `.env.local`, Neon connection strings, or the sync token.
- The phone owns plan, settings, and completion edits; the extension can edit
  only the shared desktop site blocklist.
- The database uses a single snapshot row and a monotonic revision to prevent
  silent last-write-wins data loss.
- Chrome's Manifest V3 service worker persists scheduling data in extension
  storage and uses alarms rather than long-lived timers.
- Site rules apply to top-level navigations and supported network requests;
  Chrome extension limits and site-owned service workers can affect coverage.
