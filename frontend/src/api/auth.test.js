import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from './apiFetch.js'
import { mojNalog, noviLinkZaPotvrdu, odjava, potvrdiEmail, prijava, registracija } from './auth.js'

const json = (status, tijelo) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

const KORISNIK = { id: 'u1', email: 'hana@primjer.ba', displayName: 'Hana Šehić', emailVerified: false }

function backend() {
	const fetch = vi.fn(async (putanja) => {
		if (putanja === '/api/auth/register') return new Response(null, { status: 202 })
		if (putanja === '/api/auth/verify-email' || putanja === '/api/auth/email-verification') return new Response(null, { status: 204 })
		if (putanja === '/api/auth/login') return json(200, { accessToken: 'tok', tokenType: 'Bearer', expiresIn: 900 })
		if (putanja === '/api/auth/logout') return new Response(null, { status: 204 })
		return json(200, KORISNIK)
	})
	vi.stubGlobal('fetch', fetch)
	return fetch
}

const tijelo = (fetch, putanja) => JSON.parse(fetch.mock.calls.find(([p]) => p === putanja)[1].body)

beforeEach(() => postaviToken(null))
afterEach(() => vi.unstubAllGlobals())

describe('auth', () => {
	test('prijava šalje email i password, a sljedeći zahtjev nosi dobijeni token', async () => {
		const fetch = backend()

		await prijava('hana@primjer.ba', 'čitaonica1')
		await mojNalog()

		expect(tijelo(fetch, '/api/auth/login')).toEqual({ email: 'hana@primjer.ba', password: 'čitaonica1' })
		expect(fetch.mock.calls.at(-1)[1].headers.Authorization).toBe('Bearer tok')
	})

	// Prijava radi tek poslije potvrde emaila, pa registracija ne prijavljuje
	test('registracija šalje displayName i ne pokušava prijavu', async () => {
		const fetch = backend()

		await registracija('Hana Šehić', 'hana@primjer.ba', 'čitaonica1')

		expect(tijelo(fetch, '/api/auth/register')).toEqual({ email: 'hana@primjer.ba', password: 'čitaonica1', displayName: 'Hana Šehić' })
		expect(fetch.mock.calls.map(([p]) => p)).toEqual(['/api/auth/register'])
	})

	test('potvrda emaila šalje token iz linka, a novi link samo email', async () => {
		const fetch = backend()

		await potvrdiEmail('tok-iz-maila')
		await noviLinkZaPotvrdu('hana@primjer.ba')

		expect(tijelo(fetch, '/api/auth/verify-email')).toEqual({ token: 'tok-iz-maila' })
		expect(tijelo(fetch, '/api/auth/email-verification')).toEqual({ email: 'hana@primjer.ba' })
	})

	test('odjava briše token kad je server potvrdi', async () => {
		const fetch = backend()
		await prijava('hana@primjer.ba', 'čitaonica1')

		await odjava()
		await mojNalog()

		expect(fetch.mock.calls.at(-1)[1].headers.Authorization).toBeUndefined()
	})

	test('kad odjava ne uspije, token ostaje, jer refresh kolačić ostaje u browseru', async () => {
		const fetch = backend()
		await prijava('hana@primjer.ba', 'čitaonica1')
		fetch.mockImplementationOnce(async () => Promise.reject(new TypeError('Failed to fetch')))

		await expect(odjava()).rejects.toMatchObject({ status: 0 })
		await mojNalog()

		expect(fetch.mock.calls.at(-1)[1].headers.Authorization).toBe('Bearer tok')
	})

	test('mojNalog vraća polja koja UI koristi, a bez emailVerified pada glasno', async () => {
		const fetch = backend()
		expect(await mojNalog()).toEqual({ id: 'u1', email: 'hana@primjer.ba', ime: 'Hana Šehić', emailPotvrden: false })

		fetch.mockImplementationOnce(async () => json(200, { id: 'u1', email: 'hana@primjer.ba', displayName: 'Hana Šehić' }))
		await expect(mojNalog()).rejects.toThrow('/api/me: nema polja emailVerified')
	})
})
