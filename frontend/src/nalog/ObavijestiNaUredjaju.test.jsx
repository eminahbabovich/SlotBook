// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'
import * as uredjaj from '../obavijesti/uredjaj.js'

// Browser je lažan: jsdom nema service worker ni push. Testira se šta stranica radi s onim što
// browser vrati, a pravi push na telefonu je provjeren ručno (docs/ODLUKE.md).
vi.mock('../obavijesti/uredjaj.js', () => ({
	podrzava: vi.fn(() => true),
	naIPhoneu: vi.fn(() => false),
	dozvola: vi.fn(() => 'default'),
	opisUredjaja: vi.fn(() => 'Telefon, Chrome'),
	trenutnaPretplata: vi.fn(async () => null),
	pretplataAkoPostoji: vi.fn(async () => null),
	pretplati: vi.fn(),
}))

const ENDPOINT = 'https://fcm.googleapis.com/fcm/send/hana-telefon'
const pretplata = () => ({
	endpoint: ENDPOINT,
	toJSON: () => ({ endpoint: ENDPOINT, expirationTime: null, keys: { p256dh: 'BKljuc', auth: 'tajna' } }),
	unsubscribe: vi.fn(async () => true),
})
const UKLJUCEN = { '/api/push/public-key': () => json({ enabled: true, publicKey: 'BJavni' }) }
const PROBA = 'POST /api/me/push-subscriptions/test'

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
	vi.clearAllMocks()
})

test('uključivanje traži dozvolu, šalje pretplatu serveru i nudi probnu obavijest', async () => {
	uredjaj.pretplati.mockResolvedValue(pretplata())
	const fetch = backend({
		...PRIJAVLJENA,
		...UKLJUCEN,
		'PUT /api/me/push-subscriptions': () => json({ id: 'p1', device: 'Telefon, Chrome', createdAt: '2035-11-19T08:00:00Z' }),
		[PROBA]: () => new Response(null, { status: 204 }),
	})
	otvori('/nalog')
	const korisnik = userEvent.setup()

	expect(await screen.findByText('Isključene su na ovom uređaju.')).toBeTruthy()
	await korisnik.click(screen.getByRole('button', { name: 'Uključi obavijesti' }))

	expect(await screen.findByText('Uključene su na ovom uređaju.')).toBeTruthy()
	expect(uredjaj.pretplati).toHaveBeenCalledWith('BJavni')
	expect(tijelo(fetch, 'PUT /api/me/push-subscriptions')).toEqual({
		endpoint: ENDPOINT,
		keys: { p256dh: 'BKljuc', auth: 'tajna' },
		device: 'Telefon, Chrome',
	})

	await korisnik.click(screen.getByRole('button', { name: 'Pošalji probnu obavijest' }))
	expect(await screen.findByText(/Probna obavijest je poslana/)).toBeTruthy()
	expect(tijelo(fetch, PROBA)).toEqual({ endpoint: ENDPOINT })
})

// Browser već ima pretplatu, možda od osobe koja je prije bila prijavljena: server je dobije pod Haninim nalogom
test('postojeća pretplata browsera se veže za prijavljenu osobu, a isključivanje je briše i kod push servisa', async () => {
	const postojeca = pretplata()
	uredjaj.trenutnaPretplata.mockResolvedValue(postojeca)
	const fetch = backend({
		...PRIJAVLJENA,
		...UKLJUCEN,
		'PUT /api/me/push-subscriptions': () => json({ id: 'p1', device: 'Telefon, Chrome', createdAt: '2035-11-19T08:00:00Z' }),
		[`DELETE /api/me/push-subscriptions?endpoint=${encodeURIComponent(ENDPOINT)}`]: () => new Response(null, { status: 204 }),
	})
	otvori('/nalog')
	const korisnik = userEvent.setup()

	expect(await screen.findByText('Uključene su na ovom uređaju.')).toBeTruthy()
	expect(tijelo(fetch, 'PUT /api/me/push-subscriptions').endpoint).toBe(ENDPOINT)
	await korisnik.click(screen.getByRole('button', { name: 'Isključi obavijesti' }))

	expect(await screen.findByText('Obavijesti su isključene na ovom uređaju.')).toBeTruthy()
	expect(postojeca.unsubscribe).toHaveBeenCalled()
	expect(screen.getByRole('button', { name: 'Uključi obavijesti' })).toBeTruthy()
})

test('uređaj koji push servis više ne poznaje dobije poruku servera', async () => {
	uredjaj.trenutnaPretplata.mockResolvedValue(pretplata())
	backend({
		...PRIJAVLJENA,
		...UKLJUCEN,
		'PUT /api/me/push-subscriptions': () => json({ id: 'p1', device: null, createdAt: '2035-11-19T08:00:00Z' }),
		[PROBA]: () =>
			problem(404, { title: 'Uređaj nije prijavljen', detail: 'Ovaj uređaj više nije prijavljen za obavijesti. Isključi ih i uključi ponovo.' }),
	})
	otvori('/nalog')

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Pošalji probnu obavijest' }))

	expect((await screen.findByRole('alert')).textContent).toBe('Ovaj uređaj više nije prijavljen za obavijesti. Isključi ih i uključi ponovo.')
})

test('bez ključeva na serveru push se ne nudi', async () => {
	backend({ ...PRIJAVLJENA, '/api/push/public-key': () => json({ enabled: false, publicKey: null }) })
	otvori('/nalog')

	expect(await screen.findByText(/nisu uključene na serveru/)).toBeTruthy()
	expect(screen.queryByRole('button', { name: 'Uključi obavijesti' })).toBeNull()
})

test('na iPhoneu u Safariju piše kako dodati SlotBook na početni ekran', async () => {
	uredjaj.podrzava.mockReturnValue(false)
	uredjaj.naIPhoneu.mockReturnValue(true)
	backend(PRIJAVLJENA)
	otvori('/nalog')

	expect(await screen.findByText(/Dijeli, pa Dodaj na početni ekran \(iOS 16.4 ili noviji\)/)).toBeTruthy()
})

test('odjava ugasi obavijesti ovog browsera prije nego što se sesija zatvori', async () => {
	const postojeca = pretplata()
	uredjaj.podrzava.mockReturnValue(true)
	uredjaj.pretplataAkoPostoji.mockResolvedValue(postojeca)
	const fetch = backend({
		...PRIJAVLJENA,
		[`DELETE /api/me/push-subscriptions?endpoint=${encodeURIComponent(ENDPOINT)}`]: () => new Response(null, { status: 204 }),
		'POST /api/auth/logout': () => new Response(null, { status: 204 }),
	})
	otvori('/rezervacije')
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: 'Odjavi se' }))

	await screen.findByRole('heading', { name: /Prijava/ })
	const redoslijed = fetch.mock.calls.map(([p, o]) => `${o.method} ${p}`).filter((z) => z.includes('push') || z.includes('logout'))
	expect(redoslijed).toEqual([`DELETE /api/me/push-subscriptions?endpoint=${encodeURIComponent(ENDPOINT)}`, 'POST /api/auth/logout'])
	expect(postojeca.unsubscribe).toHaveBeenCalled()
})
