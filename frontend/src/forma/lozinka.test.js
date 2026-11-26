import { describe, expect, test } from 'vitest'
import { problemLozinke } from './lozinka.js'

describe('lozinka prije slanja', () => {
	// Svaka od ovih bi prošla staro pravilo od osam znakova
	test.each(['12345678', '1234567890', 'QWERTZUIOP', 'aaaaaaaaaaaa', '0987654321'])('%s ne prolazi', (lozinka) => {
		expect(problemLozinke(lozinka)).not.toBeNull()
	})

	test('lozinka s imenom iz emaila ne prolazi, ista bez emaila prolazi', () => {
		expect(problemLozinke('amra.hodzic2001', 'amra.hodzic@example.com')).toBe('Lozinka ne smije sadržati tvoj email.')
		expect(problemLozinke('kozija-cuprija-1580', 'amra.hodzic@example.com')).toBeNull()
	})

	test('dužina se broji po slovima, pa č ne broji duplo', () => {
		expect(problemLozinke('čćšžđčćšžđ')).toBeNull()
		expect(problemLozinke('čćšžđčćšž')).toMatch(/najmanje 10/)
	})
})
