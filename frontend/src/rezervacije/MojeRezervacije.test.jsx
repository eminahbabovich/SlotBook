// @vitest-environment jsdom
import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem } from '../test/lazniBackend.js'

const rez = (id, resurs, organizacija, start, end, status, rok) => ({
	id, resourceId: 'r1', resourceName: resurs, organizationName: organizacija, timezone: 'Europe/Sarajevo', startTime: start, endTime: end, status, cancelDeadline: rok,
})
// Sada je subota 17.11.2035. u 11:00 po Sarajevu. Backend šalje najkasniji termin prvo.
const POCETNE = () => [
	rez('b3', 'Grupna soba 2', 'Čitaonica PMF', '2035-11-20T09:00:00Z', '2035-11-20T10:30:00Z', 'PENDING', '2035-11-19T09:00:00Z'),
	rez('b1', 'Sto 4, tihi dio', 'Čitaonica PMF', '2035-11-19T08:00:00Z', '2035-11-19T09:00:00Z', 'CONFIRMED', '2035-11-18T08:00:00Z'),
	rez('b2', 'Termin konsultacija', 'Konsultacije ETF', '2035-11-17T15:00:00Z', '2035-11-17T16:00:00Z', 'CONFIRMED', '2035-11-17T09:00:00Z'),
	rez('b4', 'Sala 1', 'Teretana Ilidža', '2035-11-10T17:00:00Z', '2035-11-10T18:00:00Z', 'CANCELLED', '2035-11-10T17:00:00Z'),
]

// Lažni backend pamti otkazivanje, kao pravi
function saRezervacijama(otkazivanje = () => null, rezervacije = POCETNE()) {
	const fetch = backend({
		...PRIJAVLJENA,
		'/api/bookings/mine?page=0&size=20': () => json({ content: rezervacije, page: 0, size: 20, totalElements: rezervacije.length, totalPages: 1 }),
		'POST /api/bookings/b1/cancel': async () => {
			const odgovor = await otkazivanje()
			if (odgovor !== null) return odgovor
			rezervacije.find((r) => r.id === 'b1').status = 'CANCELLED'
			return json({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'CANCELLED', expiresAt: null })
		},
	})
	return fetch
}

const lista = (naziv) => within(screen.getByRole('list', { name: naziv })).getAllByRole('listitem')
const redSa = (tekst) => screen.getAllByRole('listitem').find((li) => li.textContent.includes(tekst))
const otkazivanja = (fetch) => fetch.mock.calls.filter(([putanja]) => putanja === '/api/bookings/b1/cancel')

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-11-17T10:00:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

describe('lista', () => {
	test('do liste se dolazi iz zaglavlja, nadolazeće su od najbliže, a prošle i otkazane odvojeno', async () => {
		saRezervacijama()
		otvori('/')

		await userEvent.setup().click(await screen.findByRole('link', { name: 'Moje rezervacije' }))

		expect(await screen.findByRole('heading', { name: 'Moje rezervacije' })).toBeTruthy()
		expect(lista('Nadolazeće').map((li) => li.querySelector('time').textContent)).toEqual([
			'subota, 17. novembar, 16:00 do 17:00',
			'ponedjeljak, 19. novembar, 09:00 do 10:00',
			'utorak, 20. novembar, 10:00 do 11:30',
		])
		expect(lista('Prošle i otkazane')).toHaveLength(1)
		expect(within(redSa('Sala 1')).getByText('otkazana')).toBeTruthy()
		expect(within(redSa('Grupna soba 2')).getByText('čeka potvrdu')).toBeTruthy()
		expect(within(redSa('Sto 4')).getByText('Sto 4, tihi dio · Čitaonica PMF')).toBeTruthy()
		expect(window.location.pathname).toBe('/rezervacije')
	})

	test('bez rezervacija piše gdje se termin bira', async () => {
		saRezervacijama(undefined, [])
		otvori('/rezervacije')

		expect(await screen.findByText(/Još nemaš rezervacija/)).toBeTruthy()
		expect(screen.getByRole('link', { name: 'Tvoje organizacije' })).toBeTruthy()
	})

	test('kad se lista ne učita, piše zašto i nudi ponovni pokušaj', async () => {
		let radi = false
		backend({ ...PRIJAVLJENA, '/api/bookings/mine?page=0&size=20': () => (radi ? json({ content: POCETNE(), page: 0, size: 20, totalElements: 4, totalPages: 1 }) : new Response('', { status: 502 })) })
		otvori('/rezervacije')

		expect((await screen.findByRole('alert', {}, { timeout: 3000 })).textContent).toMatch(/Rezervacije se nisu učitale, server ne odgovara/)
		radi = true
		await userEvent.setup().click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

		expect(await screen.findByRole('list', { name: 'Nadolazeće' })).toBeTruthy()
	})

	test('starije rezervacije su na sljedećoj stranici', async () => {
		const fetch = backend({
			...PRIJAVLJENA,
			'/api/bookings/mine?page=0&size=20': () => json({ content: POCETNE(), page: 0, size: 20, totalElements: 21, totalPages: 2 }),
			'/api/bookings/mine?page=1&size=20': () =>
				json({ content: [rez('b9', 'Sala 3', 'Teretana Ilidža', '2035-10-01T17:00:00Z', '2035-10-01T18:00:00Z', 'COMPLETED', '2035-10-01T17:00:00Z')], page: 1, size: 20, totalElements: 21, totalPages: 2 }),
		})
		otvori('/rezervacije')
		const korisnik = userEvent.setup()

		expect((await screen.findByRole('button', { name: 'Novije' })).disabled).toBe(true)
		await korisnik.click(screen.getByRole('button', { name: 'Starije' }))

		expect(await screen.findByText('Sala 3 · Teretana Ilidža')).toBeTruthy()
		expect(screen.getByRole('button', { name: 'Starije' }).disabled).toBe(true)
		expect(fetch.mock.calls.some(([putanja]) => putanja === '/api/bookings/mine?page=1&size=20')).toBe(true)
	})
})

