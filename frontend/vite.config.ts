import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';

// The security headers nginx sends (nginx.conf add_header lines), so `vite preview` behaves like production (P4 T8, ZAP).
const securityHeaders = Object.fromEntries(
  [...readFileSync(new URL('./nginx.conf', import.meta.url), 'utf8').matchAll(/^\s*add_header\s+(\S+)\s+"([^"]*)"\s+always;/gm)].map((m) => [m[1], m[2]]),
);

// The dev server proxies backend routes the same way nginx does in docker (nginx.conf).
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  // Some of Sirius Web's dependencies read process.env (Node); its own build provides it the same way (P4 T3).
  define: { 'process.env': '{}', global: 'globalThis' },
  preview: { headers: securityHeaders },
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
