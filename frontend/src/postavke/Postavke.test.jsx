// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { CITAONICA, PRIJAVLJENA, TERETANA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

test('admin postavi pravilo nedolazaka, a polja se popune iz organizacije', async () => {
	const korisnik = userEvent.setup()
	const sPravilom = { ...CITAONICA, noShowRules: { limit: 3, windowDays: 30, blockDays: 7 } }
	const fetch = backend({
		...PRIJAVLJENA,
		'/api/me/organizations': () => json([sPravilom, TERETANA]),
		'PUT /api/organizations/o1/no-show-rules': (t) => json(t),
	})
	otvori('/o/citaonica-pmf/postavke')

	const broj = await screen.findByLabelText('Broj nedolazaka')
	expect(broj.value).toBe('3')
	await korisnik.clear(screen.getByLabelText('Blokada traje (dana)'))
	await korisnik.type(screen.getByLabelText('Blokada traje (dana)'), '14')
	await korisnik.click(screen.getByRole('button', { name: 'Sačuvaj pravilo' }))

	expect(await screen.findByText('Pravilo je sačuvano.')).toBeTruthy()
	expect(tijelo(fetch, 'PUT /api/organizations/o1/no-show-rules')).toEqual({ limit: 3, windowDays: 30, blockDays: 14 })
})

test('pola pravila vrati 400, a poruka stoji ispod prvog polja', async () => {
	const korisnik = userEvent.setup()
	backend({
		...PRIJAVLJENA,
		'PUT /api/organizations/o1/no-show-rules': () =>
			problem(400, { title: 'Neispravni podaci', detail: 'Neka polja nisu ispravna.', greske: [{ polje: 'limit', poruka: 'Upiši sva tri broja (nedolasci, period i blokada) ili nijedan.' }] }),
	})
	otvori('/o/citaonica-pmf/postavke')

	await korisnik.type(await screen.findByLabelText('Broj nedolazaka'), '2')
	await korisnik.click(screen.getByRole('button', { name: 'Sačuvaj pravilo' }))

	expect(await screen.findByText('Upiši sva tri broja (nedolasci, period i blokada) ili nijedan.')).toBeTruthy()
	expect(screen.getByLabelText('Broj nedolazaka').getAttribute('aria-invalid')).toBe('true')
})

test('klijent ne vidi postavke', async () => {
	backend(PRIJAVLJENA)
	otvori('/o/teretana-ilidza/postavke')
	expect(await screen.findByRole('heading', { name: 'Postavke nisu dostupne' })).toBeTruthy()
})
