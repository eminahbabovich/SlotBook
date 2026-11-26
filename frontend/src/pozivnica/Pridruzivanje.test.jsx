// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { BEZ_SESIJE, PRIJAVLJENA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const NEPRIJAVLJENA = { '/api/auth/refresh': BEZ_SESIJE }
const PREGLED = '/api/invite-links/abc'
const upotrebljiv = () => json({ organizationName: 'Čitaonica PMF', slug: 'citaonica-pmf', usable: true })

beforeEach(() => postaviToken(null))
afterEach(cleanup)

// Ko nema nalog, vidi gdje ulazi, a prijava ga vraća baš na ovaj link
test('bez prijave se vidi ime organizacije, a prijava pamti link za povratak', async () => {
	backend({ ...NEPRIJAVLJENA, [PREGLED]: upotrebljiv })
	otvori('/pridruzi/abc')

	expect(await screen.findByRole('heading', { name: 'Čitaonica PMF' })).toBeTruthy()
	await userEvent.setup().click(screen.getByRole('link', { name: 'Prijavi se' }))

	expect(window.location.pathname).toBe('/prijava')
	expect(window.history.state.usr).toEqual({ odakle: '/pridruzi/abc' })
})

test('prijavljena osoba ulazi na klik i završi na stranici organizacije', async () => {
	const fetch = backend({
		...PRIJAVLJENA,
		[PREGLED]: upotrebljiv,
		'POST /api/invite-links/join': () => json({ organizationId: 'o1', slug: 'citaonica-pmf', name: 'Čitaonica PMF', alreadyMember: false }),
	})
	otvori('/pridruzi/abc')

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Uđi u organizaciju' }))

	expect(await screen.findByRole('heading', { name: 'Čitaonica PMF', level: 1 })).toBeTruthy()
	expect(window.location.pathname).toBe('/o/citaonica-pmf')
	expect(tijelo(fetch, 'POST /api/invite-links/join')).toEqual({ token: 'abc' })
})

test('već član dobije poruku, a ne grešku', async () => {
	backend({
		...PRIJAVLJENA,
		[PREGLED]: upotrebljiv,
		'POST /api/invite-links/join': () => json({ organizationId: 'o1', slug: 'citaonica-pmf', name: 'Čitaonica PMF', alreadyMember: true }),
	})
	otvori('/pridruzi/abc')

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Uđi u organizaciju' }))

	expect((await screen.findByRole('status')).textContent).toBe('Već si član ove organizacije, ništa se nije promijenilo.')
	expect(screen.getByRole('link', { name: 'Otvori Čitaonica PMF' })).toBeTruthy()
})

test('popunjen link: poruka servera i bez dugmeta za ponovni pokušaj', async () => {
	backend({
		...PRIJAVLJENA,
		[PREGLED]: upotrebljiv,
		'POST /api/invite-links/join': () =>
			problem(409, { title: 'Pozivni link ne važi', detail: 'Preko ovog linka je ušao najveći broj ljudi koji je admin dozvolio.', razlog: 'FULL' }),
	})
	otvori('/pridruzi/abc')

	await userEvent.setup().click(await screen.findByRole('button', { name: 'Uđi u organizaciju' }))

	expect((await screen.findByRole('alert')).textContent).toMatch(/najveći broj ljudi/)
	expect(screen.queryByRole('button', { name: 'Uđi u organizaciju' })).toBeNull()
})

test('link koji ne postoji i link koji više ne radi imaju svoje objašnjenje', async () => {
	backend({ ...NEPRIJAVLJENA, [PREGLED]: () => problem(404, { title: 'Pozivni link ne važi' }) })
	otvori('/pridruzi/abc')
	expect(await screen.findByRole('heading', { name: 'Link ne postoji' })).toBeTruthy()
	cleanup()

	backend({ ...NEPRIJAVLJENA, [PREGLED]: () => json({ organizationName: 'Čitaonica PMF', slug: 'citaonica-pmf', usable: false }) })
	otvori('/pridruzi/abc')
	expect(await screen.findByText(/više ne radi/)).toBeTruthy()
	expect(screen.queryByRole('link', { name: 'Prijavi se' })).toBeNull()
})
