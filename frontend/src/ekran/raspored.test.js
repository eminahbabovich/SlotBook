import { describe, expect, it } from 'vitest'
import { rasporedResursa, stanjeTermina } from './raspored.js'

const t = (od, doKraja, slobodnih = 1) => ({ pocetak: `2036-03-17T${od}:00Z`, kraj: `2036-03-17T${doKraja}:00Z`, slobodnih })
const DAN = [t('07:00', '08:00'), t('08:00', '09:00', 0), t('09:00', '10:00'), t('10:00', '11:00')]
const u = (sat) => Date.parse(`2036-03-17T${sat}:00Z`)

describe('rasporedResursa', () => {
	it('termin u toku je trenutni, a sljedeći je prvi koji još nije počeo', () => {
		const { trenutni, sljedeci, ostali } = rasporedResursa(DAN, u('08:30'))
		expect(trenutni.pocetak).toBe('2036-03-17T08:00:00Z')
		expect(sljedeci.pocetak).toBe('2036-03-17T09:00:00Z')
		expect(ostali.map((x) => x.pocetak)).toEqual(['2036-03-17T10:00:00Z'])
	})

	// Kraj termina ne pripada terminu: u 09:00 je u toku onaj od 09, ne onaj od 08
	it('na granici dva termina trenutni je onaj koji počinje', () => {
		expect(rasporedResursa(DAN, u('09:00')).trenutni.pocetak).toBe('2036-03-17T09:00:00Z')
	})

	it('prije prvog i poslije zadnjeg termina nema trenutnog', () => {
		expect(rasporedResursa(DAN, u('06:00')).trenutni).toBeNull()
		expect(rasporedResursa(DAN, u('06:00')).sljedeci.pocetak).toBe('2036-03-17T07:00:00Z')
		expect(rasporedResursa(DAN, u('12:00'))).toEqual({ trenutni: null, sljedeci: null, ostali: [] })
	})
})

describe('stanjeTermina', () => {
	it('grupni termin kaže koliko je mjesta ostalo, a pun je popunjen', () => {
		expect(stanjeTermina({ slobodnih: 2 }, 6)).toBe('slobodno, 2 od 6 mjesta')
		expect(stanjeTermina({ slobodnih: 0 }, 6)).toBe('popunjeno')
		expect(stanjeTermina({ slobodnih: 0 }, 1)).toBe('zauzeto')
		expect(stanjeTermina({ slobodnih: 1 }, 1)).toBe('slobodno')
	})
})
