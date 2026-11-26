// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { CITAONICA, PRIJAVLJENA, TERETANA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

// Hana (u1) je admin čitaonice, a u konsultacijama vodi termine
const KONSULTACIJE = { id: 'o3', name: 'Konsultacije, prof. Đulić', slug: 'konsultacije-dulic', timezone: 'Europe/Sarajevo', role: 'PROVIDER' }
const resurs = (id, naziv, providerId, ostalo = {}) => ({
	id, organizationId: 'o1', providerId, name: naziv, description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity: 1, priceCents: 0, active: true, hasImage: false, ...ostalo,
})
const stranica = (lista) => json({ content: lista, page: 0, size: 100, totalElements: lista.length, totalPages: 1 })

// Lažni backend pamti resurse, kao pravi: novi se pojavi u listi, izmjena ostaje.
function organizacijeSResursima({ citaonica = [], konsultacije = [], odgovori = {} } = {}) {
	const liste = { o1: [...citaonica], o3: [...konsultacije] }
	const tacke = {
		...PRIJAVLJENA,
		'/api/me/organizations': () => json([CITAONICA, TERETANA, KONSULTACIJE]),
		'/api/organizations/o1/resources?size=100': () => stranica(liste.o1),
		'/api/organizations/o3/resources?size=100': () => stranica(liste.o3),
		'/api/resources/r-novi/availability': () => json({ timezone: 'Europe/Sarajevo', rules: [] }),
		'/api/resources/r-novi/time-off?size=50': () => stranica([]),
		'POST /api/organizations/o1/resources': (t) => {
			const novi = resurs('r-novi', t.name, 'u1', { description: t.description, slotMinutes: t.slotMinutes, autoConfirm: t.autoConfirm, cancelNoticeHours: t.cancelNoticeHours, capacity: t.capacity, priceCents: t.priceCents })
			liste.o1.push(novi)
			return json(novi, 201)
		},
	}
	for (const [org, lista] of Object.entries(liste)) {
		for (const r of lista) {
			tacke[`PUT /api/organizations/${org}/resources/${r.id}`] = (t) => {
				Object.assign(r, { name: t.name, description: t.description, slotMinutes: t.slotMinutes, autoConfirm: t.autoConfirm, cancelNoticeHours: t.cancelNoticeHours, capacity: t.capacity, priceCents: t.priceCents })
				return json(r)
			}
		}
	}
	return backend({ ...tacke, ...odgovori })
}

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

