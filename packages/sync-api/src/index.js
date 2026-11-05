import pg from 'pg';
import { createSyncApi } from './api.js';
import { createPostgresSnapshotStore } from './postgres-store.js';

const { Pool } = pg;
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 5,
  idleTimeoutMillis: 10_000,
  connectionTimeoutMillis: 5_000,
});
const api = createSyncApi({
  store: createPostgresSnapshotStore(pool),
  readToken: process.env.SYNC_API_READ_TOKEN,
  writeToken: process.env.SYNC_API_WRITE_TOKEN,
});

export default { fetch: api };
