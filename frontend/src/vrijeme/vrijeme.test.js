import { afterEach, describe, expect, test, vi } from 'vitest'
import { danUZoni, dodajDane, nazivDana, satUZoni, sedmicaOd, zonaUredjajaRazlicita } from './vrijeme.js'

const SARAJEVO = 'Europe/Sarajevo'

// Ljetno vrijeme 2035. počinje u nedjelju 25. marta: u 02:00 sat skoči na 03:00.
describe('prelazak na ljetno vrijeme', () => {
	test('termin od 08:00 je 08:00 i prije i poslije prelaska, iako mu se UTC pomjeri za sat', () => {
		expect(satUZoni('2035-03-24T07:00:00Z', SARAJEVO)).toBe('08:00')
		expect(satUZoni('2035-03-26T06:00:00Z', SARAJEVO)).toBe('08:00')
	})

	test('na dan prelaska 01:00 UTC je 03:00, jer 02:00 tog dana ne postoji', () => {
		expect(satUZoni('2035-03-25T00:59:00Z', SARAJEVO)).toBe('01:59')
		expect(satUZoni('2035-03-25T01:00:00Z', SARAJEVO)).toBe('03:00')
	})

	test('sedmica preko prelaska ima sedam različitih dana, bez preskočenog ili dupliranog', () => {
		expect(sedmicaOd('2035-03-22')).toEqual([
			'2035-03-22', '2035-03-23', '2035-03-24', '2035-03-25', '2035-03-26', '2035-03-27', '2035-03-28',
		])
		expect(dodajDane('2035-10-27', 2)).toBe('2035-10-29')
	})
})

describe('dan u zoni organizacije, ne u zoni browsera', () => {
	test('termin u 00:30 po Sarajevu pripada tom danu, iako je u UTC-u još prethodni', () => {
		expect(danUZoni('2035-11-18T23:30:00Z', SARAJEVO)).toBe('2035-11-19')
		expect(danUZoni('2035-03-24T23:30:00Z', SARAJEVO)).toBe('2035-03-25')
	})

	test('danas je dan u zoni organizacije: za Sarajevo je već utorak, za New York još ponedjeljak', () => {
		const sada = new Date('2035-11-19T23:30:00Z')

		expect(danUZoni(sada, SARAJEVO)).toBe('2035-11-20')
		expect(danUZoni(sada, 'America/New_York')).toBe('2035-11-19')
	})
})

test('naziv dana je na bosanskom i ne pomjera se za dan u zoni zapadno od UTC-a', () => {
	expect(nazivDana('2035-11-19')).toBe('ponedjeljak, 19. novembar')
	expect(nazivDana('2035-11-19', 'kratko')).toBe('pon 19.')
})

test('oznaka zone treba samo kad uređaj pokazuje drugi sat, ne kad zona ima drugo ime', () => {
	const termini = ['2035-11-19T07:00:00Z']

	expect(zonaUredjajaRazlicita(termini, SARAJEVO, 'Europe/Zagreb')).toBe(false)
	expect(zonaUredjajaRazlicita(termini, SARAJEVO, 'Europe/London')).toBe(true)
})

describe('browser bez bosanskog u Intl-u', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
		vi.resetModules()
	})

	// Edge 157 za 'bs' vraća "M11 19, Mon" (provjereno 15.11.2026). Node ima puni ICU, pa
	// se to ovdje glumi: Intl za 'bs' formatira kao da jezika nema.
	test('nazivi dana i mjeseci i sati su isti i kad browser ne zna bosanski', async () => {
		const Pravi = Intl.DateTimeFormat
		vi.stubGlobal('Intl', {
			...Intl,
			DateTimeFormat: function (jezik, opcije) {
				return new Pravi(jezik === 'bs' ? 'en-US-u-ca-iso8601' : jezik, opcije)
			},
		})
		vi.resetModules()
		const vrijeme = await import('./vrijeme.js')

		expect(vrijeme.nazivDana('2035-11-19')).toBe('ponedjeljak, 19. novembar')
		expect(vrijeme.nazivDana('2035-08-25', 'kratko')).toBe('sub 25.')
		expect(vrijeme.nazivDana('2035-03-25')).toBe('nedjelja, 25. mart')
		expect(vrijeme.satUZoni('2035-11-19T13:05:00Z', SARAJEVO)).toBe('14:05')
	})
})
