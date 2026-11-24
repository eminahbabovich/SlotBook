import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { linkZaDolazak, mojeRezervacije, otkazi, platiti, rezervisi, rezervisiSeriju, upisiDolazakSKodom } from './rezervacije.js'

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

// Oblik kao BookingResponse u BookingApiTest
test('rezervacija šalje početak i Idempotency-Key, a vraća status i rok za potvrdu', async () => {
	const fetch = vi.fn(async () =>
		new Response(
			JSON.stringify({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T07:00:00Z', endTime: '2035-11-19T08:00:00Z', status: 'PENDING', expiresAt: '2035-11-18T07:00:00Z' }),
			{ status: 201, headers: { 'Content-Type': 'application/json' } },
		),
	)
	vi.stubGlobal('fetch', fetch)

	const rezervacija = await rezervisi('r1', '2035-11-19T07:00:00Z', 'kljuc-1')

	const [putanja, opcije] = fetch.mock.calls[0]
	expect(putanja).toBe('/api/resources/r1/bookings')
	expect(opcije.method).toBe('POST')
	expect(JSON.parse(opcije.body)).toEqual({ startTime: '2035-11-19T07:00:00Z' })
	expect(opcije.headers['Idempotency-Key']).toBe('kljuc-1')
	expect(rezervacija).toEqual({ id: 'b1', pocetak: '2035-11-19T07:00:00Z', kraj: '2035-11-19T08:00:00Z', status: 'PENDING', rokPotvrde: '2035-11-18T07:00:00Z' })
})

test('moje rezervacije dolaze s nazivom resursa i organizacije, zonom, rokom za otkazivanje, cijenom i rokom za uplatu', async () => {
	const fetch = vi.fn(async () =>
		new Response(
			JSON.stringify({
				content: [
					{ id: 'b1', resourceId: 'r1', resourceName: 'Sto 4, tihi dio', organizationName: 'Čitaonica PMF', timezone: 'Europe/Sarajevo', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'CONFIRMED', cancelDeadline: '2035-11-18T08:00:00Z', priceCents: 0, paymentDeadline: null },
					{ id: 'b2', resourceId: 'r2', resourceName: 'Sala za jogu', organizationName: 'Teretana Ilidža', timezone: 'Europe/Sarajevo', startTime: '2035-11-20T07:00:00Z', endTime: '2035-11-20T08:00:00Z', status: 'PENDING', cancelDeadline: '2035-11-20T07:00:00Z', priceCents: 1500, paymentDeadline: '2035-11-17T10:30:00Z' },
				],
				page: 1, size: 20, totalElements: 21, totalPages: 2,
			}),
			{ status: 200, headers: { 'Content-Type': 'application/json' } },
		),
	)
	vi.stubGlobal('fetch', fetch)

	const stranica = await mojeRezervacije(1)

	expect(fetch.mock.calls[0][0]).toBe('/api/bookings/mine?page=1&size=20')
	expect(stranica).toEqual({
		rezervacije: [
			{ id: 'b1', resurs: 'Sto 4, tihi dio', organizacija: 'Čitaonica PMF', zona: 'Europe/Sarajevo', pocetak: '2035-11-19T08:00:00Z', kraj: '2035-11-19T09:00:00Z', status: 'CONFIRMED', rokOtkazivanja: '2035-11-18T08:00:00Z', cijenaFeninga: 0, rokUplate: null },
			{ id: 'b2', resurs: 'Sala za jogu', organizacija: 'Teretana Ilidža', zona: 'Europe/Sarajevo', pocetak: '2035-11-20T07:00:00Z', kraj: '2035-11-20T08:00:00Z', status: 'PENDING', rokOtkazivanja: '2035-11-20T07:00:00Z', cijenaFeninga: 1500, rokUplate: '2035-11-17T10:30:00Z' },
		],
		stranica: 1,
		ukupnoStranica: 2,
	})
})

test('otkazivanje je POST na rezervaciju i vraća novi status', async () => {
	const fetch = vi.fn(async () =>
		new Response(JSON.stringify({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'CANCELLED', expiresAt: null }), {
			status: 200,
			headers: { 'Content-Type': 'application/json' },
		}),
	)
	vi.stubGlobal('fetch', fetch)

	expect((await otkazi('b1')).status).toBe('CANCELLED')
	expect(fetch.mock.calls[0][0]).toBe('/api/bookings/b1/cancel')
	expect(fetch.mock.calls[0][1].method).toBe('POST')
})

// BookingSeriesResponse iz BookingSeriesApiTest: rezervacije po redu sedmica, svaka kao BookingResponse
test('serija vraća rezervacije po sedmicama s istim poljima kao jedna rezervacija', async () => {
	const tijelo = {
		seriesId: 's1',
		bookings: [
			{ id: 'b1', resourceId: 'r1', startTime: '2036-03-24T08:00:00Z', endTime: '2036-03-24T09:00:00Z', status: 'PENDING', expiresAt: '2036-03-21T10:00:00Z' },
			{ id: 'b2', resourceId: 'r1', startTime: '2036-03-31T07:00:00Z', endTime: '2036-03-31T08:00:00Z', status: 'PENDING', expiresAt: '2036-03-21T10:00:00Z' },
		],
	}
	vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(tijelo), { status: 201, headers: { 'Content-Type': 'application/json' } })))

	const rezervacije = await rezervisiSeriju('r1', '2036-03-24T08:00:00Z', 2, 'k-2')

	expect(rezervacije.map((r) => r.pocetak)).toEqual(['2036-03-24T08:00:00Z', '2036-03-31T07:00:00Z'])
	expect(rezervacije[0]).toEqual({ id: 'b1', pocetak: '2036-03-24T08:00:00Z', kraj: '2036-03-24T09:00:00Z', status: 'PENDING', rokPotvrde: '2036-03-21T10:00:00Z' })
})

