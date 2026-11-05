import { defineConfig } from '@neon/config/v1';

export default defineConfig({
  functions: {
    jpfreedomsync: {
      name: 'JP Freedom sync API',
      source: './services/sync-api/src/index.js',
      env: {
        SYNC_API_TOKEN: process.env.SYNC_API_TOKEN,
      },
    },
  },
});
