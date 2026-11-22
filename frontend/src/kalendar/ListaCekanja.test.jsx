// @vitest-environment jsdom
import { cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const STO = { id: 'r1', organizationId: 'o1', providerId: 'u9', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, active: true, hasImage: false }
const SLOTS = '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26'

// Ponedjeljak 19.11.2035: 08 slobodno, 09 ponuđen meni do 07:30, 10 tuđe, 11 tuđe i na listi sam.
// Lažni backend pamti upis i ispis, kao pravi.
function kalendar(upis = () => json({ id: 'w3', status: 'WAITING' }, 201)) {
	const naListi = { '2035-11-19T10:00:00Z': 'w2' }
	return backend({
		...PRIJAVLJENA,
		'/api/organizations/o1/resources?size=100': () => json({ content: [STO], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
		[SLOTS]: () =>
			json({
				timezone: 'Europe/Sarajevo',
				slots: [
					{ start: '2035-11-19T07:00:00Z', end: '2035-11-19T08:00:00Z' },
					{ start: '2035-11-19T08:00:00Z', end: '2035-11-19T09:00:00Z' },
				],
				taken: ['2035-11-19T09:00:00Z', '2035-11-19T10:00:00Z'].map((start) => ({
					start,
					end: start.replace(/T(\d\d)/, (_, h) => `T${String(Number(h) + 1).padStart(2, '0')}`),
					myStatus: null,
					myWaitlistId: naListi[start] ?? null,
				})),
				offeredToMe: [{ start: '2035-11-19T08:00:00Z', end: '2035-11-19T09:00:00Z', offeredUntil: '2035-11-19T06:30:00Z', waitlistId: 'w1' }],
			}),
		'POST /api/resources/r1/waitlist': async (zahtjev) => {
			const odgovor = await upis(zahtjev)
			if (odgovor.status === 201) naListi[zahtjev.startTime] = 'w3'
			return odgovor
		},
		'DELETE /api/waitlist/w2': () => {
			delete naListi['2035-11-19T10:00:00Z']
			return new Response(null, { status: 204 })
		},
	})
}

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-11-19T06:00:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

test('svako stanje liste čekanja piše i riječima, a čitač ekrana čita cijelu rečenicu', async () => {
	kalendar()
	otvori('/o/citaonica-pmf/r/r1')

	expect(await screen.findByRole('button', { name: 'ponedjeljak, 19. novembar, 09:00 do 10:00, ponuđen tebi danas do 07:30' })).toBeTruthy()
	expect(screen.getByRole('button', { name: 'ponedjeljak, 19. novembar, 10:00 do 11:00, zauzeto' })).toBeTruthy()
	expect(screen.getByRole('button', { name: 'ponedjeljak, 19. novembar, 11:00 do 12:00, zauzeto, na listi si' })).toBeTruthy()
})

test('na zauzet termin se upisuje na listu, i kalendar onda kaže da sam na listi', async () => {
	const fetch = kalendar()
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /10:00 do 11:00, zauzeto$/ }))
	expect(screen.getByRole('group', { name: 'Termin ponedjeljak, 19. novembar, 10:00 do 11:00 je zauzet.' })).toBeTruthy()
	await korisnik.click(screen.getByRole('button', { name: 'Javi mi ako se oslobodi' }))

	expect((await screen.findByRole('status')).textContent).toMatch(/^Na listi čekanja si za ponedjeljak, 19. novembar, 10:00 do 11:00\./)
	expect(tijelo(fetch, 'POST /api/resources/r1/waitlist')).toEqual({ startTime: '2035-11-19T09:00:00Z' })
	expect(await screen.findByRole('button', { name: /10:00 do 11:00, zauzeto, na listi si$/ })).toBeTruthy()
})

test('sa liste se ispisuje iz istog reda, a fokus ide na poruku', async () => {
	const fetch = kalendar()
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /11:00 do 12:00, zauzeto, na listi si$/ }))
	await korisnik.click(screen.getByRole('button', { name: 'Ispiši me sa liste' }))

	const poruka = await screen.findByText('Više nisi na listi čekanja za ponedjeljak, 19. novembar, 11:00 do 12:00.')
	expect(document.activeElement).toBe(poruka)
	expect(fetch.mock.calls.some(([putanja, o]) => putanja === '/api/waitlist/w2' && o.method === 'DELETE')).toBe(true)
	expect(await screen.findByRole('button', { name: /11:00 do 12:00, zauzeto$/ })).toBeTruthy()
})

test('kad se termin u međuvremenu oslobodio, poruka backenda kaže da ga rezervišem', async () => {
	kalendar(() => problem(409, { title: 'Termin je slobodan', detail: 'Ovaj termin je slobodan, pa lista čekanja ne treba. Rezerviši ga direktno.', razlog: 'SLOBODAN' }))
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /10:00 do 11:00, zauzeto$/ }))
	await korisnik.click(screen.getByRole('button', { name: 'Javi mi ako se oslobodi' }))

	expect((await screen.findByRole('alert')).textContent).toBe('Ovaj termin je slobodan, pa lista čekanja ne treba. Rezerviši ga direktno.')
})

test('ponuđen termin se rezerviše kao slobodan, uz rok ponude u potvrdi', async () => {
	kalendar()
	otvori('/o/citaonica-pmf/r/r1')

	await userEvent.setup().click(await screen.findByRole('button', { name: /09:00 do 10:00, ponuđen tebi danas do 07:30$/ }))

	expect(screen.getByRole('group', { name: 'Rezervisati ponedjeljak, 19. novembar, 09:00 do 10:00?' })).toBeTruthy()
	expect(screen.getByText(/ponuđen je tebi s liste čekanja danas do 07:30/)).toBeTruthy()
	await waitFor(() => expect(document.activeElement.textContent).toBe('Rezerviši'))
})
