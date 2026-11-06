CREATE TABLE IF NOT EXISTS focus_sync_snapshot (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    snapshot JSONB NOT NULL
);

INSERT INTO focus_sync_snapshot (singleton_id, revision, snapshot)
VALUES (
    1,
    0,
    '{
      "version": 1,
      "settings": {"requiredBlockCount": 3, "eveningCutoff": "21:00"},
      "desktopBlockedDomains": ["facebook.com", "instagram.com", "tiktok.com", "x.com", "reddit.com"],
      "blocks": []
    }'::jsonb
)
ON CONFLICT (singleton_id) DO NOTHING;
