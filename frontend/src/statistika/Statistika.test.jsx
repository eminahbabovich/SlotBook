// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { CITAONICA, PRIJAVLJENA, TERETANA, backend, json, otvori, problem } from '../test/lazniBackend.js'

const KONSULTACIJE = { id: 'o3', name: 'Konsultacije ETF', slug: 'konsultacije-etf', timezone: 'Europe/Sarajevo', role: 'PROVIDER' }
const NULE = { PENDING: 0, CONFIRMED: 0, REJECTED: 0, EXPIRED: 0, CANCELLED: 0, COMPLETED: 0, NO_SHOW: 0 }

// Brojke iz StatsApiTest (admin čitaonice, novembar 2035)
const NOVEMBAR = {
	from: '2035-11-01',
	to: '2035-12-01',
	timezone: 'Europe/Sarajevo',
	byStatus: { PENDING: 1, CONFIRMED: 2, REJECTED: 0, EXPIRED: 0, CANCELLED: 1, COMPLETED: 2, NO_SHOW: 1 },
	byDayAndHour: [
		{ dayOfWeek: 1, hour: 8, count: 3 },
		{ dayOfWeek: 1, hour: 9, count: 1 },
		{ dayOfWeek: 1, hour: 10, count: 1 },
		{ dayOfWeek: 1, hour: 11, count: 1 },
		{ dayOfWeek: 2, hour: 14, count: 1 },
	],
	occupancy: [
		{ resourceId: 'r1', name: 'Sto br. 1', bookedMinutes: 240, offeredMinutes: 840 },
		{ resourceId: 'r2', name: 'Sto br. 2', bookedMinutes: 60, offeredMinutes: 480 },
	],
}

const stats = (org, od, doDana) => `/api/organizations/${org}/stats?from=${od}&to=${doDana}`

function organizacije(odgovori = {}) {
	return backend({
		...PRIJAVLJENA,
		'/api/me/organizations': () => json([CITAONICA, TERETANA, KONSULTACIJE]),
		'/api/organizations/o3/resources?size=100': () => json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
		[stats('o1', '2035-11-01', '2035-12-01')]: () => json(NOVEMBAR),
		...odgovori,
	})
}

const red = (tabela, tekst) => within(tabela).getByText(tekst).closest('tr')
const pozvano = (fetch, putanja) => fetch.mock.calls.some(([p]) => p === putanja)

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-11-19T09:00:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

