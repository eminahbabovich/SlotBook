// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { postaviToken } from '../api/apiFetch.js'
import { CITAONICA, PRIJAVLJENA, backend, json, otvori, problem, tijelo } from '../test/lazniBackend.js'

const STO = { id: 'r1', organizationId: 'o1', providerId: 'u9', name: 'Sto 4, tihi dio', description: null, slotMinutes: 60, autoConfirm: true, cancelNoticeHours: 0, capacity: 1, priceCents: 0, active: true, hasImage: false }
const RESURSI = {
	'/api/organizations/o1/resources?size=100': () => json({ content: [STO], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
}
// Nedjelja 25.3.2035, dan pomjeranja sata: 08:00 po Sarajevu je 06:00 UTC, a ne 07:00
const PRIJEDLOG = {
	id: 'p1',
	action: 'BOOK',
	resourceId: 'r1',
	resourceName: 'Sto 4, tihi dio',
	startTime: '2035-03-25T06:00:00Z',
	endTime: '2035-03-25T07:00:00Z',
	needsProviderConfirmation: false,
	bookingId: null,
}
const RAZGOVOR = { 'POST /api/organizations/o1/chat/sessions': () => json({ id: 's1', createdAt: '2035-03-24T10:00:00Z' }, 201) }
const odgovor = (text, proposals = []) => ({ 'POST /api/chat/sessions/s1/messages': () => json({ text, proposals }) })

beforeEach(() => {
	postaviToken(null)
	vi.useFakeTimers({ toFake: ['Date'] })
	vi.setSystemTime(new Date('2035-03-24T10:00:00Z'))
})
afterEach(() => {
	cleanup()
	vi.useRealTimers()
	vi.unstubAllGlobals()
})

async function pitaj(tekst) {
	await userEvent.type(await screen.findByLabelText('Poruka asistentu'), tekst)
	await userEvent.click(screen.getByRole('button', { name: 'Pošalji' }))
}

test('prazan razgovor kaže šta se može pitati i ne otvara sesiju dok niko ne pita', async () => {
	const fetch = backend({ ...PRIJAVLJENA, ...RESURSI })
	otvori('/o/citaonica-pmf')

	expect(await screen.findByText('Napiši kad ti treba termin, npr. sutra poslije 14 h ili ponedjeljak ujutro.')).toBeTruthy()
	expect(fetch.mock.calls.some(([p]) => p.includes('/chat/'))).toBe(false)
})

test('prijedlog je karta u zoni organizacije, a rezervacija nastane tek na dugme', async () => {
	const fetch = backend({
		...PRIJAVLJENA,
		...RESURSI,
		...RAZGOVOR,
		...odgovor('Sto 4 je slobodan sutra u 08:00. Potvrdi na karti ispod.', [PRIJEDLOG]),
		'POST /api/chat/proposals/p1/confirm': () => json({ id: 'p1', action: 'BOOK', bookingId: 'b1', bookingStatus: 'CONFIRMED' }),
	})
	otvori('/o/citaonica-pmf')

	await pitaj('Sto 4 sutra u 8')

	const karta = await screen.findByRole('article', { name: 'Prijedlog: Sto 4, tihi dio, nedjelja, 25. mart, 08:00 do 09:00' })
	expect(tijelo(fetch, 'POST /api/chat/sessions/s1/messages')).toEqual({ text: 'Sto 4 sutra u 8' })
	expect(fetch.mock.calls.some(([p]) => p.endsWith('/confirm'))).toBe(false)

	await userEvent.click(within(karta).getByRole('button', { name: 'Rezerviši 08:00' }))

	expect(await within(karta).findByText('Sto 4, tihi dio je tvoj, nedjelja, 25. mart, od 08:00.')).toBeTruthy()
	expect(within(karta).queryByRole('button', { name: 'Rezerviši 08:00' })).toBeNull()
})

// Uređaj na kojem test radi je u Zagrebu, isti sat kao Sarajevo. Zato je ovdje organizacija u New Yorku:
// karta mora pokazati njen sat (02:00 po EDT-u), a ne sat uređaja (08:00).
test('karta pokazuje sat organizacije i kad je uređaj u drugoj zoni', async () => {
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		'/api/me/organizations': () => json([{ ...CITAONICA, timezone: 'America/New_York' }]),
		...RAZGOVOR,
		...odgovor('Evo termina.', [PRIJEDLOG]),
	})
	otvori('/o/citaonica-pmf')
	await pitaj('Sto 4 sutra')

	expect(await screen.findByRole('button', { name: 'Rezerviši 02:00' })).toBeTruthy()
})

