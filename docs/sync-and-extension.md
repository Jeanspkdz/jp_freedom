# Desktop extension and personal sync

The Android app remains the source of truth for plans, settings, and completion
states. The desktop site blocklist is shared and can be edited from Android or
the Chrome extension. Chrome applies that list during focus blocks. Evening
restriction never blocks desktop browsing.

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
7. Copy the function URL and token into JP Freedom on Android and the Chrome
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
