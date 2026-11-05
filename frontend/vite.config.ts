import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server proxies backend routes the same way nginx does in docker (nginx.conf).
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': backend,
      '/graphql': backend,
      '/actuator': backend,
      '/subscriptions': { target: backend.replace(/^http/, 'ws'), ws: true },
    },
  },
});
