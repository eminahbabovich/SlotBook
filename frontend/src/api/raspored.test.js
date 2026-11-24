import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { promijeniStatus, rasporedResursa } from './raspored.js'

const json = (tijelo) => new Response(JSON.stringify(tijelo), { status: 200, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Oblik kao ResourceBookingResponse u BookingPrelaziApiTest
test('raspored resursa za dan dolazi s imenom klijenta, statusom i rokom za potvrdu', async () => {
	const fetch = vi.fn(async () =>
		json([{ id: 'b1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'PENDING', clientName: 'Hana Šehić', expiresAt: '2035-11-18T08:00:00Z', awaitingPayment: false }]),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await rasporedResursa('r1', '2035-11-19', '2035-11-20')).toEqual([
		{ id: 'b1', pocetak: '2035-11-19T08:00:00Z', kraj: '2035-11-19T09:00:00Z', status: 'PENDING', klijent: 'Hana Šehić', rokPotvrde: '2035-11-18T08:00:00Z', cekaUplatu: false },
	])
	expect(fetch.mock.calls[0][0]).toBe('/api/resources/r1/bookings?from=2035-11-19&to=2035-11-20')
})

test('svaka promjena statusa ide na svoju adresu', async () => {
	const fetch = vi.fn(async () => json({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'CONFIRMED', expiresAt: null }))
	vi.stubGlobal('fetch', fetch)

	for (const akcija of ['potvrdi', 'odbij', 'dosao', 'nijeDosao']) await promijeniStatus('b1', akcija)

	expect(fetch.mock.calls.map(([putanja, opcije]) => `${opcije.method} ${putanja}`)).toEqual([
		'POST /api/bookings/b1/confirm',
		'POST /api/bookings/b1/reject',
		'POST /api/bookings/b1/check-in',
		'POST /api/bookings/b1/no-show',
	])
})

test('rezervacija bez awaitingPayment je greška u API sloju, jer bi raspored ponudio potvrdu neplaćene', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => json([{ id: 'b1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'PENDING', clientName: 'Hana Šehić', expiresAt: '2035-11-18T08:00:00Z' }])))

	await expect(rasporedResursa('r1', '2035-11-19', '2035-11-20')).rejects.toThrow('awaitingPayment')
})
