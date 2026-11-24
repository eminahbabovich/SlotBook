// @vitest-environment jsdom
import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { idiNaStripe } from '../rezervacije/idiNaStripe.js'
import { PRIJAVLJENA, backend, json, otvori, problem } from '../test/lazniBackend.js'

vi.mock('../rezervacije/idiNaStripe.js', () => ({ idiNaStripe: vi.fn() }))

// Sala za jogu u Čitaonici (o1 iz lažnog backenda), 15 KM po terminu, s automatskom potvrdom koja
// kod plaćenog ipak čeka uplatu
const SALA = { id: 'r1', organizationId: 'o1', providerId: 'u9', name: 'Sala za jogu', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity: 1, priceCents: 1500, active: true, hasImage: false }
const SLOTS = '/api/resources/r1/slots?from=2035-11-19&to=2035-11-26'
const STRIPE = 'https://checkout.stripe.com/c/pay/cs_test_b1'

// Ponedjeljak 19.11.2035. u 08 i 09 po Sarajevu. Rezervacija čeka uplatu do 07:30 (sada je 07:00).
function salaZaJogu(placanje = () => json({ url: STRIPE, expiresAt: '2035-11-19T06:31:00Z' })) {
	const slobodni = ['2035-11-19T07:00:00Z', '2035-11-19T08:00:00Z']
	const moji = []
	const sat = (start) => new Date(new Date(start).getTime() + 3600000).toISOString().replace('.000', '')
	return backend({
		...PRIJAVLJENA,
		'/api/organizations/o1/resources?size=100': () => json({ content: [SALA], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
		[SLOTS]: () =>
			json({
				timezone: 'Europe/Sarajevo',
				slots: slobodni.map((start) => ({ start, end: sat(start) })),
				taken: moji.map((start) => ({ start, end: sat(start), myStatus: 'PENDING' })),
			}),
		'POST /api/resources/r1/bookings': (tijelo) => {
			slobodni.splice(slobodni.indexOf(tijelo.startTime), 1)
			moji.push(tijelo.startTime)
			return json({ id: 'b1', resourceId: 'r1', startTime: tijelo.startTime, endTime: sat(tijelo.startTime), status: 'PENDING', expiresAt: '2035-11-19T06:30:00Z' }, 201)
		},
		'POST /api/bookings/b1/pay': placanje,
	})
}

const red = (vrijeme) => within(screen.getByRole('list', { name: /Termini/ })).getAllByRole('listitem').find((li) => li.textContent.startsWith(vrijeme))

beforeEach(() => {
	postaviToken(null)
	idiNaStripe.mockClear()
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-11-19T06:00:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

test('potvrda plaćenog termina kaže cijenu i ne nudi ponavljanje', async () => {
	salaZaJogu()
	otvori('/o/citaonica-pmf/r/r1')

	await userEvent.setup().click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ }))

	expect(screen.getByRole('group').textContent).toContain('Termin košta 15 KM i plaća se karticom odmah poslije rezervacije.')
	expect(screen.queryByLabelText('Ponavljanje')).toBeNull()
})

test('poslije rezervacije termin čeka uplatu, poruka kaže rok, a dugme vodi na Stripe', async () => {
	salaZaJogu()
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ }))
	await korisnik.click(screen.getByRole('button', { name: 'Rezerviši' }))

	expect((await screen.findByText(/čeka uplatu\. Rok/)).textContent).toBe(
		'Termin ponedjeljak, 19. novembar, 08:00 do 09:00 čeka uplatu. Rok za uplatu: ponedjeljak, 19. novembar, 07:30. Poslije toga se termin oslobađa.',
	)
	await waitFor(() => expect(red('08:00').textContent).toContain('čeka uplatu'))
	await korisnik.click(screen.getByRole('button', { name: 'Plati 15 KM' }))
	await waitFor(() => expect(idiNaStripe).toHaveBeenCalledWith(STRIPE))
})

test('kad rezervacija više ne čeka uplatu, poruka kaže zašto i dugme nestaje', async () => {
	salaZaJogu(() => problem(409, { title: 'Rezervacija ne čeka uplatu', detail: 'Ova rezervacija se ne plaća: besplatna je, već plaćena, otkazana ili joj je prošao rok za uplatu. Osvježi prikaz.' }))
	otvori('/o/citaonica-pmf/r/r1')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: /08:00 do 09:00, slobodno/ }))
	await korisnik.click(screen.getByRole('button', { name: 'Rezerviši' }))
	await korisnik.click(await screen.findByRole('button', { name: 'Plati 15 KM' }))

	expect((await screen.findByText(/Ova rezervacija se ne plaća/)).getAttribute('role')).toBe('status')
	expect(screen.queryByRole('button', { name: /Plati/ })).toBeNull()
	expect(idiNaStripe).not.toHaveBeenCalled()
})
