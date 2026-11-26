// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const clan = (id, ime, email, uloga) => ({ userId: id, displayName: ime, email, role: uloga, joinedAt: '2035-09-01T08:00:00Z' })
const stranica = (clanovi, broj = 0, ukupno = 1) => () => json({ content: clanovi, page: broj, size: 50, totalElements: clanovi.length, totalPages: ukupno })
const CLANOVI = '/api/organizations/o1/members?page=0&size=50'
const POZIVNICA = 'POST /api/organizations/o1/invites'
const OTVORENE = '/api/organizations/o1/invites?size=50'
const LINKOVI = '/api/organizations/o1/invite-links?size=20'
const lista = (sadrzaj) => () => json({ content: sadrzaj, page: 0, size: 50, totalElements: sadrzaj.length, totalPages: 1 })
const link = (dodatno = {}) => ({
	id: 'l1',
	url: 'http://localhost:5173/pridruzi/abc',
	maxUses: 50,
	uses: 12,
	expiresAt: '2035-12-01T09:30:00Z',
	revokedAt: null,
	createdAt: '2035-11-01T09:30:00Z',
	active: true,
	...dodatno,
})
// Ime prijavljene osobe piše i u zaglavlju, pa se ime traži samo u listi
const uListi = async () => within(await screen.findByRole('list', { name: 'Članovi' }))

function kaoAdmin(odgovori = {}) {
	return backend({
		...PRIJAVLJENA,
		[CLANOVI]: stranica([clan('u1', 'Hana Šehić', 'hana@primjer.ba', 'ADMIN'), clan('u7', 'Emir Žunić', 'emir@primjer.ba', 'PROVIDER')]),
		[OTVORENE]: lista([]),
		[LINKOVI]: lista([]),
		...odgovori,
	})
}

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

describe('lista članova', () => {
	test('admin sa stranice organizacije otvara članove: ime, email i uloga, a sebe vidi kao "ti"', async () => {
		kaoAdmin()
		otvori('/o/citaonica-pmf')

		await userEvent.setup().click(await screen.findByRole('link', { name: 'Članovi i pozivnice' }))

		const redovi = within(await screen.findByRole('list', { name: 'Članovi' })).getAllByRole('listitem')
		expect(redovi.map((li) => li.textContent)).toEqual([
			expect.stringMatching(/Hana Šehić \(ti\).*hana@primjer\.ba.*admin/),
			expect.stringMatching(/Emir Žunić.*emir@primjer\.ba.*vodi termine/),
		])
		expect(window.location.pathname).toBe('/o/citaonica-pmf/clanovi')
	})

	test('druga stranica je novi upit, a na zadnjoj se dalje ne može', async () => {
		const fetch = kaoAdmin({
			[CLANOVI]: stranica([clan('u1', 'Hana Šehić', 'hana@primjer.ba', 'ADMIN')], 0, 2),
			'/api/organizations/o1/members?page=1&size=50': stranica([clan('u9', 'Đana Mujić', 'djana@primjer.ba', 'CLIENT')], 1, 2),
		})
		otvori('/o/citaonica-pmf/clanovi')

		expect((await uListi()).getByText('Hana Šehić')).toBeTruthy()
		await userEvent.setup().click(screen.getByRole('button', { name: 'Sljedeća stranica' }))

		expect(await screen.findByText('Đana Mujić')).toBeTruthy()
		expect((await uListi()).queryByText('Hana Šehić')).toBeNull()
		expect(screen.getByRole('button', { name: 'Sljedeća stranica' }).disabled).toBe(true)
		expect(fetch.mock.calls.map(([p]) => p)).toContain('/api/organizations/o1/members?page=1&size=50')
	})

	test('admin koji je jedini član dobije uputu da pozove druge', async () => {
		kaoAdmin({ [CLANOVI]: stranica([clan('u1', 'Hana Šehić', 'hana@primjer.ba', 'ADMIN')]) })
		otvori('/o/citaonica-pmf/clanovi')

		expect(await screen.findByText(/Za sada si jedini član/)).toBeTruthy()
	})

	test('kad se lista ne učita, piše zašto i može se pokušati ponovo', async () => {
		let pokusaj = 0
		kaoAdmin({
			[CLANOVI]: () => (++pokusaj === 1 ? problem(500, { title: 'Greška' }) : stranica([clan('u1', 'Hana Šehić', 'hana@primjer.ba', 'ADMIN')])()),
		})
		otvori('/o/citaonica-pmf/clanovi')

		expect((await screen.findByRole('alert')).textContent).toMatch(/Članovi se nisu učitali/)
		await userEvent.setup().click(screen.getByRole('button', { name: 'Pokušaj ponovo' }))

		expect((await uListi()).getByText('Hana Šehić')).toBeTruthy()
	})

	test('ko nije admin ne vidi link, a na adresi dobije objašnjenje bez upita za članove', async () => {
		const fetch = kaoAdmin()
		otvori('/o/teretana-ilidza')
		await screen.findByRole('heading', { name: 'Teretana Ilidža' })
		expect(screen.queryByRole('link', { name: 'Članovi i pozivnice' })).toBeNull()
		cleanup()

		otvori('/o/teretana-ilidza/clanovi')

		expect(await screen.findByRole('heading', { name: 'Članovi nisu dostupni' })).toBeTruthy()
		expect(fetch.mock.calls.some(([p]) => p.includes('/members'))).toBe(false)
	})
})