describe('novi resurs', () => {
	test('admin napravi resurs s ručnom potvrdom i rokom, i odmah upisuje njegovo radno vrijeme', async () => {
		const fetch = organizacijeSResursima()
		otvori('/o/citaonica-pmf')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('link', { name: 'Novi resurs' }))
		await korisnik.type(screen.getByLabelText('Naziv'), 'Grupna soba 2')
		await korisnik.type(screen.getByLabelText('Opis'), 'Za četvoro, s tablom.')
		await korisnik.clear(screen.getByLabelText('Trajanje termina u minutama'))
		await korisnik.type(screen.getByLabelText('Trajanje termina u minutama'), '90')
		await korisnik.selectOptions(screen.getByLabelText('Potvrda rezervacije'), 'ručno, potvrđuje ko vodi resurs')
		await korisnik.clear(screen.getByLabelText('Rok za otkazivanje u satima'))
		await korisnik.type(screen.getByLabelText('Rok za otkazivanje u satima'), '12')
		await korisnik.clear(screen.getByLabelText('Broj mjesta u terminu'))
		await korisnik.type(screen.getByLabelText('Broj mjesta u terminu'), '4')
		await korisnik.type(screen.getByLabelText('Cijena termina u KM'), '7,50')
		await korisnik.type(screen.getByLabelText('Najviše budućih rezervacija po osobi'), '2')
		// Bilježi svaki naslov koji se pojavi, i onaj koji odmah nestane
		const naslovi = []
		const posmatrac = new MutationObserver(() => document.querySelectorAll('h1').forEach((h) => naslovi.push(h.textContent)))
		posmatrac.observe(document.body, { childList: true, subtree: true })
		await korisnik.click(screen.getByRole('button', { name: 'Napravi resurs' }))

		expect(await screen.findByRole('heading', { name: 'Radno vrijeme: Grupna soba 2' })).toBeTruthy()
		posmatrac.disconnect()
		// Stranica traži resurs u listi. Bez osvježene liste bi na tren pisalo da nije dostupan.
		expect(naslovi).not.toContain('Radno vrijeme nije dostupno')
		expect(window.location.pathname).toBe('/o/citaonica-pmf/r/r-novi/radno-vrijeme')
		expect(tijelo(fetch, 'POST /api/organizations/o1/resources')).toEqual({
			name: 'Grupna soba 2', description: 'Za četvoro, s tablom.', slotMinutes: 90, autoConfirm: false, cancelNoticeHours: 12,
			capacity: 4, priceCents: 750, maxActivePerMember: 2, maxDaysAhead: null, minNoticeMinutes: null,
		})
	})

	test('greške iz 400 stoje ispod polja na koja se odnose', async () => {
		organizacijeSResursima({
			odgovori: {
				'POST /api/organizations/o1/resources': () =>
					problem(400, {
						detail: 'Neka polja nisu ispravna.',
						greske: [
							{ polje: 'name', poruka: 'Unesi naziv resursa, npr. Sala za funkcionalni trening.' },
							{ polje: 'slotMinutes', poruka: 'Termin traje najviše 480 minuta (8 sati).' },
						],
					}),
			},
		})
		otvori('/o/citaonica-pmf/resursi/novi')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Trajanje termina u minutama'), '0')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi resurs' }))

		expect(await screen.findByText('Unesi naziv resursa, npr. Sala za funkcionalni trening.')).toBeTruthy()
		expect(screen.getByLabelText('Naziv').getAttribute('aria-invalid')).toBe('true')
		expect(screen.getByLabelText('Trajanje termina u minutama').getAttribute('aria-invalid')).toBe('true')
		expect(window.location.pathname).toBe('/o/citaonica-pmf/resursi/novi')
	})

	test('cijena koja nije iznos u KM se ne šalje, nego piše šta treba upisati', async () => {
		const fetch = organizacijeSResursima()
		otvori('/o/citaonica-pmf/resursi/novi')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Naziv'), 'Sala za mali fudbal')
		await korisnik.type(screen.getByLabelText('Cijena termina u KM'), '15 KM')
		await korisnik.click(screen.getByRole('button', { name: 'Napravi resurs' }))

		expect(screen.getByText('Upiši cijenu kao broj, npr. 15 ili 7,50.')).toBeTruthy()
		expect(screen.getByLabelText('Cijena termina u KM').getAttribute('aria-invalid')).toBe('true')
		expect(fetch.mock.calls.some(([putanja, opcije]) => opcije?.method === 'POST' && putanja.endsWith('/resources'))).toBe(false)
	})

	test('ko rezerviše termine ne vidi link, a na adresi dobije objašnjenje', async () => {
		const fetch = organizacijeSResursima()
		otvori('/o/teretana-ilidza')
		await screen.findByRole('heading', { name: 'Teretana Ilidža' })
		expect(screen.queryByRole('link', { name: 'Novi resurs' })).toBeNull()
		cleanup()

		otvori('/o/teretana-ilidza/resursi/novi')

		expect(await screen.findByRole('heading', { name: 'Resurs ne možeš dodati' })).toBeTruthy()
		expect(screen.queryByRole('button', { name: 'Napravi resurs' })).toBeNull()
		expect(fetch.mock.calls.some(([p, o]) => o.method === 'POST' && p.includes('/resources'))).toBe(false)
	})
})

