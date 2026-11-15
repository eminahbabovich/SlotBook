import { expect, test } from 'vitest'
import { predloziAdresu } from './adresa.js'

test('adresa iz naziva nema kvačica, razmaka ni velikih slova, kao što traži backend', () => {
	expect(predloziAdresu('Čitaonica PMF')).toBe('citaonica-pmf')
	expect(predloziAdresu('Đački dom, sala 2')).toBe('djacki-dom-sala-2')
	expect(predloziAdresu('  Teretana Žuč -- Šip  ')).toBe('teretana-zuc-sip')
})

test('adresa ne prelazi 60 znakova i ne završava crticom', () => {
	const adresa = predloziAdresu(`Konsultacije ${'profesorice '.repeat(10)}`)

	expect(adresa.length).toBeLessThanOrEqual(60)
	expect(adresa).toMatch(/^[a-z0-9]+(-[a-z0-9]+)*$/)
})
