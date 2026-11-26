import { describe, expect, test } from 'vitest'
import { emajlPoRedu, emajlResursa, inicijali } from './emajl.js'

test('isti resurs uvijek dobije istu boju, a različiti resursi ne dobiju svi istu', () => {
	const id = '3f2a9c1e-7b4d-4e8a-9f10-2c5d6e7f8a9b'
	expect(emajlResursa(id)).toBe(emajlResursa(id))
	expect(emajlResursa(id)).toMatch(/^var\(--emajl-(tirkiz|lapis|safran|sipak)\)$/)

	const boje = new Set(['sto-1', 'sto-2', 'sto-3', 'sala-a', 'sala-b', 'konsultacije', 'teretana', 'bazen'].map(emajlResursa))
	expect(boje.size).toBeGreaterThan(2)
})

describe('inicijali i boja po redu', () => {
	test('dvije riječi daju dva slova, a riječ koja počinje cifrom se preskače', () => {
		expect(inicijali('Čitaonica PMF')).toBe('ČP')
		expect(inicijali('Sto 4, tihi dio')).toBe('ST')
		expect(inicijali('sala')).toBe('S')
	})

	test('susjedne organizacije na listi nemaju istu boju', () => {
		expect(emajlPoRedu(0)).not.toBe(emajlPoRedu(1))
		expect(emajlPoRedu(4)).toBe(emajlPoRedu(0))
	})
})
