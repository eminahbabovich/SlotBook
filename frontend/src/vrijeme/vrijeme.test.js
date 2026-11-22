import { afterEach, describe, expect, test, vi } from 'vitest'
import { rokPonude } from './opis.js'
import { danUSedmici, danUZoni, dodajDane, dodajMjesece, mjesecUZoni, nazivDana, nazivMjeseca, satUZoni, sedmicaOd, uTrenutakSPomakom, zonaUredjajaRazlicita } from './vrijeme.js'

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

test('rok ponude kaže danas ili sutra po zoni organizacije, ne po zoni uređaja', () => {
	// 23:50 po Sarajevu je 22:50Z. Rok 30 minuta kasnije je već sutra u Sarajevu, a isti dan u UTC-u.
	const sada = Date.parse('2035-11-19T22:50:00Z')
	expect(rokPonude('2035-11-19T23:00:00Z', 'Europe/Sarajevo', sada)).toBe('sutra do 00:00')
	expect(rokPonude('2035-11-19T22:55:00Z', 'Europe/Sarajevo', sada)).toBe('danas do 23:55')
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

// Izuzetak (godišnji, praznik) se upisuje kao dan i sat u zoni organizacije, a backend traži
// OffsetDateTime. Pomak zavisi od datuma: zimi +01:00, ljeti +02:00.
describe('dan i sat u zoni u trenutak s pomakom', () => {
	test('zimi je pomak +01:00, ljeti +02:00, za isti sat', () => {
		expect(uTrenutakSPomakom('2026-12-24', '00:00', SARAJEVO)).toBe('2026-12-24T00:00:00+01:00')
		expect(uTrenutakSPomakom('2027-07-01', '08:30', SARAJEVO)).toBe('2027-07-01T08:30:00+02:00')
	})

	test('pomak se uzima iz zone organizacije, ne iz zone uređaja', () => {
		expect(uTrenutakSPomakom('2026-12-24', '00:00', 'America/New_York')).toBe('2026-12-24T00:00:00-05:00')
	})

	test('dan prelaska na ljetno: 01:30 je još zimsko, 03:00 već ljetno, a 02:30 ne postoji', () => {
		expect(uTrenutakSPomakom('2027-03-28', '01:30', SARAJEVO)).toBe('2027-03-28T01:30:00+01:00')
		expect(uTrenutakSPomakom('2027-03-28', '03:00', SARAJEVO)).toBe('2027-03-28T03:00:00+02:00')
		expect(uTrenutakSPomakom('2027-03-28', '02:30', SARAJEVO)).toBeNull()
	})

	// 25.10.2026. u 03:00 sat se vrati na 02:00, pa 02:30 postoji dvaput. Uzima se prvo, da
	// izuzetak radije počne sat ranije nego kasnije.
	test('dan prelaska na zimsko: 02:30 postoji dvaput i uzima se prvo, ljetno', () => {
		expect(uTrenutakSPomakom('2026-10-25', '02:30', SARAJEVO)).toBe('2026-10-25T02:30:00+02:00')
		expect(uTrenutakSPomakom('2026-10-25', '04:00', SARAJEVO)).toBe('2026-10-25T04:00:00+01:00')
	})

	test('ponoć na kraju godine ne preskače u drugu godinu', () => {
		expect(uTrenutakSPomakom('2026-12-31', '23:59', SARAJEVO)).toBe('2026-12-31T23:59:00+01:00')
		expect(uTrenutakSPomakom('2027-01-01', '00:00', SARAJEVO)).toBe('2027-01-01T00:00:00+01:00')
	})
})

describe('mjesec za statistiku', () => {
	// 30.11. u 23:30 UTC je u Sarajevu već 1. decembar
	test('mjesec se čita u zoni organizacije, ne u UTC-u', () => {
		expect(mjesecUZoni('2026-11-30T23:30:00Z', 'Europe/Sarajevo')).toBe('2026-12')
		expect(mjesecUZoni('2026-11-30T22:30:00Z', 'Europe/Sarajevo')).toBe('2026-11')
	})

	test('pomjeranje mjeseca prelazi granicu godine u oba smjera', () => {
		expect(dodajMjesece('2026-11', 1)).toBe('2026-12')
		expect(dodajMjesece('2026-12', 1)).toBe('2027-01')
		expect(dodajMjesece('2027-01', -1)).toBe('2026-12')
	})

	test('naziv mjeseca je malim slovom, s godinom', () => {
		expect(nazivMjeseca('2026-11')).toBe('novembar 2026')
		expect(nazivMjeseca('2027-01')).toBe('januar 2027')
	})

	// DayHourCount.dayOfWeek po ISO-u: 1 je ponedjeljak, 7 nedjelja
	test('dan u sedmici po ISO broju', () => {
		expect(danUSedmici(1)).toBe('ponedjeljak')
		expect(danUSedmici(7)).toBe('nedjelja')
	})
})
