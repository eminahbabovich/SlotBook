import { afterEach, describe, expect, test, vi } from 'vitest'
import { ApiError, apiFetch } from './apiFetch.js'

function odgovori(status, tijelo, tip = 'application/json') {
	const fetch = vi.fn(async () => new Response(tijelo, { status, headers: tijelo === null ? {} : { 'Content-Type': tip } }))
	vi.stubGlobal('fetch', fetch)
	return fetch
}

async function greskaIz(obecanje) {
	try {
		await obecanje
	} catch (greska) {
		return greska
	}
	throw new Error('očekivana je greška, a poziv je prošao')
}

afterEach(() => {
	vi.unstubAllGlobals()
})

describe('apiFetch', () => {
	test('vraća JSON iz uspješnog odgovora i traži JSON od servera', async () => {
		const fetch = odgovori(200, JSON.stringify({ aplikacija: 'slotbook' }))

		expect(await apiFetch('/api/status')).toEqual({ aplikacija: 'slotbook' })
		const [putanja, opcije] = fetch.mock.calls[0]
		expect(putanja).toBe('/api/status')
		expect(opcije.method).toBe('GET')
		expect(opcije.headers.Accept).toContain('application/problem+json')
	})

	test('šalje tijelo kao JSON sa Content-Type zaglavljem', async () => {
		const fetch = odgovori(201, JSON.stringify({ id: 'b1' }))

		await apiFetch('/api/resources/r1/bookings', { metoda: 'POST', tijelo: { startTime: '2035-11-19T08:00:00+01:00' } })

		const [, opcije] = fetch.mock.calls[0]
		expect(opcije.method).toBe('POST')
		expect(opcije.headers['Content-Type']).toBe('application/json')
		expect(JSON.parse(opcije.body)).toEqual({ startTime: '2035-11-19T08:00:00+01:00' })
	})

	test('odgovor 204 bez tijela vraća null', async () => {
		odgovori(204, null)

		expect(await apiFetch('/api/resources/r1', { metoda: 'DELETE' })).toBeNull()
	})

	test('409 postaje ApiError sa statusom i Problem Detailsom iz tijela', async () => {
		const problem = { title: 'Conflict', status: 409, detail: 'Termin je u međuvremenu zauzet.' }
		odgovori(409, JSON.stringify(problem), 'application/problem+json')

		const greska = await greskaIz(apiFetch('/api/resources/r1/bookings', { metoda: 'POST', tijelo: {} }))

		expect(greska).toBeInstanceOf(ApiError)
		expect(greska.status).toBe(409)
		expect(greska.problem).toEqual(problem)
	})

	test('status se uzima iz HTTP odgovora, ne iz tijela', async () => {
		// Ovako je izgledao bug iz Faze 4: setProperty("status", ...) je pregazio broj.
		odgovori(409, JSON.stringify({ title: 'Conflict', status: 'CANCELLED' }), 'application/problem+json')

		const greska = await greskaIz(apiFetch('/api/bookings/b1/confirm', { metoda: 'POST' }))

		expect(greska.status).toBe(409)
	})

	test('400 zadržava greske po poljima za formu', async () => {
		const greske = [{ polje: 'email', poruka: 'Email nije ispravan.' }]
		odgovori(400, JSON.stringify({ title: 'Bad Request', status: 400, greske }), 'application/problem+json')

		const greska = await greskaIz(apiFetch('/api/auth/register', { metoda: 'POST', tijelo: {} }))

		expect(greska.problem.greske).toEqual(greske)
	})

	test('greška bez JSON tijela daje ApiError bez problema', async () => {
		odgovori(502, '<html>Bad Gateway</html>', 'text/html')

		const greska = await greskaIz(apiFetch('/api/status'))

		expect(greska.status).toBe(502)
		expect(greska.problem).toBeNull()
	})

	test('kad server ne odgovara, ApiError ima status 0', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))

		const greska = await greskaIz(apiFetch('/api/status'))

		expect(greska).toBeInstanceOf(ApiError)
		expect(greska.status).toBe(0)
		expect(greska.problem).toBeNull()
	})

	// Registracija vraća 202 bez tijela (Faza 8, korak 5), a ne 204
	test('prazan uspješan odgovor koji nije 204 vraća null umjesto greške', async () => {
		odgovori(202, null)

		expect(await apiFetch('/api/auth/register', { metoda: 'POST', tijelo: {} })).toBeNull()
	})

	test('429 nosi Retry-After u sekundama, da UI može reći kad je sljedeći pokušaj', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ status: 429, detail: 'Previše pokušaja.' }), {
			status: 429,
			headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '720' },
		})))

		const greska = await greskaIz(apiFetch('/api/auth/login', { metoda: 'POST', tijelo: {} }))

		expect(greska.status).toBe(429)
		expect(greska.retryAfter).toBe(720)
	})

	test('greška bez Retry-After nema ni retryAfter', async () => {
		odgovori(409, JSON.stringify({ status: 409, detail: 'Zauzeto.' }), 'application/problem+json')

		expect((await greskaIz(apiFetch('/api/x'))).retryAfter).toBeNull()
	})
})
