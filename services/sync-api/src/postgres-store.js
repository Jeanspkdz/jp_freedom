import { DEFAULT_SNAPSHOT } from './api.js';

export function createPostgresSnapshotStore(pool) {
  return {
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
