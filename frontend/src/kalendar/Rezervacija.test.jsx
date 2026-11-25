// @vitest-environment jsdom
import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem } from '../test/lazniBackend.js'

const STO = { id: 'r1', organizationId: 'o1', providerId: 'u9', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity: 1, priceCents: 0, active: true, hasImage: false }
const SLOTS = '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

// Ponedjeljak 19.11.2035: 08, 09 i 12 slobodni, 10 tuđi. Stanje se mijenja kad rezervacija
// prođe, kao na pravom backendu.
function kalendarSaRezervacijom(posalji) {
	const slobodni = ['2035-11-19T07:00:00Z', '2035-11-19T08:00:00Z', '2035-11-19T11:00:00Z']
	const zauzeti = [{ start: '2035-11-19T09:00:00Z', myStatus: null }]
	const sat = (start) => new Date(new Date(start).getTime() + 3600000).toISOString().replace('.000', '')
	const fetch = backend({
		...PRIJAVLJENA,
		'/api/organizations/o1/resources?size=100': () => json({ content: [STO], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
		[SLOTS]: () =>
			json({
				timezone: 'Europe/Sarajevo',
				slots: slobodni.map((start) => ({ start, end: sat(start) })),
				taken: zauzeti.map((z) => ({ ...z, end: sat(z.start) })),
			}),
		'POST /api/resources/r1/bookings': async (tijelo, zaglavlja) => {
			const odgovor = await posalji(tijelo, zaglavlja)
			if (odgovor.status === 201) {
				const status = (await odgovor.clone().json()).status
				slobodni.splice(slobodni.indexOf(tijelo.startTime), 1)
				zauzeti.push({ start: tijelo.startTime, myStatus: status })
			}
			return odgovor
		},
	})
	return fetch
}

const potvrda = (start, status = 'CONFIRMED', rok = null) =>
	json({ id: 'b1', resourceId: 'r1', startTime: start, endTime: '2035-11-19T08:00:00Z', status, expiresAt: rok }, 201)
const pozivi = (fetch) => fetch.mock.calls.filter(([putanja, o]) => o.method === 'POST' && putanja === '/api/resources/r1/bookings')
const kljuc = (poziv) => poziv[1].headers['Idempotency-Key']
const red = (vrijeme) => within(screen.getByRole('list', { name: /Termini/ })).getAllByRole('listitem').find((li) => li.textContent.startsWith(vrijeme))

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

test('potvrđen termin postaje moj, a zahtjev nosi Idempotency-Key', async () => {
	const fetch = kalendarSaRezervacijom((tijelo) => potvrda(tijelo.startTime))
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ }))
	const grupa = screen.getByRole('group', { name: 'Rezervisati ponedjeljak, 19. novembar, 08:00 do 09:00?' })
	expect(document.activeElement).toBe(within(grupa).getByRole('button', { name: 'Rezerviši' }))
	await korisnik.click(within(grupa).getByRole('button', { name: 'Rezerviši' }))

	expect((await screen.findByText(/Termin je tvoj/)).textContent).toBe('Termin je tvoj: ponedjeljak, 19. novembar, 08:00 do 09:00.')
	await waitFor(() => expect(red('08:00').textContent).toMatch(/tvoje$/))
	expect(JSON.parse(pozivi(fetch)[0][1].body)).toEqual({ startTime: '2035-11-19T07:00:00Z' })
	expect(kljuc(pozivi(fetch)[0])).toMatch(UUID)
})

test('rezervacija na čekanju kaže do kada je treba potvrditi', async () => {
	kalendarSaRezervacijom((tijelo) => potvrda(tijelo.startTime, 'PENDING', '2035-11-19T10:00:00Z'))
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /12:00 do 13:00, slobodno/ }))
	await korisnik.click(screen.getByRole('button', { name: 'Rezerviši' }))

	expect((await screen.findByText(/Zahtjev je poslan/)).textContent).toBe(
		'Zahtjev je poslan, a termin ponedjeljak, 19. novembar, 12:00 do 13:00 čeka potvrdu. Rok za potvrdu: ponedjeljak, 19. novembar, 11:00.',
	)
	await waitFor(() => expect(red('12:00').textContent).toMatch(/čeka potvrdu$/))
})

