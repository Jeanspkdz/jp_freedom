import { DEFAULT_SNAPSHOT } from './api.js';

export function createPostgresSnapshotStore(pool) {
  return {
    async readHistory() {
      const result = await pool.query('SELECT session FROM focus_study_history ORDER BY session->>\'startedAt\'');
      return result.rows.map(row => row.session);
    },

    async importHistory(sessions) {
      await pool.query(
        `INSERT INTO focus_study_history (id, session)
         SELECT value->>'id', value FROM jsonb_array_elements($1::jsonb) AS value
         ON CONFLICT (id) DO UPDATE SET session = EXCLUDED.session
         WHERE focus_study_history.session->>'state' IN ('ACTIVE', 'PENDING_REVIEW')
           AND EXCLUDED.session->>'state' = 'REVIEWED'`,
        [JSON.stringify(sessions)],
      );
      return this.readHistory();
    },

    async resolveHistory(session) {
      const result = await pool.query(
        `WITH updated_history AS (
           UPDATE focus_study_history SET session = $2::jsonb WHERE id = $1 RETURNING session
         ), updated_current AS (
           UPDATE focus_study_session SET session = $2::jsonb, revision = revision + 1, updated_at = now()
           WHERE session->>'id' = $1 RETURNING revision
         ) SELECT session FROM updated_history`,
        [session.id, JSON.stringify(session)],
      );
      if (!result.rows[0]) throw new Error('Study session history record not found');
      return result.rows[0].session;
    },
    async readSession() {
      const result = await pool.query('SELECT revision, updated_at, session FROM focus_study_session WHERE singleton_id = 1');
      const row = result.rows[0];
      return row ? { revision: Number(row.revision), updatedAt: row.updated_at?.toISOString?.() ?? null, session: row.session }
        : { revision: 0, updatedAt: null, session: null };
    },

    async replaceSession(expectedRevision, session) {
      const result = await pool.query(
        `WITH changed AS (
           UPDATE focus_study_session SET revision = revision + 1, session = $1::jsonb, updated_at = now()
           WHERE singleton_id = 1 AND revision = $2 RETURNING revision, updated_at, session
         ), recorded AS (
           INSERT INTO focus_study_history (id, session)
           SELECT changed.session->>'id', changed.session FROM changed
           ON CONFLICT (id) DO UPDATE SET session = EXCLUDED.session
           RETURNING id
         )
         SELECT changed.revision, changed.updated_at, changed.session FROM changed JOIN recorded ON true`,
        [JSON.stringify(session), expectedRevision],
      );
      const row = result.rows[0];
      if (row) return { conflict: false, revision: Number(row.revision), updatedAt: row.updated_at?.toISOString?.() ?? null, session: row.session };
      const current = await pool.query('SELECT revision FROM focus_study_session WHERE singleton_id = 1');
      return { conflict: true, revision: Number(current.rows[0]?.revision ?? 0) };
    },
    async read() {
      const result = await pool.query(
        'SELECT revision, updated_at, snapshot FROM focus_sync_snapshot WHERE singleton_id = 1',
      );
      const row = result.rows[0];
      if (!row) return { revision: 0, updatedAt: null, snapshot: DEFAULT_SNAPSHOT };
      return {
        revision: Number(row.revision),
        updatedAt: row.updated_at?.toISOString?.() ?? null,
        snapshot: row.snapshot,
      };
    },

    async replace(expectedRevision, snapshot) {
      const result = await pool.query(
        `UPDATE focus_sync_snapshot
         SET revision = revision + 1, snapshot = $1::jsonb, updated_at = now()
         WHERE singleton_id = 1 AND revision = $2
         RETURNING revision, updated_at, snapshot`,
        [JSON.stringify(snapshot), expectedRevision],
      );
      const row = result.rows[0];
      if (row) {
        return {
          conflict: false,
          revision: Number(row.revision),
          updatedAt: row.updated_at?.toISOString?.() ?? null,
          snapshot: row.snapshot,
        };
      }
      const current = await pool.query(
        'SELECT revision FROM focus_sync_snapshot WHERE singleton_id = 1',
      );
      return { conflict: true, revision: Number(current.rows[0]?.revision ?? 0) };
    },
  };
}
