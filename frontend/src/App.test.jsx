// @vitest-environment jsdom
import { cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from './api/apiFetch.js'
import { BEZ_SESIJE, CITAONICA, PRIJAVLJENA, TERETANA, TOKEN, backend, json, otvori, problem, tijelo } from './test/lazniBackend.js'

async function prijaviSe(korisnik, email = 'hana@primjer.ba', lozinka = 'čitaonica1') {
	await korisnik.type(await screen.findByLabelText('Email'), email)
	await korisnik.type(screen.getByLabelText('Lozinka'), lozinka)
	await korisnik.click(screen.getByRole('button', { name: 'Prijavi se' }))
}

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

describe('prijava', () => {
	test('prijava vodi na listu organizacija s ulogom, na početnu adresu', async () => {
		const fetch = backend({ ...PRIJAVLJENA, '/api/auth/refresh': BEZ_SESIJE, '/api/auth/login': TOKEN })
		otvori('/prijava')

		await prijaviSe(userEvent.setup())

		expect(await screen.findByRole('link', { name: /Čitaonica PMF/ })).toBeTruthy()
		expect(screen.getByRole('link', { name: /Teretana Ilidža/ }).textContent).toMatch(/rezerviše termine/)
		expect(screen.getByText('Hana Šehić')).toBeTruthy()
		expect(screen.getByText(/Email još nije potvrđen/)).toBeTruthy()
		expect(tijelo(fetch, 'POST /api/auth/login')).toEqual({ email: 'hana@primjer.ba', password: 'čitaonica1' })
		expect(window.location.pathname).toBe('/')
	})

	test('važeći refresh kolačić pri otvaranju vodi pravo na organizacije, bez forme', async () => {
		backend(PRIJAVLJENA)
		otvori('/prijava')

		expect(await screen.findByRole('link', { name: /Čitaonica PMF/ })).toBeTruthy()
		expect(screen.queryByLabelText('Lozinka')).toBeNull()
		expect(window.location.pathname).toBe('/')
	})

	test('pogrešna lozinka prikaže poruku backenda, a dugme se opet može kliknuti', async () => {
		backend({
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/login': () => problem(401, { detail: 'Email ili lozinka nisu tačni. Provjeri ih i pokušaj ponovo.' }),
		})
		otvori('/prijava')

		await prijaviSe(userEvent.setup())

		expect((await screen.findByRole('alert')).textContent).toBe('Email ili lozinka nisu tačni. Provjeri ih i pokušaj ponovo.')
		expect(screen.getByRole('button', { name: 'Prijavi se' }).disabled).toBe(false)
	})

	test('greška iz 400 stoji ispod svog polja i polje je označeno kao neispravno', async () => {
		backend({
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/login': () =>
				problem(400, { detail: 'Neki podaci nisu ispravni.', greske: [{ polje: 'password', poruka: 'Unesi lozinku.' }] }),
		})
		otvori('/prijava')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.click(screen.getByRole('button', { name: 'Prijavi se' }))

		const lozinka = screen.getByLabelText('Lozinka')
		await waitFor(() => expect(lozinka.getAttribute('aria-invalid')).toBe('true'))
		expect(document.getElementById(lozinka.getAttribute('aria-describedby')).textContent).toBe('Unesi lozinku.')
		expect(screen.getByLabelText('Email').getAttribute('aria-invalid')).toBe('false')
	})

	test('dok se prijava šalje, dugme je isključeno i kaže šta se dešava', async () => {
		let pusti
		backend({
			...PRIJAVLJENA,
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/login': () => new Promise((resolve) => (pusti = () => resolve(TOKEN()))),
		})
		otvori('/prijava')

		await prijaviSe(userEvent.setup())

		expect(screen.getByRole('button', { name: 'Prijava u toku' }).disabled).toBe(true)
		pusti()
		expect(await screen.findByText('Hana Šehić')).toBeTruthy()
	})

	test('kad server ne odgovara, poruka to kaže umjesto opće greške', async () => {
		backend({ '/api/auth/refresh': BEZ_SESIJE, '/api/auth/login': () => new Response('Bad Gateway', { status: 502 }) })
		otvori('/prijava')

		await prijaviSe(userEvent.setup())

		expect((await screen.findByRole('alert')).textContent).toMatch(/server ne odgovara/)
	})

	test('adresa otvorena bez prijave vodi na prijavu, a poslije prijave nazad na nju', async () => {
		backend({ ...PRIJAVLJENA, '/api/auth/refresh': BEZ_SESIJE, '/api/auth/login': TOKEN })
		otvori('/o/teretana-ilidza')

		await prijaviSe(userEvent.setup())

		expect(await screen.findByRole('heading', { name: 'Teretana Ilidža' })).toBeTruthy()
		expect(window.location.pathname).toBe('/o/teretana-ilidza')
	})
})

describe('registracija', () => {
	// Zauzet email više ne daje 409 (Faza 8, korak 5), pa je jedina greška forme neispravno polje
	test('kratka lozinka je greška na polju, a ispravljena forma vodi na inbox bez prijave', async () => {
		let kratka = true
		const fetch = backend({
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/register': () =>
				kratka
					? problem(400, { detail: 'Neki podaci nisu ispravni.', greske: [{ polje: 'password', poruka: 'Lozinka mora imati najmanje 8 znakova.' }] })
					: new Response(null, { status: 202 }),
		})
		otvori('/prijava')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('link', { name: 'Napravi nalog' }))
		await korisnik.type(screen.getByLabelText('Ime koje drugi vide'), 'Hana Šehić')
		await korisnik.type(screen.getByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi nalog' }))
		expect(await screen.findByText('Lozinka mora imati najmanje 8 znakova.')).toBeTruthy()

		kratka = false
		await korisnik.type(screen.getByLabelText('Lozinka'), '1')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi nalog' }))

		expect(await screen.findByRole('heading', { name: 'Pogledaj inbox' })).toBeTruthy()
		expect(tijelo(fetch, 'POST /api/auth/register')).toEqual({ email: 'hana@primjer.ba', password: 'čitaonica1', displayName: 'Hana Šehić' })
	})
})

describe('organizacije', () => {
	test('klik na organizaciju otvara njenu stranicu s ulogom i zonom', async () => {
		backend(PRIJAVLJENA)
		otvori('/')

		await userEvent.setup().click(await screen.findByRole('link', { name: /Čitaonica PMF/ }))

		expect(await screen.findByRole('heading', { name: 'Čitaonica PMF' })).toBeTruthy()
		expect(screen.getByText('Tvoja uloga: admin.')).toBeTruthy()
		expect(screen.getByText(/Europe\/Sarajevo/)).toBeTruthy()
		expect(window.location.pathname).toBe('/o/citaonica-pmf')
	})

	test('organizacija koje nema na listi ne otkriva da li postoji', async () => {
		backend(PRIJAVLJENA)
		otvori('/o/tudja-sala')

		expect((await screen.findByRole('heading', { level: 1 })).textContent).toBe('Organizacija nije dostupna')
		expect(screen.getByText(/ne postoji ili nisi njen član/)).toBeTruthy()
	})

	// Poslije pravljenja lista se učitava ponovo i to namjerno čeka. Dok čeka, korisnik
	// ne smije vidjeti "nije dostupna" za organizaciju koju je upravo napravio.
	test('bez organizacija se nudi nova, s adresom predloženom iz naziva', async () => {
		let napravljena = null
		let pustiListu
		const fetch = backend({
			...PRIJAVLJENA,
			'/api/me/organizations': () =>
				napravljena === null ? json([]) : new Promise((resolve) => (pustiListu = () => resolve(json([napravljena])))),
			'POST /api/organizations': (podaci) => {
				napravljena = { ...CITAONICA, name: podaci.name, slug: podaci.slug }
				return json({ id: 'o1', name: podaci.name, slug: podaci.slug, timezone: podaci.timezone }, 201)
			},
		})
		otvori('/')
		const korisnik = userEvent.setup()

		expect(await screen.findByText(/Još nisi član nijedne organizacije/)).toBeTruthy()
		await korisnik.type(screen.getByLabelText('Naziv'), 'Čitaonica PMF')
		expect(screen.getByLabelText('Adresa').value).toBe('citaonica-pmf')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi organizaciju' }))

		await waitFor(() => expect(pustiListu).toBeDefined())
		expect(screen.queryByText('Organizacija nije dostupna')).toBeNull()
		expect(screen.getByRole('button', { name: 'Organizacija se pravi' })).toBeTruthy()
		pustiListu()
		expect(await screen.findByRole('heading', { name: 'Čitaonica PMF' })).toBeTruthy()
		expect(window.location.pathname).toBe('/o/citaonica-pmf')
		expect(tijelo(fetch, 'POST /api/organizations')).toEqual({ name: 'Čitaonica PMF', slug: 'citaonica-pmf', timezone: 'Europe/Sarajevo' })
	})

	test('kad se organizacije ne učitaju, piše zašto i nudi ponovni pokušaj', async () => {
		let radi = false
		backend({ ...PRIJAVLJENA, '/api/me/organizations': () => (radi ? json([CITAONICA]) : new Response('', { status: 502 })) })
		otvori('/')

		expect((await screen.findByRole('alert', {}, { timeout: 3000 })).textContent).toMatch(/server ne odgovara/)
		radi = true
		await userEvent.setup().click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

		expect(await screen.findByRole('link', { name: /Čitaonica PMF/ })).toBeTruthy()
	})
})

// Landing je lijeno učitan dio (lazy), pa uz cijeli suite zna čekati duže od podrazumijevane
// sekunde. Duže čekanje je samo za prvi element landinga, ostalo je kao svuda.
const LANDING = { timeout: 5000 }

describe('početna za odjavljene', () => {
	test('bez sesije / pokazuje landing s prijavom, bez liste organizacija', async () => {
		backend({ '/api/auth/refresh': BEZ_SESIJE, '/api/demo': () => problem(404, {}) })
		otvori('/')

		expect(await screen.findByRole('heading', { name: 'Termin je tvoj.' }, LANDING)).toBeTruthy()
		expect(screen.getAllByRole('link', { name: /Prijav/ }).length).toBeGreaterThan(0)
		expect(screen.queryByLabelText('Lozinka')).toBeNull()
		expect(window.location.pathname).toBe('/')
	})

	test('na demu se s landinga ulazi jednim klikom, kao Hana', async () => {
		const fetch = backend({
			...PRIJAVLJENA,
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/login': TOKEN,
			'/api/demo': () => json({ lozinka: 'sebilj-na-bascarsiji', racuni: [{ email: 'klijent@slotbook.test', ime: 'Hana Šehić', opis: 'rezerviše sto' }] }),
		})
		otvori('/')

		await userEvent.setup().click(await screen.findByRole('button', { name: /Uđi kao Hana/ }, LANDING))

		expect(await screen.findByRole('link', { name: /Čitaonica PMF/ })).toBeTruthy()
		expect(tijelo(fetch, 'POST /api/auth/login')).toEqual({ email: 'klijent@slotbook.test', password: 'sebilj-na-bascarsiji' })
	})
})

describe('sesija', () => {
	test('kad server ne odgovara pri otvaranju, piše to i nudi ponovni pokušaj', async () => {
		let radi = false
		backend({ ...PRIJAVLJENA, '/api/auth/refresh': () => (radi ? TOKEN() : new Response('', { status: 502 })) })
		otvori('/o/citaonica-pmf')

		expect((await screen.findByRole('alert')).textContent).toMatch(/server ne odgovara/)
		radi = true
		await userEvent.setup().click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

		expect(await screen.findByText('Hana Šehić')).toBeTruthy()
	})

	test('neuspjela odjava ostavlja sesiju i kaže zašto, a uspješna vodi na početnu za odjavljene', async () => {
		let odjavaRadi = false
		backend({
			...PRIJAVLJENA,
			'/api/auth/logout': () => (odjavaRadi ? new Response(null, { status: 204 }) : new Response('', { status: 502 })),
		})
		otvori('/')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Odjavi se' }))
		expect((await screen.findByRole('alert')).textContent).toMatch(/Odjava nije uspjela/)
		expect(screen.getByText('Hana Šehić')).toBeTruthy()

		odjavaRadi = true
		await korisnik.click(screen.getByRole('button', { name: 'Odjavi se' }))
		expect(await screen.findByRole('heading', { name: 'Termin je tvoj.' }, LANDING)).toBeTruthy()
		expect(screen.queryByText('Hana Šehić')).toBeNull()
		expect(screen.queryByRole('link', { name: /Čitaonica PMF/ })).toBeNull()
	})

	// Lista za drugu osobu namjerno čeka. Da keš nije obrisan, za to vrijeme bi se
	// vidjela Hanina čitaonica iz keša, a ne poruka da se lista učitava. Hana se odjavljuje
	// sa stranice čitaonice, a Tarik poslije prijave mora doći na svoju listu, ne na nju.
	test('poslije odjave druga osoba ne vidi organizacije prethodne ni njenu adresu', async () => {
		let pustiTarikove
		let ko = 'hana'
		backend({
			...PRIJAVLJENA,
			'/api/auth/logout': () => new Response(null, { status: 204 }),
			'/api/auth/login': TOKEN,
			'/api/me/organizations': () =>
				ko === 'hana' ? json([CITAONICA]) : new Promise((resolve) => (pustiTarikove = () => resolve(json([TERETANA])))),
		})
		otvori('/o/citaonica-pmf')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Odjavi se' }))
		ko = 'tarik'
		await prijaviSe(korisnik, 'tarik@primjer.ba')

		expect(await screen.findByText('Učitavaju se tvoje organizacije')).toBeTruthy()
		expect(screen.queryByRole('link', { name: /Čitaonica PMF/ })).toBeNull()
		pustiTarikove()
		expect(await screen.findByRole('link', { name: /Teretana Ilidža/ })).toBeTruthy()
		expect(window.location.pathname).toBe('/')
	})
})
