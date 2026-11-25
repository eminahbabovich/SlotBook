// Screenshotovi landinga na 1440 i 390 px, u svijetloj i tamnoj temi, prije i poslije klika.
// Vite (5173) mora raditi, backend s profilom demo je poželjan (bez njega nema demo računa):
//   node e2e/landing-slike.mjs ../docs/redizajn/redizajn-2
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const izlaz = process.argv[2]
if (!izlaz) {
	console.error('Fali folder za slike')
	process.exit(1)
}
mkdirSync(izlaz, { recursive: true })

const SIRINE = { desktop: { width: 1440, height: 900 }, mobitel: { width: 390, height: 844 } }
const browser = await chromium.launch({ channel: 'msedge' })

for (const [ime, viewport] of Object.entries(SIRINE)) {
	for (const tema of ['light', 'dark']) {
		const stranica = await browser.newPage({ viewport, colorScheme: tema, deviceScaleFactor: 1 })
		await stranica.goto('http://localhost:5173/')
		await stranica.getByRole('heading', { level: 1 }).waitFor()
		await stranica.waitForTimeout(1200)
		const t = tema === 'light' ? 'svijetla' : 'tamna'
		await stranica.screenshot({ path: `${izlaz}/landing-hero-${ime}-${t}.png` })
		if (ime === 'desktop') await stranica.mouse.move(300, 330)
		await stranica.getByRole('button', { name: /^15:00/ }).click()
		await stranica.waitForTimeout(900)
		if (ime === 'desktop') await stranica.mouse.move(1100, 700)
		await stranica.waitForTimeout(300)
		await stranica.screenshot({ path: `${izlaz}/landing-hero-uzet-${ime}-${t}.png` })
		await stranica.getByRole('button', { name: 'Pošalji 20 zahtjeva odjednom' }).click()
		await stranica.waitForTimeout(1600)
		await stranica.screenshot({ path: `${izlaz}/landing-cijeli-${ime}-${t}.png`, fullPage: true })
		await stranica.close()
	}
}
await browser.close()
