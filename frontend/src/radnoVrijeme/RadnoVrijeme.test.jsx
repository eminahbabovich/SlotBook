// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { CITAONICA, PRIJAVLJENA, TERETANA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const resurs = (id, naziv, providerId) => ({
	id, organizationId: 'o1', providerId, name: naziv, description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, active: true, hasImage: false,
})
const stranica = (lista) => json({ content: lista, page: 0, size: 50, totalElements: lista.length, totalPages: 1 })
const ADRESA = '/o/citaonica-pmf/r/r1/radno-vrijeme'
const PRAVILA = '/api/resources/r1/availability'
const IZUZECI = '/api/resources/r1/time-off?size=50'

// Hana je admin čitaonice. Sto 4 radi ponedjeljkom 08-12 i 13-16. Lažni backend pamti
// radno vrijeme i izuzetke, kao pravi.
function citaonica({ pravila = [{ dayOfWeek: 'MONDAY', start: '08:00:00', end: '12:00:00' }, { dayOfWeek: 'MONDAY', start: '13:00:00', end: '16:00:00' }], izuzeci = [], odgovori = {} } = {}) {
	const stanje = { pravila, izuzeci: [...izuzeci] }
	return backend({
		...PRIJAVLJENA,
		'/api/me/organizations': () => json([CITAONICA, TERETANA]),
		'/api/organizations/o1/resources?size=100': () => stranica([resurs('r1', 'Sto 4, tihi dio', 'u7')]),
		'/api/organizations/o2/resources?size=100': () => stranica([{ ...resurs('r9', 'Sala 1', 'u7'), organizationId: 'o2' }]),
		[`GET ${PRAVILA}`]: () => json({ timezone: 'Europe/Sarajevo', rules: stanje.pravila }),
		[`PUT ${PRAVILA}`]: (t) => {
			stanje.pravila = t.rules.map((p) => ({ ...p, start: `${p.start}:00`, end: `${p.end}:00` }))
			return json({ timezone: 'Europe/Sarajevo', rules: stanje.pravila })
		},
		[IZUZECI]: () => stranica(stanje.izuzeci),
		'POST /api/resources/r1/time-off': (t) => {
			const novi = { id: `t${stanje.izuzeci.length + 1}`, startsAt: new Date(t.startsAt).toISOString().replace('.000', ''), endsAt: new Date(t.endsAt).toISOString().replace('.000', ''), reason: t.reason }
			stanje.izuzeci.push(novi)
			return json(novi, 201)
		},
		'DELETE /api/resources/r1/time-off/t1': () => {
			stanje.izuzeci = stanje.izuzeci.filter((i) => i.id !== 't1')
			return new Response(null, { status: 204 })
		},
		...odgovori,
	})
}

const dan = (naziv) => screen.getByRole('group', { name: naziv })
const postavi = (polje, vrijednost) => fireEvent.change(polje, { target: { value: vrijednost } })

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

describe('radno vrijeme', () => {
	test('do radnog vremena se dolazi iz izmjene resursa, a sedmica pokazuje intervale po danima', async () => {
		citaonica()
		otvori('/o/citaonica-pmf/r/r1/uredi')

		await userEvent.setup().click(await screen.findByRole('link', { name: 'Radno vrijeme i izuzeci' }))

		await screen.findByRole('heading', { name: 'Radno vrijeme: Sto 4, tihi dio' })
		expect(within(dan('ponedjeljak')).getByLabelText('ponedjeljak, 1. interval, od').value).toBe('08:00')
		expect(within(dan('ponedjeljak')).getByLabelText('ponedjeljak, 2. interval, do').value).toBe('16:00')
		expect(within(dan('utorak')).getByText('Ne radi.')).toBeTruthy()
		expect(screen.getByText(/Europe\/Sarajevo/)).toBeTruthy()
	})

	test('dodan subotnji interval i uklonjen popodnevni ponedjeljak idu jednim PUT-om, po redu dana', async () => {
		const fetch = citaonica()
		otvori(ADRESA)
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Ukloni ponedjeljak, 2. interval' }))
		await korisnik.click(screen.getByRole('button', { name: 'Dodaj interval: subota' }))
		postavi(within(dan('subota')).getByLabelText('subota, 1. interval, od'), '09:00')
		postavi(within(dan('subota')).getByLabelText('subota, 1. interval, do'), '13:00')
		await korisnik.click(screen.getByRole('button', { name: 'Sačuvaj radno vrijeme' }))

		expect(await screen.findByText(/^Radno vrijeme je sačuvano/)).toBeTruthy()
		expect(tijelo(fetch, `PUT ${PRAVILA}`)).toEqual({
			rules: [
				{ dayOfWeek: 'MONDAY', start: '08:00', end: '12:00' },
				{ dayOfWeek: 'SATURDAY', start: '09:00', end: '13:00' },
			],
		})
	})

	test('greška za rules[1] iz 400 stoji ispod drugog intervala, ne ispod prvog', async () => {
		citaonica({
			odgovori: {
				[`PUT ${PRAVILA}`]: () =>
					problem(400, {
						detail: 'Neki podaci nisu ispravni. Ispravi označena polja i pokušaj ponovo.',
						greske: [{ polje: 'rules[1]', poruka: 'Ovaj interval se preklapa s drugim intervalom istog dana.' }],
					}),
			},
		})
		otvori(ADRESA)
		const korisnik = userEvent.setup()

		postavi(await screen.findByLabelText('ponedjeljak, 2. interval, od'), '11:00')
		await korisnik.click(screen.getByRole('button', { name: 'Sačuvaj radno vrijeme' }))

		const drugi = await screen.findByRole('group', { name: 'ponedjeljak, 2. interval' })
		expect(within(drugi).getByText('Ovaj interval se preklapa s drugim intervalom istog dana.')).toBeTruthy()
		expect(within(screen.getByRole('group', { name: 'ponedjeljak, 1. interval' })).queryByText(/preklapa/)).toBeNull()
	})

	test('prazan sat se ne šalje, nego ga forma sama označi', async () => {
		const fetch = citaonica()
		otvori(ADRESA)

		postavi(await screen.findByLabelText('ponedjeljak, 1. interval, do'), '')
		await userEvent.setup().click(screen.getByRole('button', { name: 'Sačuvaj radno vrijeme' }))

		expect(within(screen.getByRole('group', { name: 'ponedjeljak, 1. interval' })).getByText('Unesi početak i kraj.')).toBeTruthy()
		expect(fetch.mock.calls.some(([, o]) => o.method === 'PUT')).toBe(false)
	})

	test('ko ne vodi resurs dobije objašnjenje, a radno vrijeme se i ne traži', async () => {
		const fetch = citaonica()
		otvori('/o/teretana-ilidza/r/r9/radno-vrijeme')

		expect(await screen.findByRole('heading', { name: 'Radno vrijeme nije dostupno' })).toBeTruthy()
		expect(fetch.mock.calls.some(([p]) => p.includes('/availability') || p.includes('/time-off'))).toBe(false)
	})
})

