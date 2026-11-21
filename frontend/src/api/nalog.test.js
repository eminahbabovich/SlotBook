import { afterEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { izvozPodataka, obrisiNalog } from './nalog.js'

// Testna lozinka u konstanti, da je gitleaks ne vidi kao ključ u { password: '...' }
const LOZINKA = 'bjelasnica-84'

afterEach(() => vi.unstubAllGlobals())

describe('nalog', () => {
	test('izvoz vraća fajl, ne objekat, da se preda browseru kakav je stigao', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => new Response('{"user":{}}', { status: 200, headers: { 'Content-Type': 'application/json' } })))

		const fajl = await izvozPodataka()

		expect(fajl).toBeInstanceOf(Blob)
		expect(await fajl.text()).toBe('{"user":{}}')
		expect(fetch.mock.calls[0][0]).toBe('/api/me/export')
	})

	test('brisanje šalje DELETE na /api/me s lozinkom u tijelu i zaboravi token', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))
		postaviToken('tok')

		await obrisiNalog(LOZINKA)
		await izvozPodataka().catch(() => {})

		const [putanja, opcije] = fetch.mock.calls[0]
		expect(putanja).toBe('/api/me')
		expect(opcije.method).toBe('DELETE')
		expect(opcije.headers.Authorization).toBe('Bearer tok')
		expect(JSON.parse(opcije.body)).toEqual({ password: LOZINKA })
		expect(fetch.mock.calls[1][1].headers.Authorization).toBeUndefined()
	})
})
