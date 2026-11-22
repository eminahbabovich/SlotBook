// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { BEZ_SESIJE, PRIJAVLJENA, TOKEN, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const LINK = '/check-in/b1#k=kod-iz-qr-a'
const UPIS = 'POST /api/bookings/b1/check-in/qr'
const ZAVRSENA = () => json({ id: 'b1', resourceId: 'r1', startTime: '2035-11-19T08:00:00Z', endTime: '2035-11-19T09:00:00Z', status: 'COMPLETED', expiresAt: null })

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
})

test('provider otvori link iz QR koda, upiše dolazak, a kod iz linka ide backendu', async () => {
	const fetch = backend({ ...PRIJAVLJENA, [UPIS]: ZAVRSENA })
	otvori(LINK)

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Upiši dolazak' }))

	expect((await screen.findByRole('status')).textContent).toBe('Dolazak je upisan, rezervacija je završena.')
	expect(tijelo(fetch, UPIS)).toEqual({ token: 'kod-iz-qr-a' })
})

// Otvaranje linka ne smije samo upisati dolazak: pregled linka u chatu bi ga upisao bez klijenta
test('otvoren link ništa ne šalje dok se ne klikne', async () => {
	const fetch = backend({ ...PRIJAVLJENA, [UPIS]: ZAVRSENA })
	otvori(LINK)

	await screen.findByRole('button', { name: 'Upiši dolazak' })

	expect(fetch.mock.calls.some(([putanja]) => putanja.includes('check-in'))).toBe(false)
})

test('provider koji nije prijavljen se prijavi i vrati na link s kodom', async () => {
	backend({ ...PRIJAVLJENA, '/api/auth/refresh': BEZ_SESIJE, '/api/auth/login': TOKEN, [UPIS]: ZAVRSENA })
	otvori(LINK)
	const korisnik = userEvent.setup()

	await korisnik.type(await screen.findByLabelText('Email'), 'hana@primjer.ba')
	await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica1')
	await korisnik.click(screen.getByRole('button', { name: 'Prijavi se' }))

	expect(await screen.findByRole('button', { name: 'Upiši dolazak' })).toBeTruthy()
	expect(window.location.pathname + window.location.hash).toBe(LINK)
})

test('kod druge rezervacije: poruka iz polja token i bez dugmeta koje opet ne bi uspjelo', async () => {
	backend({
		...PRIJAVLJENA,
		[UPIS]: () => problem(400, { title: 'Neispravni podaci', detail: 'Neki podaci nisu ispravni.', greske: [{ polje: 'token', poruka: 'Ovaj QR kod ne pripada ovoj rezervaciji.' }] }),
	})
	otvori(LINK)

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Upiši dolazak' }))

	expect((await screen.findByRole('alert')).textContent).toBe('Ovaj QR kod ne pripada ovoj rezervaciji.')
	expect(screen.queryByRole('button', { name: 'Upiši dolazak' })).toBeNull()
})

test('ko ne vodi resurs dobije objašnjenje, ne opštu grešku', async () => {
	backend({ ...PRIJAVLJENA, [UPIS]: () => problem(403, { title: 'Nemaš pristup', detail: 'Nemaš pravo na ovu radnju.' }) })
	otvori(LINK)

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Upiši dolazak' }))

	expect((await screen.findByRole('alert')).textContent).toContain('Ovu rezervaciju ne vodiš ti')
})

// Prvi klik je prošao, a odgovor se izgubio. Drugi dobije 409 COMPLETED, i to je uspjeh.
test('ponovljen upis poslije pada mreže je uspjeh', async () => {
	let pokusaj = 0
	backend({
		...PRIJAVLJENA,
		[UPIS]: () => {
			pokusaj++
			if (pokusaj === 1) throw new TypeError('Failed to fetch')
			return problem(409, { title: 'Prelaz nije dozvoljen', detail: 'Rezervacija je već završena.', trenutniStatus: 'COMPLETED', trazeniStatus: 'COMPLETED' })
		},
	})
	otvori(LINK)
	const korisnik = userEvent.setup()

	await korisnik.click(await screen.findByRole('button', { name: 'Upiši dolazak' }))
	await korisnik.click(await screen.findByRole('button', { name: 'Pokušaj ponovo' }))

	expect((await screen.findByRole('status')).textContent).toBe('Dolazak je upisan, rezervacija je završena.')
})

test('otkazana rezervacija: dolazak se ne upisuje i piše zašto', async () => {
	backend({
		...PRIJAVLJENA,
		[UPIS]: () => problem(409, { title: 'Prelaz nije dozvoljen', detail: 'Prelaz nije dozvoljen.', trenutniStatus: 'CANCELLED', trazeniStatus: 'COMPLETED' }),
	})
	otvori(LINK)

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Upiši dolazak' }))

	expect((await screen.findByRole('alert')).textContent).toBe('Dolazak se ne može upisati, rezervacija je otkazana.')
})

test('link bez koda kaže šta fali i ne nudi upis', async () => {
	backend(PRIJAVLJENA)
	otvori('/check-in/b1')

	expect(await screen.findByText(/nedostaje mu kod iza znaka #/)).toBeTruthy()
	expect(screen.queryByRole('button', { name: 'Upiši dolazak' })).toBeNull()
})
