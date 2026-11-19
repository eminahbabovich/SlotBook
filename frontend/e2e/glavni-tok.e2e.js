import { expect, test } from '@playwright/test'

// Backend i Mailpit direktno, bez Vite proxyja: ovo je priprema podataka, a ne ono što se testira
const API = 'http://localhost:8080'
const MAILPIT = 'http://localhost:8026'
const ZONA = 'Europe/Sarajevo'
const LOZINKA = 'tihi-dio-cetvrti-sprat'
const DANI = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

// Baza ostane ako se test prekine, pa svako pokretanje pravi svoje naloge i svoju organizaciju
const run = Date.now().toString(36)
const ADMIN = `edin.${run}@primjer.ba`
const KLIJENT = `hana.${run}@primjer.ba`
const ORGANIZACIJA = `Čitaonica PMF ${run}`
const RESURS = 'Sto 4, tihi dio'

const danUZoni = (trenutak) => new Intl.DateTimeFormat('en-CA', { timeZone: ZONA }).format(trenutak)
const satUZoni = (trenutak) => new Intl.DateTimeFormat('en-GB', { timeZone: ZONA, hour: '2-digit', minute: '2-digit' }).format(trenutak)

let api
let resursId
let slug

async function posalji(metoda, putanja, { token, tijelo } = {}) {
	const odgovor = await api.fetch(`${API}${putanja}`, {
		method: metoda,
		headers: token ? { Authorization: `Bearer ${token}` } : {},
		data: tijelo,
	})
	if (!odgovor.ok()) {
		throw new Error(`${metoda} ${putanja}: ${odgovor.status()} ${await odgovor.text()}`)
	}
	return odgovor.status() === 204 ? null : odgovor.json()
}

async function noviNalog(email, ime) {
	await posalji('POST', '/api/auth/register', { tijelo: { email, password: LOZINKA, displayName: ime } })
	return (await posalji('POST', '/api/auth/login', { tijelo: { email, password: LOZINKA } })).accessToken
}

