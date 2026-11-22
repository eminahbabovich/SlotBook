// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { PRIJAVLJENA, backend, json, otvori } from '../test/lazniBackend.js'

const PRAZNO = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const upis = (id, start, status, rok) => ({
	id, resourceId: 'r1', resourceName: 'Sala za grupni trening', organizationName: 'Teretana Ilidža', organizationSlug: 'teretana-ilidza',
	timezone: 'Europe/Sarajevo', startTime: start, endTime: start.replace('T17', 'T18'), status, offeredUntil: rok,
})

// Sada je ponedjeljak 19.11.2035. u 17:00 po Sarajevu. Prvi termin je ponuđen do 17:30, drugi se čeka.
function lista(upisi) {
	return backend({
		...PRIJAVLJENA,
		'/api/bookings/mine?page=0&size=20': () => json(PRAZNO),
		'/api/waitlist/mine?page=0&size=20': () => json({ ...PRAZNO, content: upisi, totalElements: upisi.length, totalPages: 1 }),
		'DELETE /api/waitlist/w2': () => {
			upisi.splice(1, 1)
			return new Response(null, { status: 204 })
		},
	})
}

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-11-19T16:00:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

test('ponuđen termin s liste ima rok i link na kalendar, a onaj koji se čeka samo stanje', async () => {
	lista([upis('w1', '2035-11-20T17:00:00Z', 'OFFERED', '2035-11-19T16:30:00Z'), upis('w2', '2035-11-21T17:00:00Z', 'WAITING', null)])
	otvori('/rezervacije')

	const redovi = within(await screen.findByRole('list', { name: 'Lista čekanja' })).getAllByRole('listitem')

	expect(redovi[0].textContent).toContain('termin se oslobodio. Rok za rezervaciju: ponedjeljak, 19. novembar, 17:30.')
	expect(within(redovi[0]).getByRole('link', { name: 'Rezerviši u kalendaru' }).getAttribute('href')).toBe('/o/teretana-ilidza/r/r1')
	expect(redovi[1].textContent).toContain('čekaš da se termin oslobodi')
	expect(within(redovi[1]).queryByRole('link')).toBeNull()
})

test('ispis sa liste traži drugi klik, jer se mjesto na listi gubi', async () => {
	const fetch = lista([upis('w1', '2035-11-20T17:00:00Z', 'OFFERED', '2035-11-19T16:30:00Z'), upis('w2', '2035-11-21T17:00:00Z', 'WAITING', null)])
	otvori('/rezervacije')
	const korisnik = userEvent.setup()
	const drugi = () => within(screen.getByRole('list', { name: 'Lista čekanja' })).getAllByRole('listitem')[1]

	await screen.findByRole('list', { name: 'Lista čekanja' })
	await korisnik.click(within(drugi()).getByRole('button', { name: 'Ispiši me sa liste' }))
	expect(fetch.mock.calls.some(([, o]) => o.method === 'DELETE')).toBe(false)
	await korisnik.click(within(drugi()).getByRole('button', { name: 'Ispiši me' }))

	expect(await screen.findByText('Više nisi na listi čekanja: Sala za grupni trening, srijeda, 21. novembar, 18:00 do 19:00.')).toBeTruthy()
	expect(fetch.mock.calls.filter(([p, o]) => p === '/api/waitlist/w2' && o.method === 'DELETE')).toHaveLength(1)
	await vi.waitFor(() => expect(within(screen.getByRole('list', { name: 'Lista čekanja' })).getAllByRole('listitem')).toHaveLength(1))
})

test('prazna lista kaže gdje se na nju upisuje', async () => {
	lista([])
	otvori('/rezervacije')

	expect(await screen.findByText(/Nisi ni na jednoj listi čekanja\. Kad je termin u kalendaru zauzet/)).toBeTruthy()
})
