import { defineConfig } from '@neon/config/v1';

export default defineConfig({
  functions: {
    jpfreedomsync: {
      name: 'JP Freedom sync API',
      source: './packages/sync-api/src/index.js',
      env: {
        SYNC_API_READ_TOKEN: process.env.SYNC_API_READ_TOKEN!,
        SYNC_API_WRITE_TOKEN: process.env.SYNC_API_WRITE_TOKEN!,
      },
    },
  },
});