describe('izmjena', () => {
	test('osoba koja vodi resurs vidi popunjenu formu, a izmjena šalje i polja koja nisu dirana', async () => {
		const fetch = organizacijeSResursima({ konsultacije: [resurs('r5', 'Termin konsultacija', 'u1', { autoConfirm: false, cancelNoticeHours: 24, capacity: 3, priceCents: 1500, description: 'Kabinet 214' })] })
		otvori('/o/konsultacije-dulic')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('link', { name: 'Uredi: Termin konsultacija' }))
		expect(screen.getByRole('heading', { name: 'Izmjena: Termin konsultacija' })).toBeTruthy()
		expect(screen.getByLabelText('Naziv').value).toBe('Termin konsultacija')
		expect(screen.getByLabelText('Opis').value).toBe('Kabinet 214')
		expect(screen.getByLabelText('Rok za otkazivanje u satima').value).toBe('24')
		expect(screen.getByLabelText('Broj mjesta u terminu').value).toBe('3')
		expect(screen.getByLabelText('Cijena termina u KM').value).toBe('15')
		await korisnik.clear(screen.getByLabelText('Trajanje termina u minutama'))
		await korisnik.type(screen.getByLabelText('Trajanje termina u minutama'), '30')
		await korisnik.click(screen.getByRole('button', { name: 'Sačuvaj promjene' }))

		expect((await screen.findByText(/^Promjene su sačuvane/)).getAttribute('role')).toBe('status')
		expect(tijelo(fetch, 'PUT /api/organizations/o3/resources/r5')).toEqual({
			name: 'Termin konsultacija', description: 'Kabinet 214', slotMinutes: 30, autoConfirm: false, cancelNoticeHours: 24,
			capacity: 3, priceCents: 1500, maxActivePerMember: null, maxDaysAhead: null, minNoticeMinutes: null,
		})
		await korisnik.click(screen.getByRole('link', { name: 'Konsultacije, prof. Đulić' }))
		expect(await screen.findByText('termini po 30 min')).toBeTruthy()
	})

	test('provider ne vidi "Uredi" za tuđi resurs, a na adresi dobije objašnjenje', async () => {
		organizacijeSResursima({ konsultacije: [resurs('r6', 'Sala za odbranu', 'u7')] })
		otvori('/o/konsultacije-dulic')
		await screen.findByRole('link', { name: /Sala za odbranu/ })
		expect(screen.queryByRole('link', { name: 'Uredi: Sala za odbranu' })).toBeNull()
		cleanup()

		otvori('/o/konsultacije-dulic/r/r6/uredi')

		expect(await screen.findByRole('heading', { name: 'Izmjena nije dostupna' })).toBeTruthy()
		expect(screen.queryByRole('button', { name: 'Sačuvaj promjene' })).toBeNull()
	})

	test('admin vidi i deaktivirane resurse, označene tekstom', async () => {
		organizacijeSResursima({ citaonica: [resurs('r1', 'Sto 4, tihi dio', 'u7'), resurs('r2', 'Stara sala', 'u7', { active: false })] })
		otvori('/o/citaonica-pmf')

		// Red resursa je stavka liste oko njegovog naziva (prva lista na stranici je put do nje)
		const stara = (await screen.findByText('Stara sala')).closest('li')
		expect(stara.textContent).toMatch(/deaktiviran/)
		const sto = screen.getByText('Sto 4, tihi dio').closest('li')
		expect(sto.textContent).not.toMatch(/deaktiviran/)
	})
})

const clan = (id, ime, uloga) => ({ userId: id, displayName: ime, email: `${id}@primjer.ba`, role: uloga, joinedAt: '2035-09-01T08:00:00Z' })
const CLANOVI_CITAONICE = () =>
	json({
		content: [clan('u1', 'Hana Šehić', 'ADMIN'), clan('u7', 'Emir Žunić', 'PROVIDER'), clan('u9', 'Đana Mujić', 'CLIENT')],
		page: 0, size: 100, totalElements: 3, totalPages: 1,
	})

// Admin čitaonice uređuje sto koji vodi Hana. Lažni backend pamti deaktivaciju i novog voditelja.
function sto(odgovori = {}) {
	const r = resurs('r1', 'Sto 4, tihi dio', 'u1')
	return organizacijeSResursima({
		citaonica: [r],
		odgovori: {
			'DELETE /api/organizations/o1/resources/r1': () => {
				r.active = false
				return new Response(null, { status: 204 })
			},
			'PUT /api/organizations/o1/resources/r1/provider': (t) => {
				r.providerId = t.providerId
				return json(r)
			},
			'/api/organizations/o1/members?page=0&size=100': CLANOVI_CITAONICE,
			...odgovori,
		},
	})
}