describe('pozivnica', () => {
	test('poslana pozivnica kaže na koji email, s kojom ulogom i do kad važi po zoni organizacije', async () => {
		const fetch = kaoAdmin({
			[POZIVNICA]: (t) => json({ id: 'i1', email: t.email.toLowerCase(), role: t.role, expiresAt: '2035-11-29T09:30:00Z' }, 201),
		})
		otvori('/o/citaonica-pmf/clanovi')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'Lamija.Coric@primjer.ba')
		await korisnik.selectOptions(screen.getByLabelText('Uloga'), 'vodi termine')
		await korisnik.click(screen.getByRole('button', { name: 'Pošalji pozivnicu' }))

		const poruka = await screen.findByText(/^Pozivnica je poslana/)
		expect(poruka.textContent).toBe('Pozivnica je poslana na lamija.coric@primjer.ba, uloga: vodi termine. Rok: četvrtak, 29. novembar, 10:30.')
		expect(poruka.getAttribute('role')).toBe('status')
		expect(tijelo(fetch, POZIVNICA)).toEqual({ email: 'Lamija.Coric@primjer.ba', role: 'PROVIDER' })
		expect(screen.getByLabelText('Email').value).toBe('')
	})

	test('podrazumijevana uloga je ona s najmanje prava', async () => {
		const fetch = kaoAdmin({ [POZIVNICA]: () => json({ id: 'i1', email: 'a@primjer.ba', role: 'CLIENT', expiresAt: '2035-11-26T09:30:00Z' }, 201) })
		otvori('/o/citaonica-pmf/clanovi')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'a@primjer.ba')
		await korisnik.click(screen.getByRole('button', { name: 'Pošalji pozivnicu' }))

		await screen.findByText(/^Pozivnica je poslana/)
		expect(tijelo(fetch, POZIVNICA).role).toBe('CLIENT')
	})

	test('neispravan email: poruka iz 400 stoji ispod polja', async () => {
		kaoAdmin({
			[POZIVNICA]: () => problem(400, { title: 'Neispravan zahtjev', detail: 'Neka polja nisu ispravna.', greske: [{ polje: 'email', poruka: 'Email nije ispravan.' }] }),
		})
		otvori('/o/citaonica-pmf/clanovi')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'hana@')
		await korisnik.click(screen.getByRole('button', { name: 'Pošalji pozivnicu' }))

		expect(await screen.findByText('Email nije ispravan.')).toBeTruthy()
		expect(screen.getByLabelText('Email').getAttribute('aria-invalid')).toBe('true')
		expect(screen.getByLabelText('Email').value).toBe('hana@')
	})

	test('osoba koja je već član: 409 s porukom servera, email ostaje u polju', async () => {
		kaoAdmin({
			[POZIVNICA]: () => problem(409, { title: 'Već je član', detail: 'Osoba s ovim emailom je već član organizacije i nalazi se na listi članova.' }),
		})
		otvori('/o/citaonica-pmf/clanovi')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'emir@primjer.ba')
		await korisnik.click(screen.getByRole('button', { name: 'Pošalji pozivnicu' }))

		expect((await screen.findByRole('alert')).textContent).toBe('Osoba s ovim emailom je već član organizacije i nalazi se na listi članova.')
		expect(screen.getByLabelText('Email').value).toBe('emir@primjer.ba')
	})
})

