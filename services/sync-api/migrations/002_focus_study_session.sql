CREATE TABLE IF NOT EXISTS focus_study_session (
  singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
  revision BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ,
  session JSONB
);

INSERT INTO focus_study_session (singleton_id, revision, session)
VALUES (1, 0, NULL)
ON CONFLICT (singleton_id) DO NOTHING;
