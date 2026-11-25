import { afterEach, describe, expect, test, vi } from 'vitest'
import { demoRacuni } from './demo.js'

function odgovori(status, tijelo) {
	const zaglavlja = { 'Content-Type': status === 200 ? 'application/json' : 'application/problem+json' }
	vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(tijelo), { status, headers: zaglavlja })))
}

afterEach(() => {
	vi.unstubAllGlobals()
})

describe('demoRacuni', () => {
	test('vraća lozinku i račune kako ih šalje DemoResponse', async () => {
		odgovori(200, { lozinka: 'sebilj', racuni: [{ email: 'klijent@slotbook.test', ime: 'Hana Šehić', opis: 'rezerviše' }] })

		expect(await demoRacuni()).toEqual({
			lozinka: 'sebilj',
			racuni: [{ email: 'klijent@slotbook.test', ime: 'Hana Šehić', opis: 'rezerviše' }],
		})
	})

	test('404 znači da ovo nije demo, pa nema računa', async () => {
		odgovori(404, { status: 404, title: 'Nije pronađeno' })

		expect(await demoRacuni()).toBeNull()
	})

	test('500 nije "nije demo", nego greška', async () => {
		odgovori(500, { status: 500, title: 'Greška na serveru' })

		await expect(demoRacuni()).rejects.toThrow()
	})
})