// Kad mreža pukne, ne znamo da li je rezervacija stigla. Isti ključ znači da je backend
// ne napravi dvaput, nego vrati onu prvu.
test('dupli klik šalje jednu rezervaciju, a ponovni pokušaj poslije pada mreže nosi isti ključ', async () => {
	let pokusaj = 0
	let pusti
	const fetch = kalendarSaRezervacijom(async (tijelo) => {
		pokusaj += 1
		if (pokusaj === 1) {
			await new Promise((resolve) => (pusti = resolve))
			throw new TypeError('Failed to fetch')
		}
		return potvrda(tijelo.startTime)
	})
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ }))
	await korisnik.dblClick(screen.getByRole('button', { name: 'Rezerviši' }))
	expect(screen.getByRole('button', { name: 'Rezervacija se šalje' }).disabled).toBe(true)
	pusti()

	expect((await screen.findByRole('alert')).textContent).toMatch(/Nije jasno da li je rezervacija stigla/)
	await korisnik.click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

	expect(await screen.findByText(/Termin je tvoj/)).toBeTruthy()
	expect(pozivi(fetch)).toHaveLength(2)
	expect(kljuc(pozivi(fetch)[1])).toBe(kljuc(pozivi(fetch)[0]))
})

// Poslije 409 kalendar se učitava ponovo, i to namjerno čeka. Termin mora biti zauzet odmah,
// ne tek kad stigne nova lista.
test('409 odmah prikaže termin kao zauzet i nudi najbliži slobodan, s novim ključem', async () => {
	let poslije409 = false
	let pustiListu
	const fetch = kalendarSaRezervacijom((tijelo) => {
		if (tijelo.startTime === '2035-11-19T07:00:00Z') {
			poslije409 = true
			return problem(409, { title: 'Termin je zauzet', detail: 'Neko je upravo rezervisao ovaj termin. Izaberi drugi slobodan termin.' })
		}
		return potvrda(tijelo.startTime)
	})
	const pravi = fetch.getMockImplementation()
	fetch.mockImplementation(async (putanja, opcije) =>
		poslije409 && putanja === SLOTS ? new Promise((resolve) => (pustiListu = () => resolve(pravi(putanja, opcije)))) : pravi(putanja, opcije),
	)
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ }))
	await korisnik.click(screen.getByRole('button', { name: 'Rezerviši' }))

	expect((await screen.findByRole('alert')).textContent).toBe('Neko je upravo rezervisao ovaj termin. Izaberi drugi slobodan termin.')
	expect(red('08:00').textContent).toMatch(/zauzeto$/)
	expect(screen.getByRole('group', { name: 'Termin ponedjeljak, 19. novembar, 08:00 do 09:00 je zauzet.' })).toBeTruthy()
	expect(screen.queryByRole('button', { name: 'Rezerviši' })).toBeNull()
	await korisnik.click(screen.getByRole('button', { name: 'Najbliži slobodan: 09:00 do 10:00' }))
	await korisnik.click(within(screen.getByRole('group', { name: /09:00 do 10:00\?$/ })).getByRole('button', { name: 'Rezerviši' }))

	expect(await screen.findByText(/Termin je tvoj: ponedjeljak, 19. novembar, 09:00 do 10:00/)).toBeTruthy()
	expect(kljuc(pozivi(fetch)[1])).not.toBe(kljuc(pozivi(fetch)[0]))
	pustiListu?.()
})

test('Odustani i Esc zatvaraju potvrdu i vraćaju fokus na termin', async () => {
	const fetch = kalendarSaRezervacijom((tijelo) => potvrda(tijelo.startTime))
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()
	const termin = await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ })

	await korisnik.click(termin)
	await korisnik.click(screen.getByRole('button', { name: 'Odustani' }))
	expect(screen.queryByRole('group')).toBeNull()
	expect(document.activeElement).toBe(termin)

	await korisnik.keyboard('{Enter}')
	expect(screen.getByRole('group')).toBeTruthy()
	await korisnik.keyboard('{Escape}')
	expect(screen.queryByRole('group')).toBeNull()
	expect(document.activeElement).toBe(termin)
	expect(pozivi(fetch)).toHaveLength(0)
})
