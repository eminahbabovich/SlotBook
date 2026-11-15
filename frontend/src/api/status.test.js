import { afterEach, describe, expect, test, vi } from 'vitest'
import { getStatus } from './status.js'

function odgovori(tijelo) {
	vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(tijelo), { status: 200, headers: { 'Content-Type': 'application/json' } })))
}

afterEach(() => {
	vi.unstubAllGlobals()
})

describe('getStatus', () => {
	test('vraća aplikaciju i vrijeme servera kako ih šalje StatusResponse', async () => {
		odgovori({ aplikacija: 'slotbook', vrijeme: '2035-11-19T07:00:00Z' })

		expect(await getStatus()).toEqual({ aplikacija: 'slotbook', vrijeme: '2035-11-19T07:00:00Z' })
	})

	test('odgovor bez polja vrijeme pada glasno, s putanjom i imenom polja', async () => {
		odgovori({ aplikacija: 'slotbook', time: '2035-11-19T07:00:00Z' })

		await expect(getStatus()).rejects.toThrow('/api/status: nema polja vrijeme')
	})
})
