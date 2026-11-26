// @vitest-environment jsdom
import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { BEZ_SESIJE, backend, json, otvori, problem } from '../test/lazniBackend.js'

// Utorak 17.3.2036, 09:30 po Sarajevu (08:30 UTC). Sto 4 je sada zauzet, sala ima dva od šest mjesta.
const EKRAN = {
	organizationName: 'Čitaonica PMF',
	timezone: 'Europe/Sarajevo',
	date: '2036-03-17',
	now: '2036-03-17T08:30:00Z',
	inviteUrl: 'https://slotbook.example/pridruzi/abc',
	resources: [
		{
			id: 'r1',
			name: 'Sto 4, tihi dio',
			capacity: 1,
			slots: [
				{ start: '2036-03-17T07:00:00Z', end: '2036-03-17T08:00:00Z', seatsLeft: 1 },
				{ start: '2036-03-17T08:00:00Z', end: '2036-03-17T09:00:00Z', seatsLeft: 0 },
				{ start: '2036-03-17T09:00:00Z', end: '2036-03-17T10:00:00Z', seatsLeft: 1 },
				{ start: '2036-03-17T10:00:00Z', end: '2036-03-17T11:00:00Z', seatsLeft: 0 },
			],
		},
		{ id: 'r2', name: 'Sala za jogu', capacity: 6, slots: [{ start: '2036-03-17T09:00:00Z', end: '2036-03-17T10:00:00Z', seatsLeft: 2 }] },
	],
}
const ADRESA = '/api/screens/tajni-token'

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2036-03-17T08:30:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
	delete document.documentElement.dataset.tema
})

const bezPrijave = { '/api/auth/refresh': BEZ_SESIJE }

test('ekran bez prijave pokazuje sat, termin u toku i sljedeći po resursu, i QR pozivnog linka', async () => {
	backend({ ...bezPrijave, [ADRESA]: () => json(EKRAN) })
	otvori('/ekran/tajni-token')

	expect(await screen.findByRole('heading', { name: 'Čitaonica PMF' })).toBeTruthy()
	expect(screen.getByLabelText('Sada je 09:30')).toBeTruthy()
	const sto = within(screen.getAllByRole('listitem').find((li) => li.querySelector('h2')?.textContent === 'Sto 4, tihi dio'))
	expect(sto.getByText('09:00–10:00, zauzeto')).toBeTruthy()
	expect(sto.getByText('10:00–11:00, slobodno')).toBeTruthy()
	expect(within(screen.getByRole('list', { name: 'Ostatak dana: Sto 4, tihi dio' })).getByRole('listitem').textContent).toBe('11:00puno')
	expect(screen.getByText('10:00–11:00, slobodno, 2 od 6 mjesta')).toBeTruthy()
	expect(screen.getByRole('img', { name: 'QR kod pozivnog linka' })).toBeTruthy()
	// Ekran je cijeli prozor i uvijek taman
	expect(screen.queryByRole('link', { name: /SlotBook/ })).toBeNull()
	expect(document.documentElement.dataset.tema).toBe('tamna')
})

test('ugašen ekran kaže da je ugašen i gdje je novi link', async () => {
	backend({
		...bezPrijave,
		[ADRESA]: () => problem(404, { title: 'Ekran ne postoji', detail: 'Ovaj ekran je ugašen ili link nije tačan.' }),
		[`${ADRESA}/stream`]: () => problem(404, { title: 'Ekran ne postoji' }),
	})
	otvori('/ekran/tajni-token')

	expect(await screen.findByRole('heading', { name: 'Ovaj ekran je ugašen' })).toBeTruthy()
	expect(screen.getByText(/Novi link je u postavkama organizacije/)).toBeTruthy()
})

test('signal s toka ponovo učita ekran', async () => {
	let poziva = 0
	let tokEkrana = null
	const fetch = backend({
		...bezPrijave,
		[ADRESA]: () => {
			poziva++
			return json(EKRAN)
		},
		// Tok ostaje otvoren, a promjenu test pošalje tek kad je ekran prikazan
		[`${ADRESA}/stream`]: (_t, _z, signal) =>
			new Response(
				new ReadableStream({
					start(k) {
						tokEkrana = k
						k.enqueue(new TextEncoder().encode(':spojeno\n\n'))
						signal?.addEventListener('abort', () => k.error(new DOMException('Prekinuto', 'AbortError')))
					},
				}),
				{ status: 200, headers: { 'Content-Type': 'text/event-stream' } },
			),
	})
	otvori('/ekran/tajni-token')

	await screen.findByRole('heading', { name: 'Čitaonica PMF' })
	await waitFor(() => expect(tokEkrana).not.toBeNull())
	expect(poziva).toBe(1)
	tokEkrana.enqueue(new TextEncoder().encode('event:promjena\ndata:r1\n\n'))
	await waitFor(() => expect(poziva).toBe(2))
	expect(fetch.mock.calls.every(([, o]) => !o.headers?.Authorization)).toBe(true)
	expect(screen.getByText('Uživo')).toBeTruthy()
})
