// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { BEZ_SESIJE, CITAONICA, PRIJAVLJENA, TERETANA, TOKEN, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const KONSULTACIJE = { id: 'o3', name: 'Konsultacije, prof. Đulić', slug: 'konsultacije-dulic', timezone: 'Europe/Sarajevo', role: 'CLIENT' }
const PRIHVATI = 'POST /api/invites/accept'
const LINK = '/pozivnica#token=Xk3pQ9'
const PRIHVACENA = () => json({ organizationId: 'o3', slug: 'konsultacije-dulic', role: 'CLIENT' })

// Lažni backend pamti članstvo, kao pravi: poslije prihvatanja je nova organizacija u listi.
function sPozivnicom(prihvatanje = PRIHVACENA, odgovori = {}) {
	let clan = false
	return backend({
		...PRIJAVLJENA,
		'/api/me/organizations': () => json(clan ? [CITAONICA, TERETANA, KONSULTACIJE] : [CITAONICA, TERETANA]),
		'/api/organizations/o3/resources?size=100': () => json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
		[PRIHVATI]: async () => {
			const odgovor = await prihvatanje()
			clan = odgovor.ok
			return odgovor
		},
		...odgovori,
	})
}

const NEPRIJAVLJENA = { '/api/auth/refresh': BEZ_SESIJE, '/api/auth/login': TOKEN }

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

describe('prihvatanje', () => {
	test('prijavljena osoba prihvati pozivnicu i završi na stranici nove organizacije, bez tokena u adresi', async () => {
		const fetch = sPozivnicom()
		otvori(LINK)

		expect(await screen.findByText(/hana@primjer\.ba/)).toBeTruthy()
		await userEvent.setup().click(screen.getByRole('button', { name: 'Prihvati pozivnicu' }))

		expect(await screen.findByRole('heading', { name: 'Konsultacije, prof. Đulić' })).toBeTruthy()
		expect(window.location.pathname).toBe('/o/konsultacije-dulic')
		expect(window.location.hash).toBe('')
		expect(tijelo(fetch, PRIHVATI)).toEqual({ token: 'Xk3pQ9' })
	})

	test('otvaranje linka samo po sebi ništa ne prihvata', async () => {
		const fetch = sPozivnicom()
		otvori(LINK)

		await screen.findByRole('button', { name: 'Prihvati pozivnicu' })

		expect(fetch.mock.calls.some(([p]) => p === '/api/invites/accept')).toBe(false)
	})

	// Email se potvrdi u drugom tabu (link iz maila), a u ovom adresa pozivnice ostaje zapamćena
	// do prijave. Bez state na linku "Na prijavu" bi osoba završila na listi organizacija.
	test('osoba bez naloga se registruje, potvrdi email, prijavi i vrati na pozivnicu s tokenom', async () => {
		const fetch = sPozivnicom(PRIHVACENA, { ...NEPRIJAVLJENA, '/api/auth/register': () => new Response(null, { status: 202 }) })
		otvori(LINK)
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('link', { name: 'Napravi nalog' }))
		await korisnik.type(screen.getByLabelText('Ime koje drugi vide'), 'Hana Šehić')
		await korisnik.type(screen.getByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica1')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi nalog' }))
		await korisnik.click(await screen.findByRole('link', { name: 'Na prijavu' }))
		await korisnik.type(await screen.findByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica1')
		await korisnik.click(screen.getByRole('button', { name: 'Prijavi se' }))
		await korisnik.click(await screen.findByRole('button', { name: 'Prihvati pozivnicu' }))

		expect(await screen.findByRole('heading', { name: 'Konsultacije, prof. Đulić' })).toBeTruthy()
		expect(tijelo(fetch, PRIHVATI)).toEqual({ token: 'Xk3pQ9' })
	})

	test('osoba s nalogom se prijavi i vrati na pozivnicu s tokenom', async () => {
		sPozivnicom(PRIHVACENA, NEPRIJAVLJENA)
		otvori(LINK)
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica1')
		await korisnik.click(screen.getByRole('button', { name: 'Prijavi se' }))

		expect(await screen.findByRole('button', { name: 'Prihvati pozivnicu' })).toBeTruthy()
		expect(window.location.pathname + window.location.hash).toBe(LINK)
	})
})

describe('pozivnica ne važi', () => {
	test('istekla pozivnica: poruka servera i put dalje, bez dugmeta koje opet ne bi uspjelo', async () => {
		sPozivnicom(() => problem(410, { title: 'Pozivnica ne važi', detail: 'Pozivnica je istekla. Zamoli admina organizacije da ti pošalje novu.' }))
		otvori(LINK)

		await userEvent.setup().click(await screen.findByRole('button', { name: 'Prihvati pozivnicu' }))

		expect((await screen.findByRole('alert')).textContent).toBe('Pozivnica je istekla. Zamoli admina organizacije da ti pošalje novu.')
		expect(screen.queryByRole('button', { name: 'Prihvati pozivnicu' })).toBeNull()
		expect(screen.getByRole('link', { name: 'Tvoje organizacije' })).toBeTruthy()
	})

	test('kad server ne odgovara, dugme ostaje i drugi pokušaj prolazi', async () => {
		let pokusaj = 0
		sPozivnicom(() => (++pokusaj === 1 ? problem(503, {}) : PRIHVACENA()))
		otvori(LINK)
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Prihvati pozivnicu' }))
		expect((await screen.findByRole('alert')).textContent).toMatch(/server ne odgovara/)
		await korisnik.click(screen.getByRole('button', { name: 'Prihvati pozivnicu' }))

		expect(await screen.findByRole('heading', { name: 'Konsultacije, prof. Đulić' })).toBeTruthy()
	})

	test('link bez tokena kaže da nije potpun i ne šalje ništa', async () => {
		const fetch = sPozivnicom()
		otvori('/pozivnica')

		expect(await screen.findByText(/Link nije potpun/)).toBeTruthy()
		expect(screen.queryByRole('button', { name: 'Prihvati pozivnicu' })).toBeNull()
		expect(fetch.mock.calls.some(([p]) => p === '/api/invites/accept')).toBe(false)
	})
})
