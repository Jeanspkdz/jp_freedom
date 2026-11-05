# Versioned sync API

The Android app remains the source of truth for day plans, settings, completion
states, and the desktop site blocklist. The API stores one versioned snapshot
in Neon Postgres. The Chrome extension uses read-only access to apply desktop
site blocking; it does not update snapshots.

## Snapshot contract

`GET /v1/snapshot` returns:

```json
{
  "revision": 0,
  "updatedAt": "2026-11-05T12:00:00.000Z",
  "snapshot": {
    "version": 1,
    "settings": {
      "requiredBlockCount": 3,
      "eveningCutoff": "21:00"
    },
    "desktopBlockedDomains": ["facebook.com", "instagram.com"],
    "blocks": []
  }
}
```

The initial migration seeds revision `0`, the default required block count of
three, a `21:00` evening cutoff, the default desktop blocklist
(`facebook.com`, `instagram.com`, `tiktok.com`, `x.com`, `youtube.com`, and
`reddit.com`), and no planned blocks. WhatsApp Web is not in the default list.

Android writes the full snapshot with `PUT /v1/snapshot` and a JSON body:

```json
{ "expectedRevision": 0, "snapshot": { "version": 1 } }
```

The API increments the revision only when `expectedRevision` equals the stored
revision. A stale write returns `409` with `revision_conflict` and the current
revision; it never replaces the newer snapshot. The client should fetch the
current snapshot, preserve pending local edits, and retry against its revision.

Snapshots are limited to version `1`. `requiredBlockCount` must be a positive
32-bit integer (1 through 2,147,483,647), and `eveningCutoff` must be `21:00`,
`22:00`, or `23:00`.
Desktop site blocklist entries must be unique lowercase DNS names without a
scheme, path, wildcard, or port; at most 100 are accepted. Planned blocks must
use real `YYYY-MM-DD` dates, increasing `HH:mm` times, supported access
profiles (`ESSENTIALS_ONLY`, `ESSENTIALS_WITH_CHATGPT`, or
`ESSENTIALS_WITH_CHATGPT_AND_SEARCH`), and completion statuses (`NOT_STARTED`,
`FINISHED`, `PARTLY_DONE`, or `NOT_DONE`). At most 1,000 blocks are accepted;
each block ID must be 1 to 80 ASCII letters, digits, underscores, or hyphens.
Names must be nonblank and no longer than 200 characters. Optional notes are
limited to 2,000 characters and can record what remains after a block. Planned
blocks on the same date cannot overlap.

## Authorization

Every route except `GET /health` requires a bearer token. The server has two
distinct tokens:

- `SYNC_API_READ_TOKEN` authorizes `GET /v1/snapshot` for the Chrome extension.
- `SYNC_API_WRITE_TOKEN` authorizes Android `GET` and `PUT` requests. It can
  read so Android can fetch the current revision before writing.

The write token authorizes both reads and writes; the read token gets
`403 read_only` if it attempts a `PUT`. Missing or invalid
tokens receive `401 unauthorized`. Tokens are loaded into the server from its
environment; the Neon database URL is injected by Neon into the server runtime.
The `neon.ts` config declares only the two sync tokens. Do not place database
credentials or token literals in source code, Android build configuration, or
the extension bundle. Configure each client with its role-specific token at
runtime; the extension receives only the read token.

`OPTIONS` requests support browser extension preflight. The public health route
returns `{ "ok": true }` without exposing configuration.

Android shows the last server revision it has stored, and Chrome shows the last
server revision it successfully fetched. Comparing those values lets the user
check whether Chrome has fetched the current server snapshot. The extension
remains read-only; the API does not record a Chrome acknowledgement.

## Local development

Requirements: Node.js 20.19 or newer, pnpm, and a Neon project.

1. Install dependencies with `pnpm install`.
2. Link this directory to your Neon project: `pnpm dlx neon@latest link`.
3. Pull the linked branch's database connection settings:
   `pnpm dlx neon@latest env pull --file .env.local`.
4. Add distinct random values for `SYNC_API_READ_TOKEN` and
   `SYNC_API_WRITE_TOKEN` to `.env.local`. Keep the file private; it is ignored
   by Git. `.env.example` shows the required variable names.
5. Apply the snapshot migration: `pnpm run migrate:sync-api`. The migration
   prefers Neon’s unpooled connection URL and can be rerun safely.
6. Run the local HTTP server with `pnpm run start:sync-api`. It listens on port
   8787 by default and reads `.env.local`.
7. Run the API tests with `pnpm test:sync-api`.

To deploy the function, run:

```sh
pnpm dlx neon@latest deploy --env .env.local
```

The `neon deploy` command applies the checked-in `neon.ts` config. The `.env`
file is used while evaluating that config; only the tokens listed in the
function's `env` map are deployed from it. Neon supplies `DATABASE_URL` to the
function automatically. See the current [Neon Functions overview](https://neon.com/docs/compute/functions/overview),
[environment variable guide](https://neon.com/docs/compute/functions/environment-variables),
and [CLI docs](https://neon.com/docs/cli).
