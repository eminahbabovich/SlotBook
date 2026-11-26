import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Service worker je naš fajl (src/sw.js, injectManifest), a plugin mu samo upiše spisak statike i
    // napravi manifest. U dev modu ga nema, da keš ne smeta dok se radi (docs/ODLUKE.md, PWA).
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.js',
      injectRegister: 'script-defer',
      injectManifest: {
        // Sve iz builda i public/ osim og slike za dijeljenje linka, koja aplikaciji ne treba
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        globIgnores: ['og.png'],
      },
      manifest: {
        name: 'SlotBook',
        short_name: 'SlotBook',
        description: 'Rezervacija termina bez preklapanja: čitaonica, konsultacije, sala za trening.',
        lang: 'bs',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        // --dan-podloga i --noc-podloga iz docs/DIZAJN.md
        background_color: '#f4f2ee',
        theme_color: '#0d0e11',
        icons: [
          { src: '/ikone/ikona-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/ikone/ikona-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/ikone/ikona-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
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