test('ponovljena potvrda koja vrati otkazanu rezervaciju ne kaže da je termin tvoj', async () => {
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		...RAZGOVOR,
		...odgovor('Evo termina.', [PRIJEDLOG]),
		'POST /api/chat/proposals/p1/confirm': () => json({ id: 'p1', action: 'BOOK', bookingId: 'b1', bookingStatus: 'CANCELLED' }),
	})
	otvori('/o/citaonica-pmf')
	await pitaj('Sto 4 sutra u 8')

	await userEvent.click(await screen.findByRole('button', { name: 'Rezerviši 08:00' }))

	expect(await screen.findByText('Rezervacija iz ovog prijedloga je u međuvremenu otkazana. Za 08:00 pitaj asistenta ponovo.')).toBeTruthy()
})

test('termin koji je u međuvremenu uzet pokaže poruku backenda na karti', async () => {
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		...RAZGOVOR,
		...odgovor('Evo termina.', [PRIJEDLOG]),
		'POST /api/chat/proposals/p1/confirm': () => problem(409, { title: 'Termin je zauzet', detail: 'Termin je upravo zauzet. Izaberi drugi termin.' }),
	})
	otvori('/o/citaonica-pmf')
	await pitaj('Sto 4 sutra u 8')

	const karta = await screen.findByRole('article', { name: /^Prijedlog:/ })
	await userEvent.click(within(karta).getByRole('button', { name: 'Rezerviši 08:00' }))

	expect((await within(karta).findByRole('alert')).textContent).toBe('Termin je upravo zauzet. Izaberi drugi termin.')
})

test('odbačen prijedlog kaže da ništa nije rezervisano', async () => {
	const fetch = backend({
		...PRIJAVLJENA,
		...RESURSI,
		...RAZGOVOR,
		...odgovor('Evo termina.', [PRIJEDLOG]),
		'POST /api/chat/proposals/p1/dismiss': () => new Response(null, { status: 204 }),
	})
	otvori('/o/citaonica-pmf')
	await pitaj('Sto 4 sutra u 8')

	await userEvent.click(await screen.findByRole('button', { name: 'Ne treba' }))

	expect(await screen.findByText('Prijedlog je odbačen, ništa nije rezervisano.')).toBeTruthy()
	expect(fetch.mock.calls.some(([p]) => p.endsWith('/confirm'))).toBe(false)
})

test('potrošena granica kaže kad asistent opet radi, a pitanje ostaje u polju', async () => {
	backend({
		...PRIJAVLJENA,
		...RESURSI,
		...RAZGOVOR,
		'POST /api/chat/sessions/s1/messages': () =>
			new Response(JSON.stringify({ status: 429, title: 'Asistent je za danas potrošen', detail: 'Tvoja dnevna granica poruka asistentu je potrošena.' }), {
				status: 429,
				headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '3600' },
			}),
	})
	otvori('/o/citaonica-pmf')
	await pitaj('Ima li slobodno?')

	expect((await screen.findByRole('alert')).textContent).toMatch(/^Tvoja dnevna granica poruka asistentu je potrošena\. Sljedeći pokušaj je moguć u \d\d:\d\d\.$/)
	expect(screen.getByLabelText('Poruka asistentu').value).toBe('Ima li slobodno?')
})

test('pun razgovor nudi novi razgovor, a nova poruka otvara novu sesiju', async () => {
	let sesija = 0
	const fetch = backend({
		...PRIJAVLJENA,
		...RESURSI,
		'POST /api/organizations/o1/chat/sessions': () => json({ id: `s${++sesija}`, createdAt: '2035-03-24T10:00:00Z' }, 201),
		'POST /api/chat/sessions/s1/messages': () => problem(409, { title: 'Razgovor je pun', detail: 'Ovaj razgovor je dostigao najveći broj poruka. Otvori novi razgovor i pitaj ponovo.' }),
		...{ 'POST /api/chat/sessions/s2/messages': () => json({ text: 'Sto 4 je slobodan cijeli dan.', proposals: [] }) },
	})
	otvori('/o/citaonica-pmf')
	await pitaj('Još jedno pitanje')

	await userEvent.click(await screen.findByRole('button', { name: 'Počni novi razgovor' }))
	await userEvent.click(screen.getByRole('button', { name: 'Pošalji' }))

	expect(await screen.findByText('Sto 4 je slobodan cijeli dan.')).toBeTruthy()
	expect(fetch.mock.calls.filter(([p]) => p === '/api/organizations/o1/chat/sessions')).toHaveLength(2)
})

test('Enter šalje poruku, a odgovor se najavi kroz log razgovora', async () => {
	backend({ ...PRIJAVLJENA, ...RESURSI, ...RAZGOVOR, ...odgovor('Sto 4 je slobodan cijeli dan.') })
	otvori('/o/citaonica-pmf')

	await userEvent.type(await screen.findByLabelText('Poruka asistentu'), 'Kad je sto 4 slobodan?{Enter}')

	const log = screen.getByRole('log', { name: 'Razgovor s asistentom' })
	expect(await within(log).findByText('Sto 4 je slobodan cijeli dan.')).toBeTruthy()
	expect(within(log).getByText('Kad je sto 4 slobodan?')).toBeTruthy()
})
