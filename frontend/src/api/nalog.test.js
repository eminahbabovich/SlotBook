import { afterEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { adresaKalendara, izvozPodataka, linkZaKalendar, noviLinkZaKalendar, obrisiNalog } from './nalog.js'

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

const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

describe('link za kalendar', () => {
	test('odgovor CalendarFeedResponse postaje polja koja UI čita, a tokena bez POST-a nema', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => json({ active: true, createdAt: '2035-11-19T07:00:00Z', token: null })))

		expect(await linkZaKalendar()).toEqual({ aktivan: true, napravljen: '2035-11-19T07:00:00Z', token: null })
	})

	// Bez tokena bi UI pokazao link ".../calendar.ics?token=null", koji izgleda ispravno, a vraća 404
	test('POST bez tokena padne glasno umjesto da pokaže neispravan link', async () => {
		vi.stubGlobal('fetch', vi.fn(async () => json({ active: true, createdAt: '2035-11-19T07:00:00Z', token: null }, 201)))

		await expect(noviLinkZaKalendar()).rejects.toThrow('/api/me/calendar-feed: nema polja token')
	})

	test('adresa je na domenu frontenda, a token se kodira za URL', () => {
		expect(adresaKalendara('a+b/c', { origin: 'https://slotbook.primjer.ba' })).toBe(
			'https://slotbook.primjer.ba/api/me/calendar.ics?token=a%2Bb%2Fc',
		)
	})
})
