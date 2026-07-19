import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Port 3000 matches the backend's FRONTEND_URL default and CORS allowlist.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    css: false,
    restoreMocks: true,
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Keep heavy libraries in their own long-cached vendor chunks so the
        // charts bundle (recharts/d3) only loads with the Stats page, etc.
        // The 'react' chunk must stay a LEAF — react/react-dom/scheduler depend on
        // nothing outside themselves. Adding a library that pulls in other
        // node_modules (react-helmet-async pulls invariant/shallowequal) makes
        // 'react' depend on 'vendor' while 'vendor' already depends on 'react';
        // Rollup then evaluates vendor first and every `extends React.Component`
        // at module scope throws on undefined, blanking the whole app.
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined;
          }
          // react-smooth is recharts' animation lib — keep it with the charts it
          // serves so it stays out of the initial bundle (and out of vendor).
          if (
            id.includes('recharts') ||
            id.includes('/d3-') ||
            id.includes('victory') ||
            id.includes('react-smooth')
          ) {
            return 'charts';
          }
          if (id.includes('react-router') || id.includes('@remix-run')) {
            return 'router';
          }
          if (id.includes('@tanstack')) {
            return 'query';
          }
          if (id.includes('dompurify') || id.includes('marked')) {
            return 'htmldeps';
          }
          if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('/scheduler/')) {
            return 'react';
          }
          return 'vendor';
        },
      },
    },
  },
});