describe('otkazivanje', () => {
	test('rok za otkazivanje piše uz rezervaciju, a otkazivanje traži potvrdu', async () => {
		const fetch = saRezervacijama()
		otvori('/rezervacije')
		const korisnik = userEvent.setup()

		const red = await waitFor(() => redSa('Sto 4'))
		expect(within(red).getByText('Rok za otkazivanje: nedjelja, 18. novembar, 09:00.')).toBeTruthy()
		await korisnik.click(within(red).getByRole('button', { name: 'Otkaži' }))
		const grupa = screen.getByRole('group', { name: 'Otkazati Sto 4, tihi dio, ponedjeljak, 19. novembar, 09:00 do 10:00?' })
		expect(otkazivanja(fetch)).toHaveLength(0)
		await korisnik.click(within(grupa).getByRole('button', { name: 'Otkaži rezervaciju' }))

		expect((await screen.findByText(/Rezervacija je otkazana/)).textContent).toBe('Rezervacija je otkazana: Sto 4, tihi dio, ponedjeljak, 19. novembar, 09:00 do 10:00.')
		await waitFor(() => expect(lista('Prošle i otkazane')).toHaveLength(2))
		expect(otkazivanja(fetch)).toHaveLength(1)
	})

	test('Ne zatvara potvrdu bez otkazivanja i vraća fokus na dugme', async () => {
		const fetch = saRezervacijama()
		otvori('/rezervacije')
		const korisnik = userEvent.setup()

		const dugme = within(await waitFor(() => redSa('Sto 4'))).getByRole('button', { name: 'Otkaži' })
		await korisnik.click(dugme)
		await korisnik.click(screen.getByRole('button', { name: 'Ne, ostavi je' }))

		expect(screen.queryByRole('group')).toBeNull()
		expect(document.activeElement).toBe(dugme)
		expect(otkazivanja(fetch)).toHaveLength(0)
	})

	test('kad je rok prošao, otkazivanje se ne nudi i piše šta korisnik može uraditi', async () => {
		saRezervacijama()
		otvori('/rezervacije')

		const red = await waitFor(() => redSa('Termin konsultacija'))
		expect(within(red).queryByRole('button', { name: 'Otkaži' })).toBeNull()
		expect(within(red).getByText('Rok za otkazivanje je bio subota, 17. novembar, 10:00. Ako ne možeš doći, javi se osobi koja vodi termin.')).toBeTruthy()
	})

	test('ako rok istekne dok je ekran otvoren, poruka backenda kaže zašto', async () => {
		saRezervacijama(() =>
			problem(409, {
				title: 'Rok za otkazivanje je prošao',
				detail: 'Ovaj termin se može otkazati najkasnije 24 h prije početka. Ako ne možeš doći, javi se osobi koja vodi termin.',
				rokZaOtkazivanje: '2035-11-18T08:00:00Z',
			}),
		)
		otvori('/rezervacije')
		const korisnik = userEvent.setup()

		await korisnik.click(within(await waitFor(() => redSa('Sto 4'))).getByRole('button', { name: 'Otkaži' }))
		await korisnik.click(screen.getByRole('button', { name: 'Otkaži rezervaciju' }))

		expect((await screen.findByRole('alert')).textContent).toMatch(/najkasnije 24 h prije početka/)
		expect(screen.queryByRole('button', { name: 'Otkaži rezervaciju' })).toBeNull()
	})

	// Prvi zahtjev je možda stigao, a odgovor se izgubio. Ponovni pokušaj tada dobije 409
	// "već otkazana", a to je upravo ono što je korisnik htio.
	test('ponovljeno otkazivanje poslije pada mreže, kad je prvo ipak prošlo, računa se kao uspjeh', async () => {
		let pokusaj = 0
		const fetch = saRezervacijama(async () => {
			pokusaj += 1
			if (pokusaj === 1) throw new TypeError('Failed to fetch')
			return problem(409, { title: 'Promjena nije moguća', detail: 'Ova promjena nije moguća jer je rezervacija otkazana. Osvježi prikaz.', trenutniStatus: 'CANCELLED', trazeniStatus: 'CANCELLED' })
		})
		otvori('/rezervacije')
		const korisnik = userEvent.setup()

		await korisnik.click(within(await waitFor(() => redSa('Sto 4'))).getByRole('button', { name: 'Otkaži' }))
		await korisnik.click(screen.getByRole('button', { name: 'Otkaži rezervaciju' }))
		expect((await screen.findByRole('alert')).textContent).toMatch(/Nije jasno da li je otkazivanje stiglo/)
		await korisnik.click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

		expect(await screen.findByText(/Rezervacija je otkazana/)).toBeTruthy()
		expect(otkazivanja(fetch)).toHaveLength(2)
	})
})
