import { describe, expect, it } from 'vitest'
import { opisiPravila, uPravilima } from './pravila.js'

const ZONA = 'Europe/Sarajevo'
const termin = (pocetak, stanje = 'slobodno') => ({ pocetak, kraj: pocetak, stanje })

describe('uPravilima', () => {
	// 19.3. u 23:30 UTC je po Sarajevu već četvrtak 20.3. u 00:30, pa dan mora biti po zoni organizacije
	it('broji dane unaprijed po kalendaru u zoni organizacije, ne u UTC-u', () => {
		const kalendar = {
			zona: ZONA,
			termini: [termin('2036-03-19T07:00:00Z'), termin('2036-03-19T23:30:00Z'), termin('2036-03-20T07:00:00Z')],
		}
		// sada: ponedjeljak 17.3. u 23:30 po Sarajevu; dva dana unaprijed je srijeda 19.3.
		const izlaz = uPravilima(kalendar, { danaUnaprijed: 2 }, '2036-03-17T22:30:00Z')
		expect(izlaz.termini.map((t) => t.pocetak)).toEqual(['2036-03-19T07:00:00Z'])
	})

	it('ne nudi slobodan termin bliži od minutaPrije, a svoj i zauzet ostaju', () => {
		const kalendar = {
			zona: ZONA,
			termini: [termin('2036-03-17T09:00:00Z'), termin('2036-03-17T09:00:00Z', 'moje'), termin('2036-03-17T09:30:00Z', 'zauzeto'), termin('2036-03-17T11:00:00Z')],
		}
		const izlaz = uPravilima(kalendar, { minutaPrije: 120 }, '2036-03-17T08:00:00Z')
		expect(izlaz.termini.map((t) => t.stanje)).toEqual(['moje', 'zauzeto', 'slobodno'])
	})

	it('bez pravila vraća isti kalendar', () => {
		const kalendar = { zona: ZONA, termini: [termin('2036-03-17T09:00:00Z')] }
		expect(uPravilima(kalendar, null, '2036-03-17T08:00:00Z')).toBe(kalendar)
		expect(uPravilima(kalendar, { najviseAktivnih: 2, danaUnaprijed: null, minutaPrije: null }, '2036-03-17T08:00:00Z')).toBe(kalendar)
	})
})

describe('opisiPravila', () => {
	it('opisuje samo pravila koja postoje', () => {
		expect(opisiPravila({ najviseAktivnih: 2, danaUnaprijed: 14, minutaPrije: 120 })).toEqual([
			'najviše 2 unaprijed po osobi',
			'do 14 dana unaprijed',
			'najkasnije 2 h prije',
		])
		expect(opisiPravila({ najviseAktivnih: null, danaUnaprijed: 1, minutaPrije: 45 })).toEqual(['do 1 dan unaprijed', 'najkasnije 45 min prije'])
	})
})
