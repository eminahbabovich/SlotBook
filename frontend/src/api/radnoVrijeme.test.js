import { afterEach, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { izuzeciResursa, noviIzuzetak, radnoVrijeme, sacuvajRadnoVrijeme, ukloniIzuzetak } from './radnoVrijeme.js'

const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => {
	vi.unstubAllGlobals()
	postaviToken(null)
})

const poziv = (fetch, i = 0) => {
	const [putanja, opcije] = fetch.mock.calls[i]
	return { zahtjev: `${opcije.method} ${putanja}`, tijelo: opcije.body === undefined ? undefined : JSON.parse(opcije.body) }
}

// Oblik kao AvailabilityResponse u AvailabilityApiTest: LocalTime stiže sa sekundama
test('radno vrijeme dolazi sa zonom, a sati bez sekundi, kako ih traži polje za vrijeme', async () => {
	vi.stubGlobal('fetch', vi.fn(async () => json({ timezone: 'Europe/Sarajevo', rules: [{ dayOfWeek: 'MONDAY', start: '08:00:00', end: '12:30:00' }] })))

	expect(await radnoVrijeme('r1')).toEqual({ zona: 'Europe/Sarajevo', intervali: [{ dan: 'MONDAY', od: '08:00', do: '12:30' }] })
})

test('čuvanje šalje cijelu sedmicu u omotaču rules', async () => {
	const fetch = vi.fn(async () => json({ timezone: 'Europe/Sarajevo', rules: [] }))
	vi.stubGlobal('fetch', fetch)

	await sacuvajRadnoVrijeme('r1', [{ dan: 'SATURDAY', od: '09:00', do: '13:00' }])

	expect(poziv(fetch)).toEqual({ zahtjev: 'PUT /api/resources/r1/availability', tijelo: { rules: [{ dayOfWeek: 'SATURDAY', start: '09:00', end: '13:00' }] } })
})

// Oblik kao TimeOffResponse: trenuci u UTC-u
test('izuzeci dolaze kao trenuci i razlog, prva stranica do 50', async () => {
	const fetch = vi.fn(async () =>
		json({ content: [{ id: 't1', startsAt: '2026-12-23T23:00:00Z', endsAt: '2026-12-26T23:00:00Z', reason: null }], page: 0, size: 50, totalElements: 1, totalPages: 1 }),
	)
	vi.stubGlobal('fetch', fetch)

	expect(await izuzeciResursa('r1')).toEqual([{ id: 't1', od: '2026-12-23T23:00:00Z', do: '2026-12-26T23:00:00Z', razlog: null }])
	expect(fetch.mock.calls[0][0]).toBe('/api/resources/r1/time-off?size=50')
})

test('novi izuzetak šalje trenutke s pomakom kako su dati, a prazan razlog kao null', async () => {
	const fetch = vi.fn(async () => json({ id: 't1', startsAt: '2026-12-23T23:00:00Z', endsAt: '2026-12-26T23:00:00Z', reason: null }, 201))
	vi.stubGlobal('fetch', fetch)

	await noviIzuzetak('r1', { od: '2026-12-24T00:00:00+01:00', do: '2026-12-27T00:00:00+01:00', razlog: '' })

	expect(poziv(fetch)).toEqual({
		zahtjev: 'POST /api/resources/r1/time-off',
		tijelo: { startsAt: '2026-12-24T00:00:00+01:00', endsAt: '2026-12-27T00:00:00+01:00', reason: null },
	})
})

test('uklanjanje je DELETE na izuzetak tog resursa', async () => {
	const fetch = vi.fn(async () => new Response(null, { status: 204 }))
	vi.stubGlobal('fetch', fetch)

	await ukloniIzuzetak('r1', 't1')

	expect(poziv(fetch)).toEqual({ zahtjev: 'DELETE /api/resources/r1/time-off/t1', tijelo: undefined })
})
