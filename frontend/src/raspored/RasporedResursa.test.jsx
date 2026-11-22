// @vitest-environment jsdom
import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { CITAONICA, PRIJAVLJENA, TERETANA, backend, json, otvori, problem } from '../test/lazniBackend.js'

const resurs = (id, naziv, providerId) => ({ id, organizationId: 'o1', providerId, name: naziv, description: null, slotMinutes: 60, autoConfirm: false, cancelNoticeHours: 24, capacity: 1, active: true, hasImage: false })
const RESURSI = (...r) => () => json({ content: r, page: 0, size: 100, totalElements: r.length, totalPages: 1 })
const rez = (id, start, end, status, klijent, rok = null) => ({ id, startTime: start, endTime: end, status, clientName: klijent, expiresAt: rok })

// Sada je ponedjeljak 19.11.2035. u 10:30 po Sarajevu. Hana je imala termin od 09:00,
// Lamija čeka potvrdu za 11:00, Emir dolazi u 13:00, Đana je otkazala.
const DAN = () => [
	rez('b1', '2035-11-19T08:00:00Z', '2035-11-19T09:00:00Z', 'CONFIRMED', 'Hana Šehić'),
	rez('b2', '2035-11-19T10:00:00Z', '2035-11-19T11:00:00Z', 'PENDING', 'Lamija Ćorić', '2035-11-18T10:00:00Z'),
	rez('b3', '2035-11-19T12:00:00Z', '2035-11-19T13:00:00Z', 'CONFIRMED', 'Emir Žunić'),
	rez('b4', '2035-11-19T13:00:00Z', '2035-11-19T14:00:00Z', 'CANCELLED', 'Đana Mujić'),
]
const ADRESA = '/api/resources/r1/bookings?from=2035-11-19&to=2035-11-20'

// Admin čitaonice. Lažni backend pamti promjene statusa, kao pravi.
function kaoAdmin(promjena = () => null) {
	const rezervacije = DAN()
	const novi = { confirm: 'CONFIRMED', reject: 'REJECTED', 'check-in': 'COMPLETED', 'no-show': 'NO_SHOW' }
	const odgovori = {
		...PRIJAVLJENA,
		'/api/organizations/o1/resources?size=100': RESURSI(resurs('r1', 'Termin konsultacija', 'u7')),
		[ADRESA]: () => json(rezervacije),
		'/api/resources/r1/bookings?from=2035-11-20&to=2035-11-21': () => json([]),
	}
	for (const r of rezervacije) {
		for (const [akcija, status] of Object.entries(novi)) {
			odgovori[`POST /api/bookings/${r.id}/${akcija}`] = async () => {
				const odgovor = await promjena(akcija)
				if (odgovor !== null) return odgovor
				r.status = status
				return json({ id: r.id, resourceId: 'r1', startTime: r.startTime, endTime: r.endTime, status, expiresAt: null })
			}
		}
	}
	return backend(odgovori)
}

