// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori } from '../test/lazniBackend.js'

const resurs = (id, name, capacity = 1) => ({ id, organizationId: 'o1', providerId: 'u9', name, description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity, priceCents: 0, active: true, hasImage: false })
const RESURSI = {
	'/api/organizations/o1/resources?size=100': () =>
		json({ content: [resurs('r1', 'Sto 4, tihi dio'), resurs('r2', 'Grupna soba 2', 4)], page: 0, size: 100, totalElements: 2, totalPages: 1 }),
}
// Ponedjeljak 19.11.2035 u 07:00 po Sarajevu: sutra poslije podne je utorak od 12 do 18
const SUTRA_POPODNE = '/api/organizations/o1/free-slots?date=2035-11-20&days=1&fromHour=12&toHour=18'
const termin = (resourceId, resourceName, start, end, capacity = 1, seatsLeft = 1) => ({ resourceId, resourceName, capacity, start, end, seatsLeft })
const SLOBODNO = {
	timezone: 'Europe/Sarajevo',
	slots: [
		termin('r2', 'Grupna soba 2', '2035-11-20T13:00:00Z', '2035-11-20T14:00:00Z', 4, 3),
		termin('r1', 'Sto 4, tihi dio', '2035-11-20T14:00:00Z', '2035-11-20T15:00:00Z'),
		termin('r2', 'Grupna soba 2', '2035-11-20T14:00:00Z', '2035-11-20T15:00:00Z', 4, 4),
	],
}
const KALENDAR_STOLA = (slots, taken = []) => ({
	'/api/resources/r1/slots?from=2035-11-19&to=2035-11-26': () => json({ timezone: 'Europe/Sarajevo', slots, taken }),
})

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

test('sutra poslije podne pokaže slobodno po satu, a klik otvori kalendar s izabranim terminom', async () => {
	const fetch = backend({
		...PRIJAVLJENA,
		...RESURSI,
		[SUTRA_POPODNE]: () => json(SLOBODNO),
		...KALENDAR_STOLA([{ start: '2035-11-20T14:00:00Z', end: '2035-11-20T15:00:00Z' }]),
	})
	otvori('/o/citaonica-pmf')
	const korisnik = userEvent.setup()

	// Dok se ne izabere, ništa se ne traži
	await korisnik.click(await screen.findByRole('button', { name: 'Sutra poslije podne' }))
	const lista = await screen.findByRole('list', { name: 'Slobodno: Sutra poslije podne' })
	expect(fetch.mock.calls.filter(([p]) => p.includes('/free-slots')).map(([p]) => p)).toEqual([SUTRA_POPODNE])
	const redovi = within(lista).getAllByRole('listitem').filter((li) => li.classList.contains('slobodno-red'))
	expect(redovi.map((r) => r.textContent)).toEqual([
		'14:00Grupna soba 2do 15:00, još 3 mjesta',
		'15:00Sto 4, tihi diodo 16:00Grupna soba 2do 16:00, još 4 mjesta',
	])
	expect(screen.getByRole('button', { name: 'Sutra poslije podne', pressed: true })).toBeTruthy()

	await korisnik.click(within(redovi[1]).getByRole('link', { name: /Sto 4, tihi dio/ }))

	expect(await screen.findByRole('group', { name: 'Rezervisati utorak, 20. novembar, 15:00 do 16:00?' })).toBeTruthy()
	expect(screen.getByRole('button', { name: /utorak, 20. novembar, 1 slobodan/, pressed: true })).toBeTruthy()
})

test('kad nema ničeg slobodnog, poruka kaže šta probati', async () => {
	backend({ ...PRIJAVLJENA, ...RESURSI, [SUTRA_POPODNE]: () => json({ timezone: 'Europe/Sarajevo', slots: [] }) })
	otvori('/o/citaonica-pmf')

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Sutra poslije podne' }))

	expect(await screen.findByText(/Sutra poslije podne nema slobodnih termina ni u jednom resursu/)).toBeTruthy()
})

// Između liste i klika neko drugi uzme termin: kalendar ne otvara potvrdu za zauzet termin
test('termin koji je u međuvremenu zauzet ne otvara potvrdu, nego poruku', async () => {
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		...KALENDAR_STOLA([], [{ start: '2035-11-20T14:00:00Z', end: '2035-11-20T15:00:00Z', myStatus: null }]),
	})
	otvori(`/o/citaonica-pmf/r/r1?termin=${encodeURIComponent('2035-11-20T14:00:00Z')}`)

	expect(await screen.findByText('Taj termin je u međuvremenu zauzet. Izaberi drugi s ploče.')).toBeTruthy()
	expect(screen.queryByRole('group', { name: /Rezervisati/ })).toBeNull()
	expect(screen.getByRole('button', { name: /utorak, 20. novembar/, pressed: true })).toBeTruthy()
})
