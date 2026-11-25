import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    setupFiles: ['src/test/postavka.js'],
  },
  server: {
    // Browser zove /api na istoj adresi kao frontend, a Vite to prosljeđuje backendu.
    // Tako je lokalno sve na jednom sajtu i refresh kolačić (SameSite=Strict) se šalje.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
