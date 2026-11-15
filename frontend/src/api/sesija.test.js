import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { ApiError, apiFetch, naIstekSesije, obnoviSesiju, postaviToken } from './apiFetch.js'

const json = (status, tijelo) =>
	new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/problem+json' } })

const nijePrijavljen = () => json(401, { title: 'Prijava je potrebna', status: 401 })

// Lažni backend: /api/auth/refresh odgovara kako test kaže, ostalo prihvata samo važeći token.
function backend({ vazeciToken = 'novi', refresh = () => json(200, { accessToken: 'novi' }) } = {}) {
	const fetch = vi.fn(async (putanja, opcije) => {
		if (putanja === '/api/auth/refresh') return refresh()
		if (opcije.headers.Authorization !== `Bearer ${vazeciToken}`) return nijePrijavljen()
		return json(200, { putanja })
	})
	vi.stubGlobal('fetch', fetch)
	return fetch
}

const pozivi = (fetch, putanja) => fetch.mock.calls.filter(([p]) => p === putanja)

beforeEach(() => {
	postaviToken(null)
	naIstekSesije(() => {})
})

afterEach(() => {
	vi.unstubAllGlobals()
})

describe('sesija', () => {
	test('uz zahtjev ide access token u Authorization zaglavlju', async () => {
		const fetch = backend({ vazeciToken: 'stari' })
		postaviToken('stari')

		await apiFetch('/api/me')

		expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer stari')
		expect(pozivi(fetch, '/api/auth/refresh')).toHaveLength(0)
	})

	test('na 401 obnovi token i ponovi zahtjev s novim tokenom', async () => {
		const fetch = backend()
		postaviToken('istekao')

		expect(await apiFetch('/api/me')).toEqual({ putanja: '/api/me' })
		expect(pozivi(fetch, '/api/auth/refresh')).toHaveLength(1)
		expect(fetch.mock.calls.at(-1)[1].headers.Authorization).toBe('Bearer novi')
	})

	test('tri paralelna 401 daju tačno jedan refresh, a sva tri zahtjeva prođu', async () => {
		let pustiRefresh
		const refreshCeka = new Promise((resolve) => {
			pustiRefresh = resolve
		})
		const fetch = backend({ refresh: () => refreshCeka.then(() => json(200, { accessToken: 'novi' })) })
		postaviToken('istekao')

		const zahtjevi = Promise.all([apiFetch('/api/me'), apiFetch('/api/organizations'), apiFetch('/api/bookings/mine')])
		await vi.waitFor(() => expect(pozivi(fetch, '/api/auth/refresh')).toHaveLength(1))
		pustiRefresh()

		expect(await zahtjevi).toHaveLength(3)
		expect(pozivi(fetch, '/api/auth/refresh')).toHaveLength(1)
	})

	test('401 koji stigne poslije obnove samo se ponovi, bez drugog refresha', async () => {
		let pustiKasni
		const kasniCeka = new Promise((resolve) => {
			pustiKasni = resolve
		})
		const obicni = backend()
		const fetch = vi.fn(async (putanja, opcije) => {
			if (putanja === '/api/kasni' && opcije.headers.Authorization === 'Bearer istekao') {
				return kasniCeka.then(nijePrijavljen)
			}
			return obicni(putanja, opcije)
		})
		vi.stubGlobal('fetch', fetch)
		postaviToken('istekao')

		const kasni = apiFetch('/api/kasni')
		await apiFetch('/api/me')
		pustiKasni()

		expect(await kasni).toEqual({ putanja: '/api/kasni' })
		expect(pozivi(fetch, '/api/auth/refresh')).toHaveLength(1)
	})

	test('kad refresh vrati 401, token se briše, javi se istek sesije i zahtjev baci 401', async () => {
		const fetch = backend({ refresh: () => json(401, { title: 'Sesija je istekla', status: 401 }) })
		const istek = vi.fn()
		naIstekSesije(istek)
		postaviToken('istekao')

		await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 401 })
		expect(istek).toHaveBeenCalledTimes(1)

		// Drugi zahtjev ide bez starog tokena i ne pokušava novi refresh.
		await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 401 })
		expect(pozivi(fetch, '/api/me').at(-1)[1].headers.Authorization).toBeUndefined()
		expect(pozivi(fetch, '/api/auth/refresh')).toHaveLength(1)
	})

	test('pogrešna lozinka (401 na /api/auth/login) ne pokreće refresh', async () => {
		const fetch = vi.fn(async () => json(401, { title: 'Prijava nije uspjela', status: 401 }))
		vi.stubGlobal('fetch', fetch)
		postaviToken('stari')

		await expect(apiFetch('/api/auth/login', { metoda: 'POST', tijelo: {} })).rejects.toBeInstanceOf(ApiError)
		expect(fetch).toHaveBeenCalledTimes(1)
	})

	test('zahtjev bez tokena na 401 ne pokreće refresh', async () => {
		const fetch = backend()

		await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 401 })
		expect(pozivi(fetch, '/api/auth/refresh')).toHaveLength(0)
	})

	test('obnoviSesiju pri otvaranju stranice vraća true kad kolačić važi', async () => {
		backend()

		expect(await obnoviSesiju()).toBe(true)
		expect(await apiFetch('/api/me')).toEqual({ putanja: '/api/me' })
	})

	test('kad server ne odgovara na refresh, sesija se ne prekida', async () => {
		backend({ refresh: () => Promise.reject(new TypeError('Failed to fetch')) })
		const istek = vi.fn()
		naIstekSesije(istek)
		postaviToken('istekao')

		await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 0 })
		expect(istek).not.toHaveBeenCalled()
	})
})
