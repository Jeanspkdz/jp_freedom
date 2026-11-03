# Use Neon for personal sync

The first version uses Neon's free Postgres backend and functions to sync day plans, phone access profiles, the desktop site blocklist, and session state between one Android phone and a Chrome extension. Supabase's free project limit is already reached, while Neon currently offers enough free capacity for this personal use; keeping a small sync API in front of standard Postgres data also leaves a path to another host later.
