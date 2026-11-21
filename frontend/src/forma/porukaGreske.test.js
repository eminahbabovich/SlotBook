import { afterEach, describe, expect, test, vi } from 'vitest'
import { ApiError } from '../api/apiFetch.js'
import { porukaGreske } from './porukaGreske.js'

const PORUKE = { serverNeOdgovara: 'Server ne odgovara.', opsta: 'Greška na serveru.' }
const ZAKLJUCANO = {
	status: 429,
	detail: 'Za ovaj email je bilo previše pogrešnih lozinki, pa je prijava zaključana na 15 minuta. Pokušaj ponovo kasnije.',
}

afterEach(() => vi.useRealTimers())

describe('porukaGreske', () => {
	// "Pokušaj ponovo kasnije" bez toga kad je u docs/DIZAJN.md zabranjeno
	test('429 s Retry-After kaže i u koliko sati je sljedeći pokušaj', () => {
		vi.useFakeTimers({ toFake: ['Date'] })
		vi.setSystemTime(new Date('2035-11-19T13:20:00Z'))

		const poruka = porukaGreske(new ApiError(429, ZAKLJUCANO, 720), PORUKE, 'Europe/Sarajevo')

		expect(poruka).toBe(`${ZAKLJUCANO.detail} Sljedeći pokušaj je moguć u 14:32.`)
	})

	test('429 bez Retry-After ostaje samo poruka backenda', () => {
		expect(porukaGreske(new ApiError(429, ZAKLJUCANO), PORUKE, 'Europe/Sarajevo')).toBe(ZAKLJUCANO.detail)
	})

	test('druge greške ne dobijaju vrijeme ni kad imaju Retry-After', () => {
		const greska = new ApiError(503, { detail: 'Održavanje.' }, 60)

		expect(porukaGreske(greska, PORUKE, 'Europe/Sarajevo')).toBe('Server ne odgovara.')
	})
})
