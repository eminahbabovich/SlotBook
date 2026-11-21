// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { BEZ_SESIJE, backend, otvori, problem, tijelo } from '../test/lazniBackend.js'

const NEPRIJAVLJENA = { '/api/auth/refresh': BEZ_SESIJE }
const PRAZNO = (status) => () => new Response(null, { status })

beforeEach(() => postaviToken(null))
afterEach(() => {
	cleanup()
	vi.unstubAllGlobals()
	vi.useRealTimers()
})

async function popuniRegistraciju(korisnik) {
	await korisnik.type(await screen.findByLabelText('Ime koje drugi vide'), 'Hana Šehić')
	await korisnik.type(screen.getByLabelText('Email'), 'hana@primjer.ba')
	await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica1')
	await korisnik.click(screen.getByRole('button', { name: 'Napravi nalog' }))
}

describe('registracija', () => {
	// Prijava odmah poslije registracije bi dobila 401, jer email još nije potvrđen
	test('poslije 202 pokaže gdje je otišao mail i ne pokušava prijavu', async () => {
		const fetch = backend({ ...NEPRIJAVLJENA, 'POST /api/auth/register': PRAZNO(202) })
		otvori('/registracija')

		await popuniRegistraciju(userEvent.setup())

		expect(await screen.findByRole('heading', { name: 'Pogledaj inbox' })).toBeTruthy()
		expect(screen.getByText(/hana@primjer\.ba/)).toBeTruthy()
		expect(screen.getByText(/24 sata/)).toBeTruthy()
		expect(fetch.mock.calls.some(([putanja]) => putanja === '/api/auth/login')).toBe(false)
	})

	test('novi link ide na isti email, a poruka ne tvrdi da je mail sigurno poslan', async () => {
		const fetch = backend({
			...NEPRIJAVLJENA,
			'POST /api/auth/register': PRAZNO(202),
			'POST /api/auth/email-verification': PRAZNO(204),
		})
		otvori('/registracija')
		const korisnik = userEvent.setup()
		await popuniRegistraciju(korisnik)

		await korisnik.click(await screen.findByRole('button', { name: 'Pošalji novi link' }))

		expect(await screen.findByText(/Ako nalog s emailom hana@primjer\.ba čeka potvrdu/)).toBeTruthy()
		expect(tijelo(fetch, 'POST /api/auth/email-verification')).toEqual({ email: 'hana@primjer.ba' })
	})
})

describe('potvrda emaila', () => {
	// Potvrda tek na klik, kao pozivnica: StrictMode bi pri otvaranju poslao dva zahtjeva, a drugi dobio 409
	test('link iz maila potvrdi email na klik i vodi na prijavu', async () => {
		const fetch = backend({ ...NEPRIJAVLJENA, 'POST /api/auth/verify-email': PRAZNO(204) })
		otvori('/potvrda-emaila#token=Qx81mZ')

		await userEvent.setup().click(await screen.findByRole('button', { name: 'Potvrdi email' }))

		expect(await screen.findByText('Email je potvrđen. Sada se možeš prijaviti.')).toBeTruthy()
		expect(screen.getByRole('link', { name: 'Prijavi se' }).getAttribute('href')).toBe('/prijava')
		expect(tijelo(fetch, 'POST /api/auth/verify-email')).toEqual({ token: 'Qx81mZ' })
	})

	test('istekao link pokaže poruku backenda i formu za novi link', async () => {
		const istekao = 'Link za potvrdu emaila je istekao. Zatraži novi link na stranici za prijavu.'
		const fetch = backend({
			...NEPRIJAVLJENA,
			'POST /api/auth/verify-email': () => problem(410, { detail: istekao }),
			'POST /api/auth/email-verification': PRAZNO(204),
		})
		otvori('/potvrda-emaila#token=Qx81mZ')
		const korisnik = userEvent.setup()

		await korisnik.click(await screen.findByRole('button', { name: 'Potvrdi email' }))
		expect(await screen.findByText(istekao)).toBeTruthy()
		await korisnik.type(screen.getByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.click(screen.getByRole('button', { name: 'Pošalji novi link' }))

		expect(await screen.findByText(/Ako nalog s emailom hana@primjer\.ba čeka potvrdu/)).toBeTruthy()
		expect(tijelo(fetch, 'POST /api/auth/email-verification')).toEqual({ email: 'hana@primjer.ba' })
	})

	test('bez tokena odmah nudi novi link', async () => {
		backend(NEPRIJAVLJENA)
		otvori('/potvrda-emaila')

		expect(await screen.findByRole('button', { name: 'Pošalji novi link' })).toBeTruthy()
		expect(screen.queryByRole('button', { name: 'Potvrdi email' })).toBeNull()
	})
})

describe('prijava', () => {
	test('zaključana prijava kaže zašto i u koliko sati je sljedeći pokušaj', async () => {
		vi.useFakeTimers({ toFake: ['Date'] })
		vi.setSystemTime(new Date('2035-11-19T13:20:00Z'))
		backend({
			...NEPRIJAVLJENA,
			'POST /api/auth/login': () =>
				new Response(JSON.stringify({ status: 429, detail: 'Prijava je zaključana na 15 minuta. Pokušaj ponovo kasnije.' }), {
					status: 429,
					headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '720' },
				}),
		})
		otvori('/prijava')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.type(screen.getByLabelText('Lozinka'), 'pogresna')
		await korisnik.click(screen.getByRole('button', { name: 'Prijavi se' }))

		const poruka = await screen.findByRole('alert')
		expect(poruka.textContent).toMatch(/zaključana na 15 minuta/)
		// Sat u zoni browsera. Test ne zna zonu mašine, pa provjerava samo oblik HH:MM.
		expect(poruka.textContent).toMatch(/Sljedeći pokušaj je moguć u \d{2}:\d{2}\./)
	})

	// Isti 401 za nepotvrđen nalog (Faza 8, korak 5), pa poruka mora pomenuti i to
	test('401 podsjeti da nov nalog prvo treba potvrditi i nudi novi link', async () => {
		backend({
			...NEPRIJAVLJENA,
			'POST /api/auth/login': () => problem(401, { detail: 'Email ili lozinka nisu tačni. Provjeri ih i pokušaj ponovo.' }),
		})
		otvori('/prijava')
		const korisnik = userEvent.setup()

		await korisnik.type(await screen.findByLabelText('Email'), 'hana@primjer.ba')
		await korisnik.type(screen.getByLabelText('Lozinka'), 'čitaonica1')
		await korisnik.click(screen.getByRole('button', { name: 'Prijavi se' }))

		expect(await screen.findByText(/Nov nalog radi tek kad potvrdiš email/)).toBeTruthy()
		expect(screen.getByRole('link', { name: 'Pošalji novi link za potvrdu' }).getAttribute('href')).toBe('/potvrda-emaila')
	})
})
