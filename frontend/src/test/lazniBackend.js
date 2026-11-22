import { render } from '@testing-library/react'
import { createElement } from 'react'
import { vi } from 'vitest'
import App from '../App.jsx'

export const problem = (status, tijelo) =>
	new Response(JSON.stringify({ status, ...tijelo }), { status, headers: { 'Content-Type': 'application/problem+json' } })
export const json = (tijelo, status = 200) => new Response(JSON.stringify(tijelo), { status, headers: { 'Content-Type': 'application/json' } })

export const HANA = { id: 'u1', email: 'hana@primjer.ba', displayName: 'Hana Šehić', emailVerified: false }
export const CITAONICA = { id: 'o1', name: 'Čitaonica PMF', slug: 'citaonica-pmf', timezone: 'Europe/Sarajevo', role: 'ADMIN' }
export const TERETANA = { id: 'o2', name: 'Teretana Ilidža', slug: 'teretana-ilidza', timezone: 'Europe/Sarajevo', role: 'CLIENT' }
export const BEZ_SESIJE = () => problem(401, { title: 'Sesija je istekla', detail: 'Sesija je istekla ili je prekinuta. Prijavi se ponovo.' })
export const TOKEN = () => json({ accessToken: 'tok', tokenType: 'Bearer', expiresIn: 900 })

// Odgovori po metodi i putanji ("GET /api/me"), ili samo po putanji. Funkcija dobije tijelo
// zahtjeva i zaglavlja, da test može provjeriti šta je poslano.
export function backend(odgovori) {
	const fetch = vi.fn(async (putanja, opcije) => {
		const odgovor = odgovori[`${opcije.method} ${putanja}`] ?? odgovori[putanja]
		if (odgovor === undefined) throw new Error(`test nema odgovor za ${opcije.method} ${putanja}`)
		return odgovor(opcije.body === undefined ? undefined : JSON.parse(opcije.body), opcije.headers)
	})
	vi.stubGlobal('fetch', fetch)
	return fetch
}

export const PRIJAVLJENA = {
	'/api/auth/refresh': TOKEN,
	'/api/me': () => json(HANA),
	'/api/me/organizations': () => json([CITAONICA, TERETANA]),
	'/api/organizations/o1/resources?size=100': () => json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
	'/api/organizations/o2/resources?size=100': () => json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
	'/api/waitlist/mine?page=0&size=20': () => json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
}

export function otvori(adresa) {
	window.history.pushState({}, '', adresa)
	render(createElement(App))
}

export const tijelo = (fetch, kljuc) => JSON.parse(fetch.mock.calls.findLast(([p, o]) => `${o.method} ${p}` === kljuc)[1].body)

