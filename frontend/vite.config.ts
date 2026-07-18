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
});
