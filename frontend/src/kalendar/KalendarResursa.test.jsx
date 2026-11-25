// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori } from '../test/lazniBackend.js'

const STO = { id: 'r1', organizationId: 'o1', providerId: 'u9', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity: 1, priceCents: 0, active: true, hasImage: false }
const RESURSI = { '/api/organizations/o1/resources?size=100': () => json({ content: [STO], page: 0, size: 100, totalElements: 1, totalPages: 1 }) }
const slobodan = (start, end) => ({ start, end })

// Ponedjeljak 19.11.2035: 08 slobodno, 09 tvoje, 10 čeka potvrdu, 11 tuđe, 12 slobodno.
// Utorak nema ništa, srijeda ima jedan slobodan termin.
const SEDMICA = {
	timezone: 'Europe/Sarajevo',
	slots: [
		slobodan('2035-11-19T07:00:00Z', '2035-11-19T08:00:00Z'),
		slobodan('2035-11-19T11:00:00Z', '2035-11-19T12:00:00Z'),
		slobodan('2035-11-21T07:00:00Z', '2035-11-21T08:00:00Z'),
	],
	taken: [
		{ start: '2035-11-19T08:00:00Z', end: '2035-11-19T09:00:00Z', myStatus: 'CONFIRMED' },
		{ start: '2035-11-19T09:00:00Z', end: '2035-11-19T10:00:00Z', myStatus: 'PENDING' },
		{ start: '2035-11-19T10:00:00Z', end: '2035-11-19T11:00:00Z', myStatus: null },
	],
}
const KALENDAR = { '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26': () => json(SEDMICA) }

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

const redovi = () => within(screen.getByRole('list', { name: /Termini/ })).getAllByRole('listitem')

test('iz organizacije se bira resurs, a kalendar otvara današnji dan u zoni organizacije', async () => {
	const fetch = backend({ ...PRIJAVLJENA, ...RESURSI, ...KALENDAR })
	otvori('/o/citaonica-pmf')

	await userEvent.setup().click(await screen.findByRole('link', { name: /^Sto 4, tihi dio/ }))

	expect(await screen.findByRole('heading', { name: 'Sto 4, tihi dio' })).toBeTruthy()
	expect(screen.getByRole('button', { name: /ponedjeljak, 19. novembar/, pressed: true })).toBeTruthy()
	// Tačkica od žara na danu s mojim terminom se čuje i na čitaču ekrana
	expect(screen.getByRole('button', { name: 'ponedjeljak, 19. novembar, imaš termin' })).toBeTruthy()
	expect(screen.getByRole('button', { name: 'utorak, 20. novembar' })).toBeTruthy()
	expect(window.location.pathname).toBe('/o/citaonica-pmf/r/r1')
	expect(fetch.mock.calls.some(([putanja]) => putanja === '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26')).toBe(true)
})

test('četiri stanja termina se razlikuju i tekstom, ne samo bojom', async () => {
	backend({ ...PRIJAVLJENA, ...RESURSI, ...KALENDAR })
	otvori('/o/citaonica-pmf/r/r1')

	await screen.findByRole('list', { name: /Termini/ })

	expect(redovi().map((red) => red.textContent)).toEqual([
		'08:00–09:00slobodno',
		'09:00–10:00tvoje',
		'10:00–11:00čeka potvrdu',
		'11:00–12:00zauzeto',
		'12:00–13:00slobodno',
	])
	expect(screen.getByRole('button', { name: 'ponedjeljak, 19. novembar, 08:00 do 09:00, slobodno' })).toBeTruthy()
	// Samo slobodan termin se može izabrati
	expect(screen.getAllByRole('button', { name: /slobodno$/ })).toHaveLength(2)
})

// Grupni trening s 5 mjesta: slobodan kaže koliko je mjesta ostalo, pun je "popunjeno", a tvoje ostaje tvoje
test('grupni termin pokazuje koliko je mjesta ostalo, a pun je popunjen', async () => {
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		'/api/resources/r1/slots?from=2035-11-19&to=2035-11-26': () =>
			json({
				timezone: 'Europe/Sarajevo',
				capacity: 5,
				slots: [
					{ start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z', seatsLeft: 5 },
					{ start: '2035-11-19T08:00:00Z', end: '2035-11-19T09:00:00Z', seatsLeft: 1 },
				],
				taken: [
					{ start: '2035-11-19T09:00:00Z', end: '2035-11-19T10:00:00Z', myStatus: 'CONFIRMED' },
					{ start: '2035-11-19T10:00:00Z', end: '2035-11-19T11:00:00Z', myStatus: null },
					{ start: '2035-11-19T11:00:00Z', end: '2035-11-19T12:00:00Z', myStatus: null, myWaitlistId: 'w1' },
				],
			}),
	})
	otvori('/o/citaonica-pmf/r/r1')

	await screen.findByRole('list', { name: /Termini/ })

	expect(redovi().map((red) => red.textContent)).toEqual([
		'08:00–09:00slobodno, još 5 mjesta',
		'09:00–10:00slobodno, još 1 mjesto',
		'10:00–11:00tvoje',
		'11:00–12:00popunjeno',
		'12:00–13:00popunjeno, na listi si',
	])
	expect(screen.getByRole('button', { name: 'ponedjeljak, 19. novembar, 09:00 do 10:00, slobodno, još 1 mjesto' })).toBeTruthy()
})

