# Desktop extension and personal sync

The Android app remains the source of truth for plans, settings, completion
states, and the desktop site blocklist. The Chrome extension reads a versioned
snapshot and applies desktop-only site blocking. Evening restriction never
blocks desktop browsing.

## Snapshot contract

`GET /v1/snapshot` returns `{ revision, updatedAt, snapshot }`. Android writes
the full snapshot with `PUT /v1/snapshot` and an `expectedRevision`. A stale
revision returns `409 revision_conflict`; the client must fetch the current
snapshot, preserve its local pending edits, and retry instead of overwriting a
newer copy. The extension only reads snapshots.

Snapshots contain `version: 1`, `settings.requiredBlockCount`,
`settings.eveningCutoff` (`21:00`, `22:00`, or `23:00`),
`desktopBlockedDomains`, and dated blocks with their profile, status, and
optional note. The default desktop list is Facebook, Instagram, TikTok, X,
YouTube, and Reddit. WhatsApp Web is not included.

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
- Only the phone writes plan snapshots; the extension is a read-only client.
- The database uses a single snapshot row and a monotonic revision to prevent
  silent last-write-wins data loss.
- Chrome's Manifest V3 service worker persists scheduling data in extension
  storage and uses alarms rather than long-lived timers.
- Site rules apply to top-level navigations and supported network requests;
  Chrome extension limits and site-owned service workers can affect coverage.
