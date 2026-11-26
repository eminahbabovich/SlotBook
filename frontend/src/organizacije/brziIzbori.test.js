import { expect, test } from 'vitest'
import { brziIzbor, poSatu } from './brziIzbori.js'

const ZONA = 'Europe/Sarajevo'

// 23:30 UTC je 00:30 po Sarajevu: "danas" je već sljedeći dan, a "Sad" kreće od ponoći
test('sad i sutra se računaju u zoni organizacije, ne u UTC-u', () => {
	const sada = new Date('2035-11-19T23:30:00Z')
	expect(brziIzbor('sad', sada, ZONA)).toEqual({ id: 'sad', dan: '2035-11-20', odSata: 0, doSata: 3 })
	expect(brziIzbor('sutra-ujutro', sada, ZONA)).toEqual({ id: 'sutra-ujutro', dan: '2035-11-21', odSata: 6, doSata: 12 })
})

test('sad kasno uveče ne prelazi ponoć', () => {
	expect(brziIzbor('sad', new Date('2035-11-19T21:10:00Z'), ZONA)).toMatchObject({ odSata: 22, doSata: 24 })
})

test('termini istog početka su u jednom redu, redoslijed ostaje', () => {
	const t = (pocetak, resurs) => ({ pocetak, resurs })
	const redovi = poSatu([t('A', 'Sto 7'), t('B', 'Sto 4'), t('B', 'Sto 7')])
	expect(redovi.map((r) => [r.pocetak, r.termini.map((x) => x.resurs)])).toEqual([
		['A', ['Sto 7']],
		['B', ['Sto 4', 'Sto 7']],
	])
})
