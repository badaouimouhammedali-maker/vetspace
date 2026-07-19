import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Port 3000 matches the backend's FRONTEND_URL default and CORS allowlist.
export default defineConfig({
  plugins: [react()],
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
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined;
          }
          if (id.includes('recharts') || id.includes('/d3-') || id.includes('victory')) {
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
          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('/scheduler/') ||
            id.includes('react-helmet')
          ) {
            return 'react';
          }
          return 'vendor';
        },
      },
    },
  },
});
