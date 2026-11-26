// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { CITAONICA, PRIJAVLJENA, TERETANA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const BEZ_EKRANA = { '/api/organizations/o1/screens': () => json([]) }
const RESURSI = {
	'/api/organizations/o1/resources?size=100': () =>
		json({
			content: [{ id: 'r1', organizationId: 'o1', providerId: 'u1', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity: 1, priceCents: 0, active: true, hasImage: false }],
			page: 0,
			size: 100,
			totalElements: 1,
			totalPages: 1,
		}),
}

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
		...BEZ_EKRANA,
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
		...BEZ_EKRANA,
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

test('admin napravi ekran za jedan resurs, otvori ga i ugasi', async () => {
	const korisnik = userEvent.setup()
	const ekrani = []
	const fetch = backend({
		...PRIJAVLJENA,
		...RESURSI,
		'GET /api/organizations/o1/screens': () => json(ekrani),
		'POST /api/organizations/o1/screens': (t) => {
			const ekran = { id: 's1', resourceId: t.resourceId, resourceName: 'Sto 4, tihi dio', url: 'http://localhost:5173/ekran/tok-s1', createdAt: '2026-11-26T10:00:00Z' }
			ekrani.push(ekran)
			return json(ekran, 201)
		},
		'DELETE /api/organizations/o1/screens/s1': () => {
			ekrani.length = 0
			return new Response(null, { status: 204 })
		},
	})
	otvori('/o/citaonica-pmf/postavke')

	expect(await screen.findByText('Još nema ekrana.')).toBeTruthy()
	await korisnik.selectOptions(screen.getByLabelText('Šta ekran pokazuje'), 'Samo Sto 4, tihi dio')
	await korisnik.click(screen.getByRole('button', { name: 'Napravi ekran' }))

	expect(tijelo(fetch, 'POST /api/organizations/o1/screens')).toEqual({ resourceId: 'r1' })
	const otvori_ = await screen.findByRole('link', { name: 'Otvori ekran' })
	expect(otvori_.getAttribute('href')).toBe('http://localhost:5173/ekran/tok-s1')
	expect(screen.getByLabelText('Link ekrana: Sto 4, tihi dio').value).toBe('http://localhost:5173/ekran/tok-s1')
	// Isto mjesto već ima ekran: dugme kaže da se pravi novi link
	expect(screen.getByRole('button', { name: 'Napravi novi link' })).toBeTruthy()

	await korisnik.click(screen.getByRole('button', { name: 'Ugasi' }))
	await korisnik.click(within(screen.getByRole('group', { name: 'Gašenje ekrana: Sto 4, tihi dio' })).getByRole('button', { name: 'Ugasi ekran' }))

	expect(await screen.findByText('Još nema ekrana.')).toBeTruthy()
})
