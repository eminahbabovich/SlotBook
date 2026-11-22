// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const STO = { id: 'r1', organizationId: 'o1', providerId: 'u9', name: 'Kabinet 2-14', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, active: true, hasImage: false }
const SLOTS = '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26'
const SERIJA = 'POST /api/resources/r1/bookings/series'

const potvrdjena = (start, end) => ({ id: `b-${start}`, resourceId: 'r1', startTime: start, endTime: end, status: 'CONFIRMED', expiresAt: null })

function kalendar(serija) {
	return backend({
		...PRIJAVLJENA,
		'/api/organizations/o1/resources?size=100': () => json({ content: [STO], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
		[SLOTS]: () => json({ timezone: 'Europe/Sarajevo', slots: [{ start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z' }], taken: [] }),
		[SERIJA]: serija,
	})
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

async function izaberiOsamISedmice(korisnik, broj) {
	await korisnik.click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno$/ }))
	await korisnik.selectOptions(screen.getByLabelText('Ponavljanje'), `Svake sedmice, ${broj} puta`)
}

test('serija od tri sedmice šalje broj sedmica i ključ za tu seriju, a poruka kaže prvi i zadnji termin', async () => {
	const fetch = kalendar(() =>
		json(
			{
				seriesId: 's1',
				bookings: [
					potvrdjena('2035-11-19T07:00:00Z', '2035-11-19T08:00:00Z'),
					potvrdjena('2035-11-26T07:00:00Z', '2035-11-26T08:00:00Z'),
					potvrdjena('2035-12-03T07:00:00Z', '2035-12-03T08:00:00Z'),
				],
			},
			201,
		),
	)
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()
	await izaberiOsamISedmice(korisnik, 3)

	await korisnik.click(screen.getByRole('button', { name: 'Rezerviši 3 termina' }))

	expect(
		await screen.findByText('Termini su tvoji, svake sedmice u isto vrijeme. Prvi: ponedjeljak, 19. novembar, 08:00 do 09:00. Zadnji: ponedjeljak, 3. decembar.'),
	).toBeTruthy()
	expect(tijelo(fetch, SERIJA)).toEqual({ startTime: '2035-11-19T07:00:00Z', weeks: 3 })
	const zaglavlja = fetch.mock.calls.findLast(([p, o]) => `${o.method} ${p}` === SERIJA)[1].headers
	expect(zaglavlja['Idempotency-Key']).toMatch(/-3$/)
	expect(fetch.mock.calls.some(([p, o]) => o.method === 'POST' && p === '/api/resources/r1/bookings')).toBe(false)
})

test('409 serije kaže koje sedmice nisu slobodne, a izabrani termin ostaje slobodan i može se rezervisati sam', async () => {
	kalendar(() =>
		problem(409, {
			title: 'Neki termini iz serije nisu slobodni',
			detail: 'Nijedna rezervacija nije napravljena, jer neke sedmice nisu slobodne. Izaberi manje sedmica ili drugi termin.',
			zauzetiTermini: ['2035-11-26T07:00:00Z'],
		}),
	)
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()
	await izaberiOsamISedmice(korisnik, 2)

	await korisnik.click(screen.getByRole('button', { name: 'Rezerviši 2 termina' }))

	expect((await screen.findByRole('alert')).textContent).toBe(
		'Nijedna rezervacija nije napravljena, jer neke sedmice nisu slobodne. Izaberi manje sedmica ili drugi termin. Nisu slobodne: ponedjeljak, 26. novembar.',
	)
	expect(screen.getByRole('button', { name: /08:00 do 09:00, slobodno$/ })).toBeTruthy()
	expect(screen.queryByRole('button', { name: /^Najbliži slobodan/ })).toBeNull()

	// Manje sedmica je nova namjera: stara greška nestane, a dugme je opet za jedan termin
	await korisnik.selectOptions(screen.getByLabelText('Ponavljanje'), 'Samo ovaj put')
	expect(screen.queryByRole('alert')).toBeNull()
	expect(screen.getByRole('button', { name: 'Rezerviši' })).toBeTruthy()
})
