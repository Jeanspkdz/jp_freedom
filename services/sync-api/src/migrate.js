import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const { Pool } = pg;
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required to apply the sync API migration.');

const here = dirname(fileURLToPath(import.meta.url));
const migrations = [
  '../migrations/001_focus_sync_snapshot.sql',
  '../migrations/002_focus_study_session.sql',
  '../migrations/003_focus_study_history.sql',
];
const pool = new Pool({ connectionString: databaseUrl, max: 1, connectionTimeoutMillis: 5_000 });
try {
  for (const path of migrations) {
    await pool.query(await readFile(resolve(here, path), 'utf8'));
    console.log(`Applied ${path.split('/').at(-1)}`);
  }
} finally {
  await pool.end();
}
