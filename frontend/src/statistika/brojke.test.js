import { expect, test } from 'vitest'
import { brojRezervacija, procenat, sati } from './brojke.js'

test('broj rezervacija se slaže s brojem, i kod 11 do 14', () => {
	expect([1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 112].map(brojRezervacija)).toEqual([
		'1 rezervacija',
		'2 rezervacije',
		'4 rezervacije',
		'5 rezervacija',
		'11 rezervacija',
		'12 rezervacija',
		'14 rezervacija',
		'21 rezervacija',
		'22 rezervacije',
		'25 rezervacija',
		'112 rezervacija',
	])
})

test('sati s decimalnim zarezom, a procenat bez ponuđenih minuta ne postoji', () => {
	expect([sati(240), sati(90), sati(50)]).toEqual(['4 h', '1,5 h', '0,8 h'])
	expect([procenat(240, 840), procenat(600, 480), procenat(0, 0)]).toEqual([29, 125, null])
})