describe('deaktivacija', () => {
	test('poslije potvrde resurs je deaktiviran, dugmeta više nema, a u listi piše deaktiviran', async () => {
		const fetch = sto()
		otvori('/o/citaonica-pmf/r/r1/uredi')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Deaktiviraj resurs' }))
		// Fokus je na odustajanju, da Enter ne potvrdi nešto što se ne može vratiti
		expect(document.activeElement.textContent).toBe('Odustani')
		await korisnik.click(within(screen.getByRole('group', { name: 'Potvrda deaktivacije' })).getByRole('button', { name: 'Da, deaktiviraj' }))

		expect(await screen.findByRole('heading', { name: 'Resurs je deaktiviran' })).toBeTruthy()
		expect(screen.queryByRole('button', { name: 'Deaktiviraj resurs' })).toBeNull()
		expect(fetch.mock.calls.filter(([, o]) => o.method === 'DELETE')).toHaveLength(1)
		await korisnik.click(screen.getByRole('link', { name: 'Čitaonica PMF' }))
		expect(await screen.findByText(/deaktiviran, termini po 60 min/)).toBeTruthy()
	})

	test('odustajanje ne šalje ništa i vraća dugme', async () => {
		const fetch = sto()
		otvori('/o/citaonica-pmf/r/r1/uredi')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Deaktiviraj resurs' }))
		await korisnik.click(screen.getByRole('button', { name: 'Odustani' }))

		expect(screen.getByRole('button', { name: 'Deaktiviraj resurs' })).toBeTruthy()
		expect(fetch.mock.calls.some(([, o]) => o.method === 'DELETE')).toBe(false)
	})

	test('već deaktiviran resurs nema dugme za deaktivaciju', async () => {
		organizacijeSResursima({ citaonica: [resurs('r1', 'Stara sala', 'u1', { active: false })] })
		otvori('/o/citaonica-pmf/r/r1/uredi')

		expect(await screen.findByRole('heading', { name: 'Resurs je deaktiviran' })).toBeTruthy()
		expect(screen.queryByRole('button', { name: 'Deaktiviraj resurs' })).toBeNull()
	})
})

describe('ko vodi resurs', () => {
	test('admin bira samo između admina i onih koji vode termine, i resurs dobije novu osobu', async () => {
		const fetch = sto()
		otvori('/o/citaonica-pmf/r/r1/uredi')
		const korisnik = userEvent.setup()

		const izbor = await screen.findByLabelText('Osoba koja vodi resurs')
		expect([...izbor.options].map((o) => o.textContent)).toEqual(['Hana Šehić (admin)', 'Emir Žunić (vodi termine)'])
		expect(izbor.value).toBe('u1')
		await korisnik.selectOptions(izbor, 'Emir Žunić (vodi termine)')
		await korisnik.click(screen.getByRole('button', { name: 'Promijeni osobu' }))

		expect(await screen.findByText('Resurs sada vodi Emir Žunić.')).toBeTruthy()
		expect(tijelo(fetch, 'PUT /api/organizations/o1/resources/r1/provider')).toEqual({ providerId: 'u7' })
	})

	test('400 od servera (npr. osoba je u međuvremenu postala klijent) stoji uz izbor', async () => {
		sto({
			'PUT /api/organizations/o1/resources/r1/provider': () =>
				problem(400, { detail: 'Neki podaci nisu ispravni. Ispravi označena polja i pokušaj ponovo.', greske: [{ polje: 'providerId', poruka: 'Resurs može voditi samo član organizacije s ulogom PROVIDER ili ADMIN.' }] }),
		})
		otvori('/o/citaonica-pmf/r/r1/uredi')
		const korisnik = userEvent.setup()

		await korisnik.selectOptions(await screen.findByLabelText('Osoba koja vodi resurs'), 'Emir Žunić (vodi termine)')
		await korisnik.click(screen.getByRole('button', { name: 'Promijeni osobu' }))

		expect(await screen.findByText('Resurs može voditi samo član organizacije s ulogom PROVIDER ili ADMIN.')).toBeTruthy()
	})

	test('provider na svom resursu ne mijenja voditelja i ne traži listu članova', async () => {
		const fetch = organizacijeSResursima({ konsultacije: [resurs('r5', 'Termin konsultacija', 'u1')] })
		otvori('/o/konsultacije-dulic/r/r5/uredi')

		await screen.findByRole('button', { name: 'Sačuvaj promjene' })

		expect(screen.queryByLabelText('Osoba koja vodi resurs')).toBeNull()
		expect(screen.getByText(/Resurs vodiš ti/)).toBeTruthy()
		expect(fetch.mock.calls.some(([p]) => p.includes('/members'))).toBe(false)
	})
})
