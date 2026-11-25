// Screenshotovi svih ekrana na 1440 i 390 px, za poređenje prije i poslije redizajna.
// Ide kroz razvojni backend (8080), Mailpit (8025) i Vite (5173), koji već moraju raditi:
//   node e2e/screenshotovi.mjs ../docs/redizajn/poslije
// Svako pokretanje pravi nove naloge i organizaciju u razvojnoj bazi, kao glavni-tok.e2e.js.
import { mkdirSync } from 'node:fs'
import { chromium, request } from 'playwright'

const API = 'http://localhost:8080'
// Za e2e Mailpit (8026): MAILPIT=http://localhost:8026 node e2e/screenshotovi.mjs ...
const MAILPIT = process.env.MAILPIT ?? 'http://localhost:8025'
const APP = 'http://localhost:5173'
const ZONA = 'Europe/Sarajevo'
const LOZINKA = 'tihi-dio-cetvrti-sprat'
const DANI = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const SIRINE = { desktop: { width: 1440, height: 900 }, mobitel: { width: 390, height: 844 } }

const izlaz = process.argv[2]
if (!izlaz) {
	console.error('Fali folder za slike, npr. node e2e/screenshotovi.mjs ../docs/redizajn/poslije')
	process.exit(1)
}
mkdirSync(izlaz, { recursive: true })

const run = Date.now().toString(36)
const api = await request.newContext()

async function posalji(metoda, putanja, { token, tijelo } = {}) {
	const odgovor = await api.fetch(API + putanja, { method: metoda, headers: token ? { Authorization: `Bearer ${token}` } : {}, data: tijelo })
	const tekst = await odgovor.text()
	if (!odgovor.ok()) throw new Error(`${metoda} ${putanja}: ${odgovor.status()} ${tekst}`)
	return tekst ? JSON.parse(tekst) : null
}

