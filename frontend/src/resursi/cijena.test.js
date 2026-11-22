import { expect, test } from 'vitest'
import { feningeUKm, kmUFeninge } from './cijena.js'

test('cijena se upisuje u KM sa zarezom ili tačkom, a šalje u feninzima', () => {
	expect(kmUFeninge('15')).toBe(1500)
	expect(kmUFeninge('7,50')).toBe(750)
	expect(kmUFeninge('7.5')).toBe(750)
	// 0,29 * 100 je u JavaScriptu 28.999999999999996
	expect(kmUFeninge('0,29')).toBe(29)
	expect(kmUFeninge(' 3 ')).toBe(300)
})

test('prazna cijena je besplatan termin', () => {
	expect(kmUFeninge('')).toBe(0)
})

test('ono što nije iznos u KM nije cijena, da ne postane besplatno', () => {
	for (const unos of ['abc', '-5', '7,555', '1.000,00', '15 KM']) {
		expect(kmUFeninge(unos)).toBeNull()
	}
})

test('feninzi se u formi prikazuju kao KM, bez nula kad ih ne treba', () => {
	expect(feningeUKm(0)).toBe('0')
	expect(feningeUKm(1500)).toBe('15')
	expect(feningeUKm(750)).toBe('7,50')
	expect(feningeUKm(1505)).toBe('15,05')
})
