// Screenshotovi ekrana za prijavljene na 375 i 1280 px, u tamnoj i svijetloj temi. Prijava ide preko
// demo računa (Hana), pa backend mora raditi s profilom demo, a Vite na 5173:
//   node e2e/pregled-slike.mjs ../docs/redizajn/redizajn-4
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const APP = 'http://localhost:5173'
const SIRINE = { mobitel: { width: 375, height: 812 }, desktop: { width: 1280, height: 860 } }

const izlaz = process.argv[2]
if (!izlaz) {
	console.error('Fali folder za slike')
	process.exit(1)
}
mkdirSync(izlaz, { recursive: true })

const browser = await chromium.launch({ channel: 'msedge' })

async function slikaj(stranica, ime) {
	await stranica.waitForTimeout(900)
	await stranica.screenshot({ path: `${izlaz}/${ime}.png` })
}

for (const [sirina, viewport] of Object.entries(SIRINE)) {
	for (const tema of ['tamna', 'svijetla']) {
		const kontekst = await browser.newContext({ viewport, timezoneId: 'Europe/Sarajevo' })
		await kontekst.addInitScript((t) => localStorage.setItem('slotbook-tema', t), tema)
		const stranica = await kontekst.newPage()
		const ime = (ekran) => `${ekran}-${sirina}-${tema}`

		await stranica.goto(`${APP}/`)
		await stranica.getByRole('heading', { level: 1 }).waitFor()
		// Karta primjera se u heru sama odštampa poslije 2,5 s
		await stranica.locator('.hero-karta .karta').waitFor()
		await slikaj(stranica, ime('landing'))

		await stranica.goto(`${APP}/prijava`)
		await stranica.getByRole('button', { name: /Hana/ }).click()
		await stranica.waitForURL(`${APP}/`)
		await stranica.getByRole('heading', { level: 1 }).waitFor()
		await slikaj(stranica, ime('organizacije'))

		await stranica.locator('main a[href^="/o/"]').first().click()
		await stranica.waitForURL(/\/o\/[^/]+$/)
		await slikaj(stranica, ime('organizacija'))

		await stranica.locator('main a[href*="/r/"]').first().click()
		await stranica.waitForURL(/\/r\/[^/]+$/)
		await stranica.locator('.termin').first().waitFor()
		await slikaj(stranica, ime('resurs'))

		const slobodan = stranica.locator('.termin-slobodno button').first()
		if (await slobodan.count()) {
			await slobodan.click()
			await slikaj(stranica, ime('resurs-izabran'))
			await stranica.keyboard.press('Escape')
		}

		await stranica.goto(`${APP}/rezervacije`)
		await stranica.getByRole('heading', { level: 1 }).waitFor()
		await slikaj(stranica, ime('rezervacije'))

		await kontekst.close()
	}
}
await browser.close()
