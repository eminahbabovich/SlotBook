// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import App from './App.jsx'
import { postaviToken } from './api/apiFetch.js'

const problem = (status, tijelo) =>
	new Response(JSON.stringify({ status, ...tijelo }), { status, headers: { 'Content-Type': 'application/problem+json' } })
const json = (tijelo) => new Response(JSON.stringify(tijelo), { status: 200, headers: { 'Content-Type': 'application/json' } })

const HANA = { id: 'u1', email: 'hana@primjer.ba', displayName: 'Hana Šehić', emailVerified: false }
const BEZ_SESIJE = () => problem(401, { title: 'Sesija je istekla', detail: 'Sesija je istekla ili je prekinuta. Prijavi se ponovo.' })
const TOKEN = () => json({ accessToken: 'tok', tokenType: 'Bearer', expiresIn: 900 })

// Odgovori po putanji. Funkcija dobije tijelo zahtjeva, da test može provjeriti šta je poslano.
function backend(odgovori) {
	const fetch = vi.fn(async (putanja, opcije) => {
		const odgovor = odgovori[putanja]
		if (odgovor === undefined) throw new Error(`test nema odgovor za ${putanja}`)
		return odgovor(opcije.body === undefined ? undefined : JSON.parse(opcije.body))
	})
	vi.stubGlobal('fetch', fetch)
	return fetch
}

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
	test('bez sesije se prikaže prijava, a poslije prijave nalog s imenom', async () => {
		const fetch = backend({ '/api/auth/refresh': BEZ_SESIJE, '/api/auth/login': TOKEN, '/api/me': () => json(HANA) })
		render(<App />)

		await prijaviSe(userEvent.setup())

		expect(await screen.findByText('Hana Šehić')).toBeTruthy()
		expect(screen.getByText(/Email još nije potvrđen/)).toBeTruthy()
		const login = fetch.mock.calls.find(([p]) => p === '/api/auth/login')
		expect(JSON.parse(login[1].body)).toEqual({ email: 'hana@primjer.ba', password: 'čitaonica1' })
	})

	test('važeći refresh kolačić pri otvaranju vodi pravo na nalog, bez forme', async () => {
		backend({ '/api/auth/refresh': TOKEN, '/api/me': () => json(HANA) })
		render(<App />)

		expect(await screen.findByText('Hana Šehić')).toBeTruthy()
		expect(screen.queryByLabelText('Lozinka')).toBeNull()
	})

	test('pogrešna lozinka prikaže poruku backenda, a dugme se opet može kliknuti', async () => {
		backend({
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/login': () => problem(401, { detail: 'Email ili lozinka nisu tačni. Provjeri ih i pokušaj ponovo.' }),
		})
		render(<App />)

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
		render(<App />)
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
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/login': () => new Promise((resolve) => (pusti = () => resolve(TOKEN()))),
			'/api/me': () => json(HANA),
		})
		render(<App />)

		await prijaviSe(userEvent.setup())

		const dugme = screen.getByRole('button', { name: 'Prijava u toku' })
		expect(dugme.disabled).toBe(true)
		pusti()
		expect(await screen.findByText('Hana Šehić')).toBeTruthy()
	})

	test('kad server ne odgovara, poruka to kaže umjesto opće greške', async () => {
		backend({ '/api/auth/refresh': BEZ_SESIJE, '/api/auth/login': () => new Response('Bad Gateway', { status: 502 }) })
		render(<App />)

		await prijaviSe(userEvent.setup())

		expect((await screen.findByRole('alert')).textContent).toMatch(/server ne odgovara/)
	})
})

describe('registracija', () => {
	test('zauzet email prikaže poruku backenda, a uspješna registracija prijavi korisnika', async () => {
		let zauzet = true
		const fetch = backend({
			'/api/auth/refresh': BEZ_SESIJE,
			'/api/auth/register': () =>
				zauzet
					? problem(409, { detail: 'Nalog s ovim emailom već postoji. Prijavi se ili koristi drugi email.' })
					: json(HANA),
			'/api/auth/login': TOKEN,
			'/api/me': () => json(HANA),
		})
		render(<App />)
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Napravi nalog' }))
		await korisnik.type(screen.getByLabelText('Ime koje drugi vide'), 'Hana Šehić')
		await korisnik.type(screen.getByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica1')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi nalog' }))
		expect((await screen.findByRole('alert')).textContent).toMatch(/već postoji/)

		zauzet = false
		await korisnik.click(screen.getByRole('button', { name: 'Napravi nalog' }))

		expect(await screen.findByText('Hana Šehić')).toBeTruthy()
		const registracija = fetch.mock.calls.findLast(([p]) => p === '/api/auth/register')
		expect(JSON.parse(registracija[1].body)).toEqual({ email: 'hana@primjer.ba', password: 'čitaonica1', displayName: 'Hana Šehić' })
	})
})

describe('sesija', () => {
	test('kad server ne odgovara pri otvaranju, piše to i nudi ponovni pokušaj', async () => {
		let radi = false
		backend({ '/api/auth/refresh': () => (radi ? TOKEN() : new Response('', { status: 502 })), '/api/me': () => json(HANA) })
		render(<App />)

		expect((await screen.findByRole('alert')).textContent).toMatch(/server ne odgovara/)
		radi = true
		await userEvent.setup().click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

		expect(await screen.findByText('Hana Šehić')).toBeTruthy()
	})

	test('odjava vraća na prijavu, a neuspjela odjava ostavlja nalog i kaže zašto', async () => {
		let odjavaRadi = false
		backend({
			'/api/auth/refresh': TOKEN,
			'/api/me': () => json(HANA),
			'/api/auth/logout': () => (odjavaRadi ? new Response(null, { status: 204 }) : new Response('', { status: 502 })),
		})
		render(<App />)
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Odjavi se' }))
		expect((await screen.findByRole('alert')).textContent).toMatch(/Odjava nije uspjela/)
		expect(screen.getByText('Hana Šehić')).toBeTruthy()

		odjavaRadi = true
		await korisnik.click(screen.getByRole('button', { name: 'Odjavi se' }))
		expect(await screen.findByLabelText('Lozinka')).toBeTruthy()
	})
})
