import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const { Pool } = pg;
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required to apply the sync API migration.');

const here = dirname(fileURLToPath(import.meta.url));
const migration = await readFile(resolve(here, '../migrations/001_focus_sync_snapshot.sql'), 'utf8');
const pool = new Pool({ connectionString: databaseUrl, max: 1, connectionTimeoutMillis: 5_000 });
try {
  await pool.query(migration);
  console.log('Applied 001_focus_sync_snapshot.sql');
} finally {
  await pool.end();
}