test('strelice idu kroz slobodne termine, a Enter otvara potvrdu za termin', async () => {
	backend({ ...PRIJAVLJENA, ...RESURSI, ...KALENDAR })
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	;(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ })).focus()
	await korisnik.keyboard('{ArrowDown}')
	expect(document.activeElement.getAttribute('aria-label')).toMatch(/12:00 do 13:00, slobodno/)
	await korisnik.keyboard('{ArrowUp}{Enter}')

	expect(screen.getByRole('group', { name: 'Rezervisati ponedjeljak, 19. novembar, 08:00 do 09:00?' })).toBeTruthy()
})

test('dan bez termina kaže to i vodi na prvi dan sedmice sa slobodnim terminom', async () => {
	backend({ ...PRIJAVLJENA, ...RESURSI, ...KALENDAR })
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /utorak, 20. novembar/ }))
	expect(screen.getByText('Ovaj dan nema termina.')).toBeTruthy()
	await korisnik.click(screen.getByRole('button', { name: 'Prvi slobodan: srijeda, 21. novembar' }))

	expect(screen.getByRole('button', { name: 'srijeda, 21. novembar, 08:00 do 09:00, slobodno' })).toBeTruthy()
})

test('sljedećih 7 dana učitava novu sedmicu, a u prošlost se ne može', async () => {
	const fetch = backend({
		...PRIJAVLJENA,
		...RESURSI,
		...KALENDAR,
		'/api/resources/r1/slots?from=2035-11-26&to=2035-12-03': () =>
			json({ timezone: 'Europe/Sarajevo', slots: [slobodan('2035-11-26T07:00:00Z', '2035-11-26T08:00:00Z')], taken: [] }),
	})
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	expect((await screen.findByRole('button', { name: 'Prethodnih 7 dana' })).disabled).toBe(true)
	await korisnik.click(screen.getByRole('button', { name: 'Sljedećih 7 dana' }))

	expect(await screen.findByRole('button', { name: /ponedjeljak, 26. novembar, 08:00 do 09:00/ })).toBeTruthy()
	expect(fetch.mock.calls.some(([putanja]) => putanja.endsWith('from=2035-11-26&to=2035-12-03'))).toBe(true)
})

// Ljetno vrijeme 2035. počinje u nedjelju 25. marta. Termin od 08:00 je prije toga 07:00Z,
// a poslije 06:00Z, i na oba dana mora pisati 08:00.
test('termin od 08:00 piše 08:00 i dan prije i dan poslije prelaska na ljetno vrijeme', async () => {
	vi.setSystemTime(new Date('2035-03-24T05:00:00Z'))
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		'/api/resources/r1/slots?from=2035-03-24&to=2035-03-31': () =>
			json({
				timezone: 'Europe/Sarajevo',
				slots: [slobodan('2035-03-24T07:00:00Z', '2035-03-24T08:00:00Z'), slobodan('2035-03-26T06:00:00Z', '2035-03-26T07:00:00Z')],
				taken: [],
			}),
	})
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	expect(await screen.findByRole('button', { name: 'subota, 24. mart, 08:00 do 09:00, slobodno' })).toBeTruthy()
	await korisnik.click(screen.getByRole('button', { name: /ponedjeljak, 26. mart/ }))
	expect(screen.getByRole('button', { name: 'ponedjeljak, 26. mart, 08:00 do 09:00, slobodno' })).toBeTruthy()
})

// U 00:30 po Sarajevu je u UTC-u još prethodni dan. I "danas" i dan termina moraju se
// računati u zoni organizacije, inače bi kalendar otvorio ponedjeljak, a termin od 00:30
// u srijedu stavio u utorak.
test('u 00:30 po Sarajevu je već utorak, a termin od 00:30 u srijedu je u srijedi', async () => {
	vi.setSystemTime(new Date('2035-11-19T23:30:00Z'))
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		'/api/resources/r1/slots?from=2035-11-20&to=2035-11-27': () =>
			json({ timezone: 'Europe/Sarajevo', slots: [slobodan('2035-11-20T23:30:00Z', '2035-11-21T00:30:00Z')], taken: [] }),
	})
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	expect(await screen.findByText('Ovaj dan nema termina.')).toBeTruthy()
	expect(screen.getByRole('button', { name: 'utorak, 20. novembar', pressed: true })).toBeTruthy()
	await korisnik.click(screen.getByRole('button', { name: 'Prvi slobodan: srijeda, 21. novembar' }))

	expect(screen.getByRole('button', { name: 'srijeda, 21. novembar, 00:30 do 01:30, slobodno' })).toBeTruthy()
})

test('kad se termini ne učitaju, piše zašto i nudi ponovni pokušaj', async () => {
	let radi = false
	backend({ ...PRIJAVLJENA, ...RESURSI, '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26': () => (radi ? json(SEDMICA) : new Response('', { status: 502 })) })
	otvori('/o/citaonica-pmf/r/r1')

	expect((await screen.findByRole('alert', {}, { timeout: 3000 })).textContent).toMatch(/Termini se nisu učitali, server ne odgovara/)
	radi = true
	await userEvent.setup().click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

	expect(await screen.findByRole('list', { name: /Termini/ })).toBeTruthy()
})

test('organizacija bez resursa kaže da još nema šta rezervisati', async () => {
	backend({ ...PRIJAVLJENA, '/api/organizations/o1/resources?size=100': () => json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }) })
	otvori('/o/citaonica-pmf')

	expect(await screen.findByText(/još nema ništa za rezervaciju/)).toBeTruthy()
})

test('resurs koji nije u organizaciji ne otkriva da li postoji', async () => {
	backend({ ...PRIJAVLJENA, ...RESURSI })
	otvori('/o/citaonica-pmf/r/tudji-resurs')

	expect((await screen.findByRole('heading', { level: 1 })).textContent).toBe('Resurs nije dostupan')
})