describe('izuzeci', () => {
	test('novi izuzetak preko Nove godine ide s pomakom zone, a lista ga pokazuje po Sarajevu', async () => {
		const fetch = citaonica()
		otvori(ADRESA)
		const korisnik = userEvent.setup()

		expect(await screen.findByText(/Nema izuzetaka/)).toBeTruthy()
		postavi(screen.getByLabelText('Od dana'), '2026-12-31')
		postavi(screen.getByLabelText('Do dana'), '2027-01-02')
		await korisnik.type(screen.getByLabelText('Razlog'), 'Praznici')
		await korisnik.click(screen.getByRole('button', { name: 'Dodaj izuzetak' }))

		const lista = await screen.findByRole('list', { name: 'Izuzeci' })
		expect(within(lista).getByText(/četvrtak, 31\. decembar, 00:00 do subota, 2\. januar, 00:00/)).toBeTruthy()
		expect(within(lista).getByText('Praznici')).toBeTruthy()
		expect(tijelo(fetch, 'POST /api/resources/r1/time-off')).toEqual({
			startsAt: '2026-12-31T00:00:00+01:00',
			endsAt: '2027-01-02T00:00:00+01:00',
			reason: 'Praznici',
		})
	})

	test('sat koji ne postoji na dan prelaska na ljetno forma odbije prije slanja', async () => {
		const fetch = citaonica()
		otvori(ADRESA)

		postavi(await screen.findByLabelText('Od dana'), '2027-03-28')
		postavi(screen.getByLabelText('Od sata'), '02:30')
		postavi(screen.getByLabelText('Do dana'), '2027-03-29')
		await userEvent.setup().click(screen.getByRole('button', { name: 'Dodaj izuzetak' }))

		expect(await screen.findByText(/02:30 tog dana ne postoji/)).toBeTruthy()
		expect(screen.getByLabelText('Od sata').getAttribute('aria-invalid')).toBe('true')
		expect(fetch.mock.calls.some(([p, o]) => o.method === 'POST' && p.includes('time-off'))).toBe(false)
	})

	test('kraj prije početka: poruka iz 400 za endsAt stoji ispod kraja', async () => {
		citaonica({
			odgovori: {
				'POST /api/resources/r1/time-off': () =>
					problem(400, { detail: 'Neki podaci nisu ispravni. Ispravi označena polja i pokušaj ponovo.', greske: [{ polje: 'endsAt', poruka: 'Kraj izuzetka mora biti poslije početka.' }] }),
			},
		})
		otvori(ADRESA)

		postavi(await screen.findByLabelText('Od dana'), '2026-12-27')
		postavi(screen.getByLabelText('Do dana'), '2026-12-24')
		await userEvent.setup().click(screen.getByRole('button', { name: 'Dodaj izuzetak' }))

		expect(await screen.findByText('Kraj izuzetka mora biti poslije početka.')).toBeTruthy()
		expect(screen.getByLabelText('Do dana').getAttribute('aria-invalid')).toBe('true')
	})

	test('uklanjanje izbaci izuzetak iz liste, a već uklonjen (404) se računa kao uklonjen', async () => {
		citaonica({
			izuzeci: [
				{ id: 't1', startsAt: '2026-12-23T23:00:00Z', endsAt: '2026-12-26T23:00:00Z', reason: 'Godišnji' },
				{ id: 't2', startsAt: '2027-01-05T07:00:00Z', endsAt: '2027-01-05T11:00:00Z', reason: null },
			],
			odgovori: { 'DELETE /api/resources/r1/time-off/t2': () => problem(404, { detail: 'Izuzetak ne postoji.' }) },
		})
		otvori(ADRESA)
		const korisnik = userEvent.setup()

		const lista = await screen.findByRole('list', { name: 'Izuzeci' })
		await korisnik.click(within(lista).getAllByRole('button', { name: /^Ukloni izuzetak/ })[0])

		expect(await screen.findByText('Izuzetak je uklonjen.')).toBeTruthy()
		expect(screen.queryByText('Godišnji')).toBeNull()
		await korisnik.click(screen.getByRole('button', { name: /^Ukloni izuzetak/ }))
		expect(screen.queryByRole('alert')).toBeNull()
	})
})
