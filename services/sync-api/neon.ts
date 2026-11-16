import { defineConfig } from '@neon/config/v1';

export default defineConfig({
  functions: {
    jpfreedomsync: {
      name: 'Dimky sync API',
      source: './src/index.js',
      env: {
        SYNC_API_TOKEN: process.env.SYNC_API_TOKEN,
      },
    },
  },
});