describe('upravljanje članovima', () => {
	test('promjena uloge ide odmah na server i lista se osvježi', async () => {
		let uloga = 'PROVIDER'
		const fetch = kaoAdmin({
			[CLANOVI]: () => stranica([clan('u1', 'Hana Šehić', 'hana@primjer.ba', 'ADMIN'), clan('u7', 'Emir Žunić', 'emir@primjer.ba', uloga)])(),
			'PUT /api/organizations/o1/members/u7/role': (t) => {
				uloga = t.role
				return json(clan('u7', 'Emir Žunić', 'emir@primjer.ba', t.role))
			},
		})
		otvori('/o/citaonica-pmf/clanovi')

		await userEvent.setup().selectOptions(await screen.findByLabelText('Uloga za Emir Žunić'), 'admin')

		expect(tijelo(fetch, 'PUT /api/organizations/o1/members/u7/role')).toEqual({ role: 'ADMIN' })
		await vi.waitFor(() => expect(screen.getByLabelText('Uloga za Emir Žunić').value).toBe('ADMIN'))
	})

	// Poruka servera kaže šta prvo uraditi, a izbor se vrati na ulogu koju član stvarno ima
	test('409 jer član vodi resurse: poruka servera, a uloga ostaje stara', async () => {
		kaoAdmin({
			'PUT /api/organizations/o1/members/u7/role': () =>
				problem(409, { title: 'Član vodi resurse', detail: 'Ovaj član vodi resurse (Sto 9). Prvo ih predaj drugom članu u uređivanju resursa.', resursi: ['Sto 9'] }),
		})
		otvori('/o/citaonica-pmf/clanovi')

		await userEvent.setup().selectOptions(await screen.findByLabelText('Uloga za Emir Žunić'), 'rezerviše termine')

		expect((await screen.findByRole('alert')).textContent).toMatch(/vodi resurse \(Sto 9\)/)
		expect(screen.getByLabelText('Uloga za Emir Žunić').value).toBe('PROVIDER')
	})

	test('uklanjanje traži potvrdu, a sebe admin ne uklanja ovdje', async () => {
		const fetch = kaoAdmin({ 'DELETE /api/organizations/o1/members/u7': () => new Response(null, { status: 204 }) })
		otvori('/o/citaonica-pmf/clanovi')
		const korisnik = userEvent.setup()

		const clanovi = await uListi()
		expect(clanovi.getAllByRole('button', { name: 'Ukloni' })).toHaveLength(1)
		await korisnik.click(clanovi.getByRole('button', { name: 'Ukloni' }))
		expect(fetch.mock.calls.some(([, o]) => o.method === 'DELETE')).toBe(false)
		expect(screen.getByText(/Ukloniti Emir Žunić\? Buduće rezervacije se otkazuju/)).toBeTruthy()

		await korisnik.click(screen.getByRole('button', { name: 'Ukloni' }))
		await vi.waitFor(() =>
			expect(fetch.mock.calls.some(([p, o]) => o.method === 'DELETE' && p === '/api/organizations/o1/members/u7')).toBe(true),
		)
	})

	test('otvorena pozivnica se vidi s rokom i povlači se jednim klikom', async () => {
		let otvorene = [{ id: 'i1', email: 'lamija@primjer.ba', role: 'CLIENT', expiresAt: '2035-11-29T09:30:00Z' }]
		const fetch = kaoAdmin({
			[OTVORENE]: () => lista(otvorene)(),
			'DELETE /api/organizations/o1/invites/i1': () => {
				otvorene = []
				return new Response(null, { status: 204 })
			},
		})
		otvori('/o/citaonica-pmf/clanovi')

		const pozivnice = within(await screen.findByRole('list', { name: 'Otvorene pozivnice' }))
		expect(pozivnice.getByText(/važi do četvrtak, 29. novembar, 10:30/)).toBeTruthy()
		await userEvent.setup().click(pozivnice.getByRole('button', { name: 'Povuci pozivnicu' }))

		await vi.waitFor(() => expect(screen.queryByRole('list', { name: 'Otvorene pozivnice' })).toBeNull())
		expect(fetch.mock.calls.some(([p, o]) => o.method === 'DELETE' && p === '/api/organizations/o1/invites/i1')).toBe(true)
	})
})

