// @vitest-environment jsdom
import { cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori } from '../test/lazniBackend.js'

const STO = { id: 'r1', organizationId: 'o1', providerId: 'u9', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity: 1, priceCents: 0, active: true, hasImage: false }
const SLOTS = '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26'
const OSMICA = { start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z' }

// Prvo učitavanje: 08:00 slobodno. Poslije toga ga je neko drugi rezervisao.
// Tok vraća kontroler, da test šalje poruke kad hoće, kao server.
function kalendar() {
	let ucitavanja = 0
	const veza = {}
	const fetch = backend({
		...PRIJAVLJENA,
		'/api/organizations/o1/resources?size=100': () => json({ content: [STO], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
		[SLOTS]: () =>
			json(
				++ucitavanja === 1
					? { timezone: 'Europe/Sarajevo', slots: [OSMICA], taken: [] }
					: { timezone: 'Europe/Sarajevo', slots: [], taken: [{ ...OSMICA, myStatus: null, myWaitlistId: null }] },
			),
		'GET /api/resources/r1/slots/stream': (_, zaglavlja, signal) => {
			veza.signal = signal
			veza.zaglavlja = zaglavlja
			veza.otvaranja = (veza.otvaranja ?? 0) + 1
			const tijelo = new ReadableStream({
				start(kontroler) {
					veza.posalji = (tekst) => kontroler.enqueue(new TextEncoder().encode(tekst))
					veza.zatvori = () => kontroler.close()
					signal.addEventListener('abort', () => kontroler.error(new DOMException('Prekinuto', 'AbortError')))
				},
			})
			return new Response(tijelo, { headers: { 'Content-Type': 'text/event-stream' } })
		},
	})
	return { fetch, veza, ucitavanja: () => ucitavanja }
}

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-11-19T06:00:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

test('kad server javi promjenu, termin postane zauzet bez ponovnog otvaranja stranice', async () => {
	const { veza } = kalendar()
	otvori('/o/citaonica-pmf/r/r1')
	expect(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno$/ })).toBeTruthy()
	await waitFor(() => expect(veza.posalji).toBeDefined())

	// Poruka stiže u dva komada, kao preko mreže
	veza.posalji(':spojeno\n\nevent:prom')
	veza.posalji('jena\ndata:r1\n\n')

	expect(await screen.findByRole('button', { name: /08:00 do 09:00, zauzeto$/ })).toBeTruthy()
	expect(screen.getByText('Termini su osvježeni.')).toBeTruthy()
	expect(veza.zaglavlja.Authorization).toBe('Bearer tok')
	expect(veza.zaglavlja.Accept).toBe('text/event-stream')
})

test('otvorena potvrda za termin koji je neko upravo uzeo kaže to prije klika i ne nudi Rezerviši', async () => {
	const { veza, fetch } = kalendar()
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()
	await korisnik.click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno$/ }))
	expect(screen.getByRole('button', { name: 'Rezerviši' })).toBeTruthy()
	await waitFor(() => expect(veza.posalji).toBeDefined())

	veza.posalji('event:promjena\ndata:r1\n\n')

	expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'Neko je upravo rezervisao ovaj termin. Izaberi drugi slobodan termin.')
	expect(screen.queryByRole('button', { name: 'Rezerviši' })).toBeNull()
	expect(fetch.mock.calls.some(([p, o]) => o.method === 'POST' && p.includes('/bookings'))).toBe(false)
})

test('otkucaj servera (komentar) ne učitava termine ponovo', async () => {
	const { veza, ucitavanja } = kalendar()
	otvori('/o/citaonica-pmf/r/r1')
	await screen.findByRole('button', { name: /08:00 do 09:00, slobodno$/ })
	await waitFor(() => expect(veza.posalji).toBeDefined())

	veza.posalji(':ziv\n\n:ziv\n\n')
	await new Promise((gotovo) => setTimeout(gotovo, 50))

	expect(ucitavanja()).toBe(1)
	expect(screen.getByRole('button', { name: /08:00 do 09:00, slobodno$/ })).toBeTruthy()
})

// Server zatvori vezu poslije 15 minuta (istek tokena). Dok nove nema, promjene ne stižu, pa se termini učitaju ponovo.
test('kad server zatvori vezu, kalendar se ponovo spoji i učita termine', async () => {
	const { veza, ucitavanja } = kalendar()
	otvori('/o/citaonica-pmf/r/r1')
	await screen.findByRole('button', { name: /08:00 do 09:00, slobodno$/ })
	await waitFor(() => expect(veza.zatvori).toBeDefined())

	veza.zatvori()

	await waitFor(() => expect(veza.otvaranja).toBe(2), { timeout: 3000 })
	expect(await screen.findByRole('button', { name: /08:00 do 09:00, zauzeto$/ })).toBeTruthy()
	expect(ucitavanja()).toBe(2)
})

test('kad se kalendar zatvori, veza se prekine', async () => {
	const { veza } = kalendar()
	otvori('/o/citaonica-pmf/r/r1')
	await waitFor(() => expect(veza.signal).toBeDefined())

	cleanup()

	expect(veza.signal.aborted).toBe(true)
})
