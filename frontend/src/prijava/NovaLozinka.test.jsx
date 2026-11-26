// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { BEZ_SESIJE, backend, otvori, problem, tijelo } from '../test/lazniBackend.js'

const NEPRIJAVLJENA = { '/api/auth/refresh': BEZ_SESIJE }

beforeEach(() => postaviToken(null))
afterEach(cleanup)

test('slaba lozinka se ne šalje, a jaka ide s tokenom iz linka i kaže da su druge prijave odjavljene', async () => {
	const fetch = backend({ ...NEPRIJAVLJENA, 'POST /api/auth/password-reset/confirm': () => new Response(null, { status: 204 }) })
	otvori('/nova-lozinka#token=abc123')
	const korisnik = userEvent.setup()
	const polje = await screen.findByLabelText('Nova lozinka')

	await korisnik.type(polje, '12345678')
	await korisnik.click(screen.getByRole('button', { name: 'Postavi lozinku' }))
	expect(screen.getByText('Lozinka treba imati najmanje 10 znakova.')).toBeTruthy()
	expect(fetch.mock.calls.some(([putanja]) => putanja === '/api/auth/password-reset/confirm')).toBe(false)

	await korisnik.clear(polje)
	await korisnik.type(polje, 'kozija-cuprija-1580')
	await korisnik.click(screen.getByRole('button', { name: 'Postavi lozinku' }))

	expect(await screen.findByText(/Lozinka je promijenjena/)).toBeTruthy()
	expect(tijelo(fetch, 'POST /api/auth/password-reset/confirm')).toEqual({ token: 'abc123', password: 'kozija-cuprija-1580' })
})

test('poruka s backenda za čestu lozinku stoji ispod polja', async () => {
	backend({
		...NEPRIJAVLJENA,
		'POST /api/auth/password-reset/confirm': () =>
			problem(400, { title: 'Neispravni podaci', greske: [{ polje: 'password', poruka: 'Ovu lozinku ljudi često koriste, pa se lako pogodi.' }] }),
	})
	otvori('/nova-lozinka#token=abc123')
	const korisnik = userEvent.setup()
	await korisnik.type(await screen.findByLabelText('Nova lozinka'), 'sarajevo2024')
	await korisnik.click(screen.getByRole('button', { name: 'Postavi lozinku' }))

	expect(await screen.findByText(/ljudi često koriste/)).toBeTruthy()
})

test('zaboravljena lozinka ne tvrdi da nalog postoji', async () => {
	const fetch = backend({ ...NEPRIJAVLJENA, 'POST /api/auth/password-reset': () => new Response(null, { status: 204 }) })
	otvori('/zaboravljena-lozinka')
	const korisnik = userEvent.setup()
	await korisnik.type(await screen.findByLabelText('Email'), 'hana@primjer.ba')
	await korisnik.click(screen.getByRole('button', { name: 'Pošalji link' }))

	expect(await screen.findByText(/Ako hana@primjer\.ba ima nalog/)).toBeTruthy()
	expect(tijelo(fetch, 'POST /api/auth/password-reset')).toEqual({ email: 'hana@primjer.ba' })
})