describe('pozivni link', () => {
	test('aktivan link: QR, broj ulazaka, link za kopiranje i plakat', async () => {
		kaoAdmin({ [LINKOVI]: lista([link()]) })
		otvori('/o/citaonica-pmf/clanovi')

		expect(await screen.findByRole('img', { name: /^QR kod za ulazak u / })).toBeTruthy()
		expect(screen.getByText(/od 50 mjesta iskorišteno/).textContent).toBe('12 od 50 mjesta iskorišteno')
		expect(screen.getByLabelText('Link').value).toBe('http://localhost:5173/pridruzi/abc')
		expect(screen.getByRole('link', { name: 'Odštampaj plakat' }).getAttribute('href')).toBe('/o/citaonica-pmf/plakat')
	})

	test('novi link šalje broj mjesta i dana, a greška polja stoji ispod polja', async () => {
		let linkovi = []
		const fetch = kaoAdmin({
			[LINKOVI]: () => lista(linkovi)(),
			'POST /api/organizations/o1/invite-links': (t) => {
				if (t.maxUses > 1000) {
					return problem(400, { detail: 'Neka polja nisu ispravna.', greske: [{ polje: 'maxUses', poruka: 'Najviše 1000 ljudi po linku.' }] })
				}
				linkovi = [link({ maxUses: t.maxUses, uses: 0 })]
				return json(linkovi[0], 201)
			},
		})
		otvori('/o/citaonica-pmf/clanovi')
		const korisnik = userEvent.setup()

		const mjesta = await screen.findByLabelText('Koliko ljudi može ući')
		await korisnik.clear(mjesta)
		await korisnik.type(mjesta, '5000')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi link' }))
		expect(await screen.findByText('Najviše 1000 ljudi po linku.')).toBeTruthy()

		await korisnik.clear(mjesta)
		await korisnik.type(mjesta, '30')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi link' }))

		expect((await screen.findByText(/od 30 mjesta iskorišteno/)).textContent).toBe('0 od 30 mjesta iskorišteno')
		expect(tijelo(fetch, 'POST /api/organizations/o1/invite-links')).toEqual({ maxUses: 30, days: 30 })
	})

	test('povlačenje traži potvrdu i šalje DELETE', async () => {
		const fetch = kaoAdmin({
			[LINKOVI]: lista([link()]),
			'DELETE /api/organizations/o1/invite-links/l1': () => new Response(null, { status: 204 }),
		})
		otvori('/o/citaonica-pmf/clanovi')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Povuci link' }))
		expect(screen.getByText(/Plakat i poslani link prestaju raditi/)).toBeTruthy()
		await korisnik.click(screen.getByRole('button', { name: 'Povuci link' }))

		await vi.waitFor(() =>
			expect(fetch.mock.calls.some(([p, o]) => o.method === 'DELETE' && p === '/api/organizations/o1/invite-links/l1')).toBe(true),
		)
	})
})