describe('statistika', () => {
	test('admin iz organizacije dođe na tekući mjesec i vidi brojke iz backenda', async () => {
		const fetch = organizacije()
		otvori('/o/citaonica-pmf')

		await userEvent.setup().click(await screen.findByRole('link', { name: 'Statistika' }))

		expect(await screen.findByRole('heading', { name: 'Statistika: Čitaonica PMF' })).toBeTruthy()
		expect(screen.getByRole('heading', { name: 'novembar 2035' })).toBeTruthy()
		const rezervacije = await screen.findByRole('table', { name: 'Rezervacije po stanju' })
		expect(within(red(rezervacije, 'potvrđena')).getByText('2')).toBeTruthy()
		expect(within(red(rezervacije, 'nedolazak')).getByText('1')).toBeTruthy()
		expect(screen.getByText('Ukupno 7 rezervacija.')).toBeTruthy()

		const popunjenost = screen.getByRole('table', { name: 'Popunjenost po resursu' })
		expect(within(red(popunjenost, 'Sto br. 1')).getByText('4 h od 14 h')).toBeTruthy()
		expect(within(red(popunjenost, 'Sto br. 1')).getByText('29 %')).toBeTruthy()

		const najtrazeniji = screen.getByRole('table', { name: 'Najtraženiji termini' })
		const [, prvi] = within(najtrazeniji).getAllByRole('row')
		expect(within(prvi).getByText('ponedjeljak, 08:00 do 09:00')).toBeTruthy()
		expect(within(prvi).getByText('3')).toBeTruthy()
		expect(pozvano(fetch, stats('o1', '2035-11-01', '2035-12-01'))).toBe(true)
	})

	// 31.12. u 23:30 UTC je u Sarajevu već 1. januar
	test('tekući mjesec je po zoni organizacije, a prethodni prelazi u staru godinu', async () => {
		vi.setSystemTime(new Date('2035-12-31T23:30:00Z'))
		const fetch = organizacije({
			[stats('o1', '2036-01-01', '2036-02-01')]: () => json({ ...NOVEMBAR, byStatus: NULE, byDayAndHour: [] }),
			[stats('o1', '2035-12-01', '2036-01-01')]: () => json(NOVEMBAR),
		})
		otvori('/o/citaonica-pmf/statistika')

		expect(await screen.findByRole('heading', { name: 'januar 2036' })).toBeTruthy()
		await userEvent.setup().click(screen.getByRole('link', { name: 'Prethodni mjesec' }))

		expect(await screen.findByRole('heading', { name: 'decembar 2035' })).toBeTruthy()
		expect(window.location.search).toBe('?mjesec=2035-12')
		expect(await screen.findByText('Ukupno 7 rezervacija.')).toBeTruthy()
		expect(pozvano(fetch, stats('o1', '2036-01-01', '2036-02-01'))).toBe(true)
	})

	test('popunjenost: bez radnog vremena nema procenta, preko 100 % ima objašnjenje, a ukupno je iz minuta', async () => {
		organizacije({
			[stats('o1', '2035-11-01', '2035-12-01')]: () =>
				json({
					...NOVEMBAR,
					occupancy: [
						{ resourceId: 'r1', name: 'Sto br. 1', bookedMinutes: 240, offeredMinutes: 840 },
						{ resourceId: 'r2', name: 'Grupna soba 2', bookedMinutes: 600, offeredMinutes: 480 },
						{ resourceId: 'r3', name: 'Sto br. 3', bookedMinutes: 0, offeredMinutes: 0 },
					],
				}),
		})
		otvori('/o/citaonica-pmf/statistika')

		const popunjenost = await screen.findByRole('table', { name: 'Popunjenost po resursu' })
		expect(within(red(popunjenost, 'Sto br. 3')).getByText('nema radnog vremena')).toBeTruthy()
		expect(within(red(popunjenost, 'Grupna soba 2')).getByText('125 %')).toBeTruthy()
		expect(screen.getByText(/Preko 100 % znači/)).toBeTruthy()
		// (240 + 600) / (840 + 480 + 0) = 63,6 %, a ne prosjek procenata
		expect(within(red(popunjenost, 'Ukupno')).getByText('64 %')).toBeTruthy()
	})

	test('mjesec bez rezervacija kaže to riječima i ne prikazuje najtraženije termine', async () => {
		organizacije({ [stats('o1', '2035-11-01', '2035-12-01')]: () => json({ ...NOVEMBAR, byStatus: NULE, byDayAndHour: [] }) })
		otvori('/o/citaonica-pmf/statistika')

		expect(await screen.findByText('Za novembar 2035 nema rezervacija.')).toBeTruthy()
		expect(screen.queryByRole('table', { name: 'Rezervacije po stanju' })).toBeNull()
		expect(screen.queryByRole('heading', { name: 'Najtraženiji termini' })).toBeNull()
	})

	test('osoba koja vodi termine vidi samo svoje resurse, a klijent ni link ni statistiku', async () => {
		const fetch = organizacije({ [stats('o3', '2035-11-01', '2035-12-01')]: () => json({ ...NOVEMBAR, occupancy: [] }) })
		otvori('/o/konsultacije-etf/statistika')

		expect(await screen.findByText(/Samo resursi koje vodiš/)).toBeTruthy()
		cleanup()

		otvori('/o/teretana-ilidza')
		expect(await screen.findByRole('heading', { name: 'Teretana Ilidža' })).toBeTruthy()
		expect(screen.queryByRole('link', { name: 'Statistika' })).toBeNull()
		cleanup()

		otvori('/o/teretana-ilidza/statistika')
		expect(await screen.findByRole('heading', { name: 'Statistika nije dostupna' })).toBeTruthy()
		expect(fetch.mock.calls.some(([p]) => p.startsWith('/api/organizations/o2/stats'))).toBe(false)
	})

	test('server ne odgovara: poruka kaže šta nije učitano i nudi ponovni pokušaj', async () => {
		let pokusaj = 0
		organizacije({
			// Dva puta, jer aplikacija sama ponovi jednom kad server ne odgovara (App.jsx)
			[stats('o1', '2035-11-01', '2035-12-01')]: () => (++pokusaj <= 2 ? problem(503, { detail: 'nema' }) : json(NOVEMBAR)),
		})
		otvori('/o/citaonica-pmf/statistika')

		expect((await screen.findByRole('alert', {}, { timeout: 3000 })).textContent).toMatch(/^Statistika se nije učitala, server ne odgovara/)
		await userEvent.setup().click(screen.getByRole('button', { name: /Pokušaj ponovo/ }))

		expect(await screen.findByRole('table', { name: 'Rezervacije po stanju' })).toBeTruthy()
	})
})

describe('CSV', () => {
	test('preuzimanje pravi fajl s imenom po periodu iz onoga što je backend poslao', async () => {
		// String, a ne jsdom Blob: Response iz Node-a jsdom Blob ne prepozna i pošalje "[object Blob]"
		const csv = '\uFEFFResurs;Zauzeto (min)\r\n'
		organizacije({
			[stats('o1', '2035-11-01', '2035-12-01')]: (_, zaglavlja) =>
				zaglavlja.Accept.startsWith('text/csv') ? new Response(csv, { status: 200, headers: { 'Content-Type': 'text/csv' } }) : json(NOVEMBAR),
		})
		// jsdom nema createObjectURL, pa se doda samo za ovaj test
		const napravi = vi.fn(() => 'blob:csv-1')
		Object.assign(URL, { createObjectURL: napravi, revokeObjectURL: vi.fn() })
		const kliknuto = []
		const klik = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
			kliknuto.push({ href: this.href, ime: this.download })
		})
		otvori('/o/citaonica-pmf/statistika')

		await userEvent.setup().click(await screen.findByRole('button', { name: 'Preuzmi popunjenost (CSV)' }))

		await vi.waitFor(() => expect(kliknuto).toEqual([{ href: 'blob:csv-1', ime: 'popunjenost-2035-11-01-2035-12-01.csv' }]))
		expect(await napravi.mock.calls[0][0].text()).toContain('Resurs;Zauzeto')
		klik.mockRestore()
		delete URL.createObjectURL
		delete URL.revokeObjectURL
	})

	test('greška pri preuzimanju se vidi, a tabele ostaju', async () => {
		organizacije({
			[stats('o1', '2035-11-01', '2035-12-01')]: (_, zaglavlja) =>
				zaglavlja.Accept.startsWith('text/csv') ? problem(500, {}) : json(NOVEMBAR),
		})
		otvori('/o/citaonica-pmf/statistika')

		await userEvent.setup().click(await screen.findByRole('button', { name: 'Preuzmi popunjenost (CSV)' }))

		expect((await screen.findByRole('alert')).textContent).toMatch(/^Fajl nije preuzet/)
		expect(screen.getByRole('table', { name: 'Popunjenost po resursu' })).toBeTruthy()
	})
})
