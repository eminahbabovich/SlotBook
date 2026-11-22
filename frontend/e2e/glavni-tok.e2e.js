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
	// 204 i 202 (registracija) nemaju tijelo
	const tekst = await odgovor.text()
	return tekst ? JSON.parse(tekst) : null
}

// Prijava radi tek kad je email potvrđen, pa se link čeka u Mailpitu kao i pozivnica
async function noviNalog(email, ime) {
	await posalji('POST', '/api/auth/register', { tijelo: { email, password: LOZINKA, displayName: ime } })
	await posalji('POST', '/api/auth/verify-email', { tijelo: { token: await tokenIzMaila(email, 'Potvrdi email') } })
	return (await posalji('POST', '/api/auth/login', { tijelo: { email, password: LOZINKA } })).accessToken
}

// Tokeni idu samo mailom, a mail šalje NotificationJob svakih 30 s
async function tokenIzMaila(email, naslov) {
	let poruka
	await expect
		.poll(
			async () => {
				const { messages } = await (await api.get(`${MAILPIT}/api/v1/messages`)).json()
				poruka = messages.find((m) => m.Subject.startsWith(naslov) && m.To.some((t) => t.Address === email))
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
	// Tri maila (dvije potvrde i pozivnica), a NotificationJob šalje svakih 30 s
	test.setTimeout(180_000)
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
	await posalji('POST', '/api/invites/accept', { token: klijent, tijelo: { token: await tokenIzMaila(KLIJENT, 'Pozivnica') } })
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
	// Bez živog kalendara, kao kad SSE veza pukne. S njim bi termin postao zauzet prije klika (test ispod),
	// a ovdje se dokazuje 409, koji ostaje zadnja zaštita.
	await page.route('**/slots/stream', (zahtjev) => zahtjev.abort())
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
	// Zauzet termin se više ne rezerviše, nego se otvara lista čekanja
	await expect(termin(page, '11:00 do 12:00', 'zauzeto')).toBeVisible()
	await page.getByRole('button', { name: 'Najbliži slobodan: 12:00 do 13:00' }).click()
	await page.getByRole('button', { name: 'Rezerviši' }).click()
	await expect(page.getByText(/^Termin je tvoj: .*12:00 do 13:00\.$/)).toBeVisible()
})

// Ponudu pravi BookingJob, koji radi svake minute, pa test čeka do dvije minute
test('klijent se upiše na listu čekanja, a kad admin otkaže, termin je ponuđen klijentu i ona ga rezerviše', async ({ page }) => {
	test.setTimeout(180_000)
	const admin = (await posalji('POST', '/api/auth/login', { tijelo: { email: ADMIN, password: LOZINKA } })).accessToken
	const sutra = danUZoni(Date.now() + 24 * 3600_000)
	const { slots } = await posalji('GET', `/api/resources/${resursId}/slots?from=${danUZoni(Date.now())}&to=${danUZoni(Date.now() + 8 * 24 * 3600_000)}`, { token: admin })
	const cetrnaest = slots.find((s) => danUZoni(new Date(s.start)) === sutra && satUZoni(new Date(s.start)) === '14:00')
	const adminova = await posalji('POST', `/api/resources/${resursId}/bookings`, { token: admin, tijelo: { startTime: cetrnaest.start } })

	await prijaviKlijenta(page)
	await termin(page, '14:00 do 15:00', 'zauzeto').click()
	await page.getByRole('button', { name: 'Javi mi ako se oslobodi' }).click()
	await expect(page.getByText(/^Na listi čekanja si za .*14:00 do 15:00\./)).toBeVisible()
	await expect(termin(page, '14:00 do 15:00', 'zauzeto, na listi si')).toBeVisible()

	await page.getByRole('link', { name: 'Moje rezervacije' }).click()
	const lista = page.getByRole('list', { name: 'Lista čekanja' })
	await expect(lista.getByRole('listitem')).toContainText('čekaš da se termin oslobodi')

	await posalji('POST', `/api/bookings/${adminova.id}/cancel`, { token: admin })
	await expect(async () => {
		await page.reload()
		await expect(page.getByRole('list', { name: 'Lista čekanja' }).getByRole('listitem')).toContainText('Rok za rezervaciju', { timeout: 2000 })
	}).toPass({ timeout: 120_000, intervals: [5000] })

	await page.getByRole('link', { name: 'Rezerviši u kalendaru' }).click()
	await page.getByRole('button', { name: /^Prvi slobodan:/ }).click()
	await termin(page, '14:00 do 15:00', String.raw`ponuđen tebi (danas|sutra) do \d\d:\d\d`).click()
	await expect(page.getByText(/ponuđen je tebi s liste čekanja (danas|sutra) do \d\d:\d\d/)).toBeVisible()
	await page.getByRole('button', { name: 'Rezerviši' }).click()
	await expect(page.getByText(/^Termin je tvoj: .*14:00 do 15:00\.$/)).toBeVisible()
})

test('klijent napravi link za kalendar, a kalendar kroz taj link bez prijave dobije njen termin', async ({ page, playwright }) => {
	await prijaviKlijenta(page)
	await termin(page, '09:00 do 10:00', 'slobodno').click()
	await page.getByRole('button', { name: 'Rezerviši' }).click()
	await expect(page.getByText(/^Termin je tvoj: .*09:00 do 10:00\.$/)).toBeVisible()

	await page.getByRole('link', { name: 'Hana Šehić' }).click()
	await page.getByRole('button', { name: 'Napravi link' }).click()
	const polje = page.getByLabel('Tvoj link za kalendar')
	const adresa = await polje.inputValue()
	expect(adresa).toMatch(/^http:\/\/localhost:\d+\/api\/me\/calendar\.ics\?token=[\w-]{43}$/)

	// Bez kolačića i tokena, kao Google kad povuče link. Ide kroz Vite proxy, kao na produkciji kroz frontend.
	const kalendar = await playwright.request.newContext()
	const odgovor = await kalendar.get(adresa)
	expect(odgovor.status()).toBe(200)
	expect(odgovor.headers()['content-type']).toMatch(/^text\/calendar/)
	const ics = await odgovor.text()
	expect(ics).toContain(`SUMMARY:${RESURS.replace(',', '\\,')}\r\n`)
	// 09:00 po Sarajevu je 08:00 u UTC-u zimi, a 07:00 ljeti
	expect(ics).toMatch(new RegExp(`DTSTART:${danUZoni(Date.now() + 24 * 3600_000).replaceAll('-', '')}T0[78]0000Z\r\n`))
	expect(ics).toContain('STATUS:CONFIRMED\r\n')

	// Novi link gasi stari, a korisnik ga vidi odmah
	await page.getByRole('button', { name: 'Napravi novi link' }).click()
	await expect(polje).not.toHaveValue(adresa)
	expect((await kalendar.get(adresa)).status()).toBe(404)
	await kalendar.dispose()
})

test('kad neko drugi rezerviše termin koji klijent gleda, kalendar ga pokaže zauzetim bez osvježavanja', async ({ page }) => {
	const tok = page.waitForResponse((odgovor) => odgovor.url().endsWith(`/api/resources/${resursId}/slots/stream`))
	await prijaviKlijenta(page)
	await termin(page, '15:00 do 16:00', 'slobodno').click()
	await expect(page.getByRole('button', { name: 'Rezerviši' })).toBeVisible()
	// Veza mora biti otvorena prije admina, inače signal nema kome stići
	expect((await tok).headers()['content-type']).toMatch(/^text\/event-stream/)

	const admin = (await posalji('POST', '/api/auth/login', { tijelo: { email: ADMIN, password: LOZINKA } })).accessToken
	const sutra = danUZoni(Date.now() + 24 * 3600_000)
	const { slots } = await posalji('GET', `/api/resources/${resursId}/slots?from=${danUZoni(Date.now())}&to=${danUZoni(Date.now() + 8 * 24 * 3600_000)}`, { token: admin })
	const petnaest = slots.find((s) => danUZoni(new Date(s.start)) === sutra && satUZoni(new Date(s.start)) === '15:00')
	await posalji('POST', `/api/resources/${resursId}/bookings`, { token: admin, tijelo: { startTime: petnaest.start } })

	// Bez reload-a i bez klika: potvrda sama kaže da je termin zauzet i nudi najbliži slobodan
	await expect(termin(page, '15:00 do 16:00', 'zauzeto')).toBeVisible({ timeout: 5000 })
	await expect(page.getByRole('alert')).toHaveText('Neko je upravo rezervisao ovaj termin. Izaberi drugi slobodan termin.')
	await expect(page.getByRole('button', { name: 'Rezerviši' })).toHaveCount(0)
	await expect(page.getByRole('button', { name: /^Najbliži slobodan:/ })).toBeVisible()
})

test('klijent rezerviše isti termin tri sedmice zaredom i vidi sva tri u svojim rezervacijama', async ({ page }) => {
	await prijaviKlijenta(page)
	await termin(page, '10:00 do 11:00', 'slobodno').click()
	await page.getByLabel('Ponavljanje').selectOption({ label: 'Svake sedmice, 3 puta' })

	await page.getByRole('button', { name: 'Rezerviši 3 termina' }).click()
	await expect(page.getByText(/^Termini su tvoji, svake sedmice u isto vrijeme\. Prvi: .*10:00 do 11:00\. Zadnji: .*\.$/)).toBeVisible()

	await page.getByRole('link', { name: 'Moje rezervacije' }).click()
	await expect(page.getByRole('list', { name: 'Nadolazeće' }).getByRole('listitem').filter({ hasText: '10:00 do 11:00' })).toHaveCount(3)
})


// Kameru Playwright nema, pa se link uzme iz odgovora koji je nacrtan u QR kod i otvori u
// browseru admina, kao što bi ga otvorio telefon. Admin se prvo prijavi, a kod iza # ostane u linku.
test('klijent pokaže QR kod, admin otvori link iz njega i upiše dolazak, a rezervacija je završena', async ({ page, browser }) => {
	await prijaviKlijenta(page)
	await termin(page, '13:00 do 14:00', 'slobodno').click()
	await page.getByRole('button', { name: 'Rezerviši' }).click()
	await expect(page.getByText(/^Termin je tvoj: .*13:00 do 14:00\.$/)).toBeVisible()

	await page.getByRole('link', { name: 'Moje rezervacije' }).click()
	const red = page.getByRole('list', { name: 'Nadolazeće' }).getByRole('listitem').filter({ hasText: '13:00 do 14:00' })
	const odgovor = page.waitForResponse((o) => o.url().endsWith('/check-in-link'))
	await red.getByRole('button', { name: 'QR kod za dolazak' }).click()
	await expect(red.getByRole('img', { name: `QR kod za dolazak: ${RESURS}` })).toBeVisible()
	const { url } = await (await odgovor).json()
	expect(url).toMatch(/^http:\/\/localhost:5173\/check-in\/[0-9a-f-]{36}#k=[\w-]{43}$/)

	const adminov = await browser.newPage()
	await adminov.goto(url)
	await adminov.getByLabel('Email').fill(ADMIN)
	await adminov.getByLabel('Lozinka').fill(LOZINKA)
	await adminov.getByRole('button', { name: 'Prijavi se' }).click()
	await adminov.getByRole('button', { name: 'Upiši dolazak' }).click()
	await expect(adminov.getByRole('status')).toHaveText('Dolazak je upisan, rezervacija je završena.')
	await adminov.close()

	await page.reload()
	await expect(page.getByRole('list', { name: 'Prošle i otkazane' }).getByRole('listitem').filter({ hasText: '13:00 do 14:00' })).toContainText('završena')
})
