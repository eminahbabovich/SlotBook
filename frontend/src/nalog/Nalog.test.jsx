// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const OBRISI = 'DELETE /api/me'

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

async function obrisi(korisnik, lozinka = 'čitaonica1') {
	await korisnik.type(await screen.findByLabelText('Trenutna lozinka'), lozinka)
	await korisnik.click(screen.getByRole('button', { name: 'Obriši nalog' }))
	await korisnik.click(screen.getByRole('button', { name: 'Da, obriši nalog' }))
}

describe('nalog', () => {
	test('pokaže ime i email prijavljene osobe', async () => {
		backend(PRIJAVLJENA)
		otvori('/nalog')

		expect(await screen.findByRole('heading', { name: 'Moj nalog' })).toBeTruthy()
		const glavni = within(screen.getByRole('main'))
		expect(glavni.getByText('Hana Šehić')).toBeTruthy()
		expect(glavni.getByText('hana@primjer.ba')).toBeTruthy()
	})

	test('izvoz preda browseru fajl s današnjim datumom u imenu', async () => {
		const fetch = backend({ ...PRIJAVLJENA, '/api/me/export': () => json({ user: { email: 'hana@primjer.ba' } }) })
		const napraviAdresu = vi.fn(() => 'blob:izvoz')
		// jsdom nema createObjectURL, pa se doda samo za ovaj test
		Object.assign(URL, { createObjectURL: napraviAdresu, revokeObjectURL: vi.fn() })
		const klik = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
		otvori('/nalog')

		await userEvent.setup().click(await screen.findByRole('button', { name: 'Preuzmi moje podatke (JSON)' }))

		await vi.waitFor(() => expect(klik).toHaveBeenCalled())
		expect(klik.mock.contexts[0].download).toMatch(/^slotbook-moji-podaci-\d{4}-\d{2}-\d{2}\.json$/)
		expect(napraviAdresu.mock.calls[0][0]).toBeInstanceOf(Blob)
		expect(fetch.mock.calls.some(([p]) => p === '/api/me/export')).toBe(true)
		klik.mockRestore()
		delete URL.createObjectURL
		delete URL.revokeObjectURL
	})

	// Nepovratna radnja traži drugi klik, a fokus ide na odustajanje, da Enter ne obriše nalog
	test('brisanje traži lozinku i drugi klik, a fokus je na odustajanju', async () => {
		const fetch = backend({ ...PRIJAVLJENA, [OBRISI]: () => new Response(null, { status: 204 }) })
		otvori('/nalog')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Trenutna lozinka'), 'čitaonica1')
		await korisnik.click(screen.getByRole('button', { name: 'Obriši nalog' }))

		expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Odustani' }))
		expect(fetch.mock.calls.some(([, o]) => o.method === 'DELETE')).toBe(false)
	})

	test('poslije brisanja sesija završi, a prijava kaže da je nalog obrisan', async () => {
		const fetch = backend({ ...PRIJAVLJENA, [OBRISI]: () => new Response(null, { status: 204 }) })
		otvori('/nalog')

		await obrisi(userEvent.setup())

		expect(await screen.findByRole('heading', { name: 'Prijava' })).toBeTruthy()
		expect(screen.getByText(/Nalog je obrisan/)).toBeTruthy()
		expect(tijelo(fetch, OBRISI)).toEqual({ password: 'čitaonica1' })
	})

	test('pogrešna lozinka je greška na polju, a nalog ostaje', async () => {
		backend({
			...PRIJAVLJENA,
			[OBRISI]: () =>
				problem(400, { detail: 'Neki podaci nisu ispravni.', greske: [{ polje: 'password', poruka: 'Lozinka nije tačna.' }] }),
		})
		otvori('/nalog')

		await obrisi(userEvent.setup(), 'pogresna')

		expect(await screen.findByText('Lozinka nije tačna.')).toBeTruthy()
		expect(screen.getByLabelText('Trenutna lozinka').getAttribute('aria-invalid')).toBe('true')
		expect(screen.getByRole('heading', { name: 'Moj nalog' })).toBeTruthy()
	})

	test('409 nabroji organizacije i resurse koje treba prvo predati', async () => {
		backend({
			...PRIJAVLJENA,
			[OBRISI]: () =>
				problem(409, {
					detail: 'Prije brisanja naloga predaj ulogu admina drugom članu.',
					organizacije: ['Čitaonica PMF'],
					resursi: ['Sto br. 3', 'Sala za jogu'],
				}),
		})
		otvori('/nalog')

		await obrisi(userEvent.setup())

		expect(await screen.findByText('Prije brisanja naloga predaj ulogu admina drugom članu.')).toBeTruthy()
		expect(screen.getByRole('list', { name: 'Organizacije u kojima si jedini admin' }).textContent).toContain('Čitaonica PMF')
		expect(screen.getByRole('list', { name: 'Resursi koje vodiš' }).textContent).toMatch(/Sto br\. 3.*Sala za jogu/)
	})
})