const redSa = (ime) => screen.getAllByRole('listitem').find((li) => li.textContent.includes(ime))
const promjene = (fetch) => fetch.mock.calls.map(([p]) => p).filter((p) => /^\/api\/bookings\/b\d\//.test(p))

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-11-19T09:30:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

describe('pregled', () => {
	test('admin iz organizacije otvara raspored dana: ko dolazi, kad i u kom je stanju', async () => {
		kaoAdmin()
		otvori('/o/citaonica-pmf')

		await userEvent.setup().click(await screen.findByRole('link', { name: 'Raspored: Termin konsultacija' }))

		expect(await screen.findByRole('heading', { name: 'ponedjeljak, 19. novembar' })).toBeTruthy()
		const redovi = within(await screen.findByRole('list', { name: 'Rezervacije' })).getAllByRole('listitem')
		expect(redovi.map((li) => li.querySelector('time').textContent)).toEqual(['09:00 do 10:00', '11:00 do 12:00', '13:00 do 14:00', '14:00 do 15:00'])
		expect(within(redSa('Lamija')).getByText('čeka potvrdu')).toBeTruthy()
		expect(within(redSa('Đana')).getByText('otkazana')).toBeTruthy()
		expect(window.location.pathname).toBe('/o/citaonica-pmf/r/r1/raspored')
	})

	test('sljedeći dan je novi upit, a prazan dan kaže da nema rezervacija', async () => {
		const fetch = kaoAdmin()
		otvori('/o/citaonica-pmf/r/r1/raspored')

		await screen.findByRole('list', { name: 'Rezervacije' })
		await userEvent.setup().click(screen.getByRole('button', { name: 'Sljedeći dan' }))

		expect(await screen.findByText('Za ovaj dan nema rezervacija.')).toBeTruthy()
		expect(screen.getByRole('heading', { name: 'utorak, 20. novembar' })).toBeTruthy()
		expect(fetch.mock.calls.some(([p]) => p === '/api/resources/r1/bookings?from=2035-11-20&to=2035-11-21')).toBe(true)
	})
})

describe('ko vidi raspored', () => {
	test('provider vidi raspored samo za resurs koji vodi', async () => {
		backend({
			...PRIJAVLJENA,
			'/api/me/organizations': () => json([{ ...CITAONICA, role: 'PROVIDER' }]),
			'/api/organizations/o1/resources?size=100': RESURSI(resurs('r1', 'Moje konsultacije', 'u1'), resurs('r2', 'Tuđe konsultacije', 'u7')),
		})
		otvori('/o/citaonica-pmf')

		expect(await screen.findByRole('link', { name: 'Raspored: Moje konsultacije' })).toBeTruthy()
		expect(screen.queryByRole('link', { name: 'Raspored: Tuđe konsultacije' })).toBeNull()
	})

	test('klijent na adresi rasporeda dobije objašnjenje, a raspored se i ne traži', async () => {
		const fetch = backend({
			...PRIJAVLJENA,
			'/api/organizations/o2/resources?size=100': RESURSI({ ...resurs('r9', 'Sala 1', 'u7'), organizationId: 'o2' }),
		})
		otvori(`/o/${TERETANA.slug}/r/r9/raspored`)

		expect(await screen.findByRole('heading', { name: 'Raspored nije dostupan' })).toBeTruthy()
		expect(screen.getByRole('link', { name: 'Kalendar: Sala 1' })).toBeTruthy()
		expect(fetch.mock.calls.some(([p]) => p.includes('/bookings'))).toBe(false)
	})
})

describe('promjene', () => {
	test('potvrda zahtjeva ide odmah i lista pokaže novo stanje', async () => {
		const fetch = kaoAdmin()
		otvori('/o/citaonica-pmf/r/r1/raspored')
		const korisnik = userEvent.setup()

		await korisnik.click(within(await waitFor(() => redSa('Lamija'))).getByRole('button', { name: 'Potvrdi' }))

		expect((await screen.findByText(/Rezervacija potvrđena/)).textContent).toBe('Rezervacija potvrđena: Lamija Ćorić, 11:00 do 12:00.')
		await waitFor(() => expect(within(redSa('Lamija')).getByText('potvrđena')).toBeTruthy())
		expect(promjene(fetch)).toEqual(['/api/bookings/b2/confirm'])
	})

	test('odbijanje traži potvrdu, a "Ne" ništa ne šalje', async () => {
		const fetch = kaoAdmin()
		otvori('/o/citaonica-pmf/r/r1/raspored')
		const korisnik = userEvent.setup()

		await korisnik.click(within(await waitFor(() => redSa('Lamija'))).getByRole('button', { name: 'Odbij' }))
		await korisnik.click(within(redSa('Lamija')).getByRole('button', { name: 'Ne' }))
		expect(promjene(fetch)).toEqual([])
		await korisnik.click(within(redSa('Lamija')).getByRole('button', { name: 'Odbij' }))
		await korisnik.click(within(redSa('Lamija')).getByRole('button', { name: 'Da, odbij' }))

		expect(await screen.findByText(/Zahtjev odbijen/)).toBeTruthy()
		expect(promjene(fetch)).toEqual(['/api/bookings/b2/reject'])
	})

	test('dolazak i nedolazak se upisuju tek kad termin počne', async () => {
		const fetch = kaoAdmin()
		otvori('/o/citaonica-pmf/r/r1/raspored')
		const korisnik = userEvent.setup()

		const emir = await waitFor(() => redSa('Emir'))
		expect(within(emir).queryByRole('button', { name: 'Upiši nedolazak' })).toBeNull()
		expect(within(emir).getByText('Dolazak se upisuje kad termin počne.')).toBeTruthy()
		await korisnik.click(within(redSa('Hana')).getByRole('button', { name: 'Upiši nedolazak' }))
		await korisnik.click(within(redSa('Hana')).getByRole('button', { name: 'Da, nije došla osoba' }))

		expect(await screen.findByText(/Nedolazak upisan/)).toBeTruthy()
		expect(promjene(fetch)).toEqual(['/api/bookings/b1/no-show'])
	})

	test('kad je neko drugi već promijenio rezervaciju, piše poruka backenda i lista se osvježi', async () => {
		const fetch = kaoAdmin(async () =>
			problem(409, { title: 'Promjena nije moguća', detail: 'Ova promjena nije moguća jer je rezervacija otkazana. Osvježi prikaz.', trenutniStatus: 'CANCELLED', trazeniStatus: 'CONFIRMED' }),
		)
		otvori('/o/citaonica-pmf/r/r1/raspored')

		await userEvent.setup().click(within(await waitFor(() => redSa('Lamija'))).getByRole('button', { name: 'Potvrdi' }))

		expect((await within(redSa('Lamija')).findByRole('alert')).textContent).toMatch(/rezervacija otkazana/)
		await waitFor(() => expect(fetch.mock.calls.filter(([p]) => p === ADRESA).length).toBe(2))
	})
})