test('serija bez liste rezervacija padne glasno', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ seriesId: 's1' }), { status: 201, headers: { 'Content-Type': 'application/json' } })))

	await expect(rezervisiSeriju('r1', '2036-03-24T08:00:00Z', 2, 'k-2')).rejects.toThrow('/api/resources/r1/bookings/series: nema polja bookings')
})


// Oblik kao CheckInLinkResponse i BookingResponse u BookingPrelaziApiTest
test('link za dolazak je samo url, a upis dolaska šalje kod iz linka', async () => {
	const fetch = vi.fn(async (putanja) =>
		putanja.endsWith('/check-in-link')
			? new Response(JSON.stringify({ url: 'http://localhost:5173/check-in/b1#k=abc' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
			: new Response(JSON.stringify({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'COMPLETED', expiresAt: null }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await linkZaDolazak('b1')).toBe('http://localhost:5173/check-in/b1#k=abc')
	const upisana = await upisiDolazakSKodom('b1', 'abc')

	expect(fetch.mock.calls[0][0]).toBe('/api/bookings/b1/check-in-link')
	const [putanja, opcije] = fetch.mock.calls[1]
	expect(putanja).toBe('/api/bookings/b1/check-in/qr')
	expect(opcije.method).toBe('POST')
	expect(JSON.parse(opcije.body)).toEqual({ token: 'abc' })
	expect(upisana.status).toBe('COMPLETED')
})

// CheckoutResponse iz PaymentApiTest
test('plaćanje je POST na rezervaciju i vraća adresu Stripe Checkouta', async () => {
	const fetch = vi.fn(async () =>
		new Response(JSON.stringify({ url: 'https://checkout.stripe.com/c/pay/cs_test_1', expiresAt: '2035-11-17T10:31:00Z' }), {
			status: 200,
			headers: { 'Content-Type': 'application/json' },
		}),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await platiti('b2')).toBe('https://checkout.stripe.com/c/pay/cs_test_1')
	expect(fetch.mock.calls[0][0]).toBe('/api/bookings/b2/pay')
	expect(fetch.mock.calls[0][1].method).toBe('POST')
})

test('rezervacija bez priceCents je greška u API sloju, jer plaćena ne bi dobila dugme za plaćanje', async () => {
	vi.stubGlobal('fetch', vi.fn(async () =>
		new Response(
			JSON.stringify({
				content: [{ id: 'b2', resourceId: 'r2', resourceName: 'Sala za jogu', organizationName: 'Teretana Ilidža', timezone: 'Europe/Sarajevo', startTime: '2035-11-20T07:00:00Z', endTime: '2035-11-20T08:00:00Z', status: 'PENDING', cancelDeadline: '2035-11-20T07:00:00Z' }],
				page: 0, size: 20, totalElements: 1, totalPages: 1,
			}),
			{ status: 200, headers: { 'Content-Type': 'application/json' } },
		),
	))

	await expect(mojeRezervacije(0)).rejects.toThrow('priceCents')
})