// Mail šalje NotificationJob svakih 30 s, pa se čeka do minute i po
async function tokenIzMaila(email, naslov) {
	for (let i = 0; i < 90; i++) {
		const { messages } = await (await api.get(`${MAILPIT}/api/v1/messages`)).json()
		const poruka = messages.find((m) => m.Subject.startsWith(naslov) && m.To.some((t) => t.Address === email))
		if (poruka) {
			const { Text } = await (await api.get(`${MAILPIT}/api/v1/message/${poruka.ID}`)).json()
			return Text.match(/#token=(\S+)/)[1]
		}
		await new Promise((r) => setTimeout(r, 1000))
	}
	throw new Error(`Mail "${naslov}" za ${email} nije stigao`)
}

async function noviNalog(email, ime) {
	await posalji('POST', '/api/auth/register', { tijelo: { email, password: LOZINKA, displayName: ime } })
	await posalji('POST', '/api/auth/verify-email', { tijelo: { token: await tokenIzMaila(email, 'Potvrdi email') } })
	return (await posalji('POST', '/api/auth/login', { tijelo: { email, password: LOZINKA } })).accessToken
}

const danUZoni = (ms) => new Intl.DateTimeFormat('en-CA', { timeZone: ZONA }).format(ms)
const satUZoni = (iso) => new Intl.DateTimeFormat('en-GB', { timeZone: ZONA, hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
const sutra = danUZoni(Date.now() + 864e5)

// Podaci: rezervacije su sutra, da kalendar izgleda isto bez obzira u koliko sati se pokrene
const ADMIN = `edin.${run}@primjer.ba`
const KLIJENT = `hana.${run}@primjer.ba`
const admin = await noviNalog(ADMIN, 'Edin Mujkić')
const slug = `citaonica-pmf-${run}`
const praznaSlug = `teretana-kvadrat-${run}`
const org = await posalji('POST', '/api/organizations', { token: admin, tijelo: { name: 'Čitaonica PMF', slug, timezone: ZONA } })
await posalji('POST', '/api/organizations', { token: admin, tijelo: { name: 'Teretana Kvadrat', slug: praznaSlug, timezone: ZONA } })
const noviResurs = (tijelo) => posalji('POST', `/api/organizations/${org.id}/resources`, { token: admin, tijelo: { capacity: 1, priceCents: 0, cancelNoticeHours: 0, autoConfirm: true, ...tijelo } })
const sto = await noviResurs({ name: 'Sto 4, tihi dio', description: 'Četvrti sprat, bez razgovora.', slotMinutes: 60 })
const kons = await noviResurs({ name: 'Termin konsultacija', description: 'Kabinet 214. Ponesi indeks.', slotMinutes: 30, autoConfirm: false, cancelNoticeHours: 2 })
const sala = await noviResurs({ name: 'Sala za trening', description: 'Prizemlje, ulaz iz dvorišta.', slotMinutes: 60, priceCents: 1500 })
for (const r of [sto, kons, sala]) {
	await posalji('PUT', `/api/resources/${r.id}/availability`, { token: admin, tijelo: { rules: DANI.map((dayOfWeek) => ({ dayOfWeek, start: '08:00', end: '20:00' })) } })
}
await posalji('POST', `/api/organizations/${org.id}/invites`, { token: admin, tijelo: { email: KLIJENT, role: 'CLIENT' } })
const klijent = await noviNalog(KLIJENT, 'Hana Šehić')
await posalji('POST', '/api/invites/accept', { token: klijent, tijelo: { token: await tokenIzMaila(KLIJENT, 'Pozivnica') } })

async function pocetak(resurs, sat) {
	const { slots } = await posalji('GET', `/api/resources/${resurs.id}/slots?from=${danUZoni(Date.now())}&to=${danUZoni(Date.now() + 2 * 864e5)}`, { token: admin })
	return slots.find((s) => danUZoni(new Date(s.start)) === sutra && satUZoni(s.start) === sat).start
}
const rezervisi = async (token, resurs, sat) => posalji('POST', `/api/resources/${resurs.id}/bookings`, { token, tijelo: { startTime: await pocetak(resurs, sat) } })
await rezervisi(klijent, sto, '09:00')
await rezervisi(admin, sto, '11:00')
await rezervisi(admin, sto, '12:00')
const otkazana = await rezervisi(klijent, sto, '15:00')
await posalji('POST', `/api/bookings/${otkazana.id}/cancel`, { token: klijent })
await rezervisi(klijent, kons, '10:00')
await rezervisi(admin, kons, '10:30')
await rezervisi(klijent, sala, '17:00')
const izuzetak = new Date(Date.now() + 5 * 864e5)
izuzetak.setUTCHours(7, 0, 0, 0)
await posalji('POST', `/api/resources/${sto.id}/time-off`, {
	token: admin,
	tijelo: { startsAt: izuzetak.toISOString(), endsAt: new Date(izuzetak.getTime() + 8 * 36e5).toISOString(), reason: 'Ispitni rok, čitaonica zatvorena' },
})
await api.dispose()

// Slike
const browser = await chromium.launch({ channel: 'msedge' })
const greske = new Set()
const cekaj = (stranica, ms = 1800) => stranica.waitForTimeout(ms)
const slikaj = (stranica, ime, sirina) => stranica.screenshot({ path: `${izlaz}/${ime}-${sirina}.png`, fullPage: true })

async function novaStranica(sirina) {
	const kontekst = await browser.newContext({ viewport: SIRINE[sirina], timezoneId: ZONA })
	const stranica = await kontekst.newPage()
	stranica.on('console', (p) => p.type() === 'error' && greske.add(`${sirina} ${stranica.url()}: ${p.text().slice(0, 160)}`))
	return { kontekst, stranica }
}

async function prijavi(stranica, email, lozinka = LOZINKA) {
	await stranica.goto(`${APP}/prijava`)
	await stranica.getByLabel('Email').fill(email)
	await stranica.getByLabel('Lozinka').fill(lozinka)
	await stranica.getByRole('button', { name: 'Prijavi se' }).click()
	await cekaj(stranica)
}

const naSutra = (stranica) => stranica.getByRole('button', { name: new RegExp(`, ${Number(sutra.slice(8))}\\. `) }).first().click()

for (const sirina of Object.keys(SIRINE)) {
	let { kontekst, stranica } = await novaStranica(sirina)
	for (const [ime, putanja] of [['01-prijava', '/prijava'], ['02-registracija', '/registracija'], ['03-potvrda-emaila-bez-tokena', '/potvrda-emaila'], ['04-ne-postoji', '/nema-ove-stranice']]) {
		await stranica.goto(APP + putanja)
		await cekaj(stranica)
		await slikaj(stranica, ime, sirina)
	}
	await prijavi(stranica, KLIJENT, 'pogresna-lozinka-123')
	await slikaj(stranica, '05-prijava-greska', sirina)
	await kontekst.close()

	;({ kontekst, stranica } = await novaStranica(sirina))
	await prijavi(stranica, KLIJENT)
	await slikaj(stranica, '10-klijent-organizacije', sirina)
	await stranica.goto(`${APP}/o/${slug}`)
	await cekaj(stranica)
	await slikaj(stranica, '11-klijent-organizacija', sirina)
	for (const [ime, resurs] of [['12-kalendar-sto', sto], ['14-kalendar-konsultacije', kons], ['15-kalendar-sala-ceka-uplatu', sala]]) {
		await stranica.goto(`${APP}/o/${slug}/r/${resurs.id}`)
		await cekaj(stranica)
		await naSutra(stranica)
		await cekaj(stranica)
		await slikaj(stranica, ime, sirina)
		if (resurs === sto) {
			await stranica.getByRole('button', { name: /10:00 do 11:00, slobodno$/ }).click()
			await cekaj(stranica, 800)
			await slikaj(stranica, '13-kalendar-sto-odabran', sirina)
		}
	}
	await stranica.goto(`${APP}/rezervacije`)
	await cekaj(stranica)
	await slikaj(stranica, '16-moje-rezervacije', sirina)
	await stranica.getByRole('button', { name: /QR/ }).first().click()
	await cekaj(stranica, 1000)
	await slikaj(stranica, '17-moje-rezervacije-qr', sirina)
	await stranica.goto(`${APP}/nalog`)
	await cekaj(stranica)
	await slikaj(stranica, '18-nalog', sirina)

	// Učitavanje i greška se ne mogu uhvatiti na pravom backendu, pa se odgovor za termine zadrži ili podmetne
	const termini = '**/api/resources/*/slots?*'
	await stranica.route(termini, async (zahtjev) => {
		await new Promise((r) => setTimeout(r, 4000))
		await zahtjev.continue().catch(() => {})
	})
	await stranica.goto(`${APP}/o/${slug}/r/${sto.id}`)
	await cekaj(stranica, 1200)
	await slikaj(stranica, '19-kalendar-ucitavanje', sirina)
	await stranica.unroute(termini)
	await stranica.route(termini, (zahtjev) => zahtjev.fulfill({ status: 500, contentType: 'application/problem+json', body: JSON.stringify({ title: 'Internal Server Error', status: 500 }) }))
	await stranica.goto(`${APP}/o/${slug}/r/${sto.id}`)
	await cekaj(stranica, 2500)
	await slikaj(stranica, '20-kalendar-greska', sirina)
	await kontekst.close()

	;({ kontekst, stranica } = await novaStranica(sirina))
	await prijavi(stranica, ADMIN)
	await slikaj(stranica, '30-admin-organizacije', sirina)
	for (const [ime, putanja] of [
		['31-admin-organizacija', `/o/${slug}`],
		['32-admin-prazna-organizacija', `/o/${praznaSlug}`],
		['33-clanovi', `/o/${slug}/clanovi`],
		['34-novi-resurs', `/o/${slug}/resursi/novi`],
		['35-uredi-resurs', `/o/${slug}/r/${sala.id}/uredi`],
		['36-radno-vrijeme', `/o/${slug}/r/${sto.id}/radno-vrijeme`],
		['37-statistika', `/o/${slug}/statistika`],
	]) {
		await stranica.goto(APP + putanja)
		await cekaj(stranica)
		await slikaj(stranica, ime, sirina)
	}
	await stranica.goto(`${APP}/o/${slug}/r/${sto.id}/raspored`)
	await cekaj(stranica)
	await stranica.getByRole('button', { name: 'Sljedeći dan' }).or(stranica.getByRole('link', { name: 'Sljedeći dan' })).first().click()
	await cekaj(stranica)
	await slikaj(stranica, '38-raspored', sirina)
	await kontekst.close()
}
await browser.close()

console.log(`Slike su u ${izlaz}. Greške u konzoli (401 bez prijave i podmetnuti 500 su očekivani):`)
for (const g of greske) console.log(`  ${g}`)