// Token pozivnice ide samo mailom, a mail šalje NotificationJob svakih 30 s
async function tokenPozivnice(email) {
	let poruka
	await expect
		.poll(
			async () => {
				const { messages } = await (await api.get(`${MAILPIT}/api/v1/messages`)).json()
				poruka = messages.find((m) => m.Subject.startsWith('Pozivnica') && m.To.some((t) => t.Address === email))
				return poruka !== undefined
			},
			{ timeout: 60_000, intervals: [1000] },
		)
		.toBe(true)
	const { Text } = await (await api.get(`${MAILPIT}/api/v1/message/${poruka.ID}`)).json()
	return Text.match(/#token=(\S+)/)[1]
}

// Radno vrijeme je svaki dan osim današnjeg. Kalendar se otvori na prazan dan, a prvi slobodan
// je sutra od 08:00, pa test ne zavisi od toga u koliko sati se pokrene.
test.beforeAll(async ({ playwright }) => {
	api = await playwright.request.newContext()
	const admin = await noviNalog(ADMIN, 'Edin Mujkić')
	slug = `citaonica-pmf-${run}`
	const org = await posalji('POST', '/api/organizations', { token: admin, tijelo: { name: ORGANIZACIJA, slug, timezone: ZONA } })
	const resurs = await posalji('POST', `/api/organizations/${org.id}/resources`, {
		token: admin,
		tijelo: { name: RESURS, description: 'Četvrti sprat, bez razgovora.', slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0 },
	})
	resursId = resurs.id
	const danas = new Intl.DateTimeFormat('en-US', { timeZone: ZONA, weekday: 'long' }).format(new Date()).toUpperCase()
	await posalji('PUT', `/api/resources/${resursId}/availability`, {
		token: admin,
		tijelo: { rules: DANI.filter((d) => d !== danas).map((dayOfWeek) => ({ dayOfWeek, start: '08:00', end: '16:00' })) },
	})
	await posalji('POST', `/api/organizations/${org.id}/invites`, { token: admin, tijelo: { email: KLIJENT, role: 'CLIENT' } })
	const klijent = await noviNalog(KLIJENT, 'Hana Šehić')
	await posalji('POST', '/api/invites/accept', { token: klijent, tijelo: { token: await tokenPozivnice(KLIJENT) } })
})

test.afterAll(async () => {
	await api?.dispose()
})

async function prijaviKlijenta(page) {
	await page.goto('/prijava')
	await page.getByLabel('Email').fill(KLIJENT)
	await page.getByLabel('Lozinka').fill(LOZINKA)
	await page.getByRole('button', { name: 'Prijavi se' }).click()
	await page.getByRole('link', { name: new RegExp(ORGANIZACIJA) }).click()
	await page.getByRole('link', { name: new RegExp(RESURS) }).click()
	await expect(page.getByText('Ovaj dan nema termina.')).toBeVisible()
	await page.getByRole('button', { name: /^Prvi slobodan:/ }).click()
}

const termin = (page, vrijeme, stanje) => page.getByRole('button', { name: new RegExp(`${vrijeme}, ${stanje}$`) })

test('klijent se prijavi, rezerviše termin i otkaže ga, pa je termin opet slobodan', async ({ page }) => {
	await prijaviKlijenta(page)
	// Browser je u New Yorku (playwright.config.js), a sati ispod su po Sarajevu
	await expect(page.getByText(`Vremena su po zoni ${ZONA}, a tvoj uređaj je u drugoj zoni.`)).toBeVisible()

	await termin(page, '08:00 do 09:00', 'slobodno').click()
	await page.getByRole('button', { name: 'Rezerviši' }).click()
	await expect(page.getByText(/^Termin je tvoj: .*08:00 do 09:00\.$/)).toBeVisible()

	await page.getByRole('link', { name: 'Moje rezervacije' }).click()
	const nadolazece = page.getByRole('list', { name: 'Nadolazeće' })
	await expect(nadolazece.getByRole('listitem')).toHaveCount(1)
	await expect(nadolazece.getByRole('listitem')).toContainText(`08:00 do 09:00${RESURS} · ${ORGANIZACIJA}potvrđena`)
	await nadolazece.getByRole('button', { name: 'Otkaži' }).click()
	await page.getByRole('button', { name: 'Otkaži rezervaciju' }).click()

	await expect(page.getByRole('list', { name: 'Prošle i otkazane' }).getByRole('listitem')).toContainText('otkazana')
	await expect(page.getByRole('list', { name: 'Nadolazeće' })).toHaveCount(0)

	// Otkazana rezervacija ne drži termin ni u bazi: kalendar ga opet nudi kao slobodan
	await page.goto(`/o/${slug}/r/${resursId}`)
	await page.getByRole('button', { name: /^Prvi slobodan:/ }).click()
	await expect(termin(page, '08:00 do 09:00', 'slobodno')).toBeVisible()
})

test('kad neko drugi uzme termin prije potvrde, klijent dobije poruku i najbliži slobodan termin', async ({ page }) => {
	await prijaviKlijenta(page)
	await termin(page, '11:00 do 12:00', 'slobodno').click()

	// Admin rezerviše isti termin dok klijent još gleda potvrdu
	const admin = (await posalji('POST', '/api/auth/login', { tijelo: { email: ADMIN, password: LOZINKA } })).accessToken
	const sutra = danUZoni(Date.now() + 24 * 3600_000)
	const { slots } = await posalji('GET', `/api/resources/${resursId}/slots?from=${danUZoni(Date.now())}&to=${danUZoni(Date.now() + 8 * 24 * 3600_000)}`, { token: admin })
	const jedanaest = slots.find((s) => danUZoni(new Date(s.start)) === sutra && satUZoni(new Date(s.start)) === '11:00')
	await posalji('POST', `/api/resources/${resursId}/bookings`, { token: admin, tijelo: { startTime: jedanaest.start } })

	await page.getByRole('button', { name: 'Rezerviši' }).click()

	await expect(page.getByRole('alert')).toHaveText('Neko je upravo rezervisao ovaj termin. Izaberi drugi slobodan termin.')
	// Zauzet termin nije dugme, nego red s oznakom
	await expect(page.getByRole('listitem').filter({ hasText: '11:00–12:00' })).toContainText('zauzeto')
	await page.getByRole('button', { name: 'Najbliži slobodan: 12:00 do 13:00' }).click()
	await page.getByRole('button', { name: 'Rezerviši' }).click()
	await expect(page.getByText(/^Termin je tvoj: .*12:00 do 13:00\.$/)).toBeVisible()
})
