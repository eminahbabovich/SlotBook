// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem } from '../test/lazniBackend.js'

const NOVI = () => json({ active: true, createdAt: '2035-11-19T07:00:00Z', token: 'tajni-token' }, 201)
const POSTOJI = () => json({ active: true, createdAt: '2035-11-19T07:00:00Z', token: null })

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

async function dio() {
	otvori('/nalog')
	return within((await screen.findByRole('heading', { name: 'Kalendar na telefonu' })).closest('section'))
}

describe('pretplata na kalendar', () => {
	test('napravljen link se pokaže jednom, s adresom frontenda i webcal linkom', async () => {
		const fetch = backend({ ...PRIJAVLJENA, 'POST /api/me/calendar-feed': NOVI })
		const korisnik = userEvent.setup()
		const kalendar = await dio()

		await korisnik.click(await kalendar.findByRole('button', { name: 'Napravi link' }))

		const adresa = `${window.location.origin}/api/me/calendar.ics?token=tajni-token`
		expect((await kalendar.findByLabelText('Tvoj link za kalendar')).value).toBe(adresa)
		expect(kalendar.getByRole('link', { name: 'Otvori u aplikaciji za kalendar' }).getAttribute('href')).toBe(
			adresa.replace(/^http:/, 'webcal:'),
		)
		expect(kalendar.getByRole('button', { name: 'Napravi novi link' })).toBeTruthy()
		expect(fetch.mock.calls.filter(([p, o]) => o.method === 'POST' && p === '/api/me/calendar-feed')).toHaveLength(1)
	})

	test('kopiranje javi da je uspjelo, a kad browser ne da, kaže da se kopira ručno', async () => {
		backend({ ...PRIJAVLJENA, 'POST /api/me/calendar-feed': NOVI })
		const pisi = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('nema dozvole'))
		// userEvent.setup() zamijeni clipboard svojim, pa se lažni postavlja poslije njega
		const korisnik = userEvent.setup()
		vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: pisi } })
		const kalendar = await dio()
		await korisnik.click(await kalendar.findByRole('button', { name: 'Napravi link' }))

		await korisnik.click(await kalendar.findByRole('button', { name: 'Kopiraj link' }))
		expect(await kalendar.findByText('Link je kopiran.')).toBeTruthy()
		expect(pisi).toHaveBeenCalledWith(`${window.location.origin}/api/me/calendar.ics?token=tajni-token`)

		await korisnik.click(kalendar.getByRole('button', { name: 'Kopiraj link' }))
		expect(await kalendar.findByText(/Kopiranje nije uspjelo/)).toBeTruthy()
	})

	test('postojeći link se ne prikazuje ponovo, a ugašen link vraća dugme za pravljenje', async () => {
		const fetch = backend({ ...PRIJAVLJENA, 'GET /api/me/calendar-feed': POSTOJI, 'DELETE /api/me/calendar-feed': () => new Response(null, { status: 204 }) })
		const korisnik = userEvent.setup()
		const kalendar = await dio()

		expect(await kalendar.findByText(/Ne možemo ga ponovo prikazati/)).toBeTruthy()
		expect(kalendar.queryByLabelText('Tvoj link za kalendar')).toBeNull()

		await korisnik.click(kalendar.getByRole('button', { name: 'Ugasi link' }))

		expect(await kalendar.findByRole('button', { name: 'Napravi link' })).toBeTruthy()
		expect(kalendar.queryByRole('button', { name: 'Ugasi link' })).toBeNull()
		expect(fetch.mock.calls.some(([p, o]) => o.method === 'DELETE' && p === '/api/me/calendar-feed')).toBe(true)
	})

	test('greška učitavanja ima poruku i ponovni pokušaj, bez dugmeta za pravljenje', async () => {
		let pokusaj = 0
		backend({
			...PRIJAVLJENA,
			'GET /api/me/calendar-feed': () => (++pokusaj === 1 ? problem(500, {}) : POSTOJI()),
		})
		const korisnik = userEvent.setup()
		const kalendar = await dio()

		expect((await kalendar.findByRole('alert')).textContent).toMatch(/Link za kalendar se nije učitao/)
		expect(kalendar.queryByRole('button', { name: 'Napravi link' })).toBeNull()

		await korisnik.click(kalendar.getByRole('button', { name: 'Pokušaj ponovo' }))
		expect(await kalendar.findByRole('button', { name: 'Napravi novi link' })).toBeTruthy()
	})
})
