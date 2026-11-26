// Slika za dijeljenje linka (og:image, 1200 × 630): hero u tamnoj temi, s kartom koja se sama
// odštampa poslije 2,5 s. Vite mora raditi na 5173:
//   node e2e/og-slika.mjs
import { chromium } from 'playwright'

const browser = await chromium.launch({ channel: 'msedge' })
const stranica = await browser.newPage({ viewport: { width: 1200, height: 800 }, deviceScaleFactor: 1 })
await stranica.addInitScript(() => localStorage.setItem('slotbook-tema', 'tamna'))
await stranica.goto('http://localhost:5173/')
await stranica.locator('.hero-karta .karta').waitFor({ timeout: 10_000 })
await stranica.mouse.move(0, 0)
await stranica.waitForTimeout(1200)
// Ispod zaglavlja, da stanu i naslov i cijela karta
await stranica.screenshot({ path: 'public/og.png', clip: { x: 0, y: 120, width: 1200, height: 630 } })
await browser.close()
