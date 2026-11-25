import { expect, test } from 'vitest'
import { emajlResursa } from './emajl.js'

test('isti resurs uvijek dobije istu boju, a različiti resursi ne dobiju svi istu', () => {
	const id = '3f2a9c1e-7b4d-4e8a-9f10-2c5d6e7f8a9b'
	expect(emajlResursa(id)).toBe(emajlResursa(id))
	expect(emajlResursa(id)).toMatch(/^var\(--emajl-(tirkiz|lapis|safran|sipak)\)$/)

	const boje = new Set(['sto-1', 'sto-2', 'sto-3', 'sala-a', 'sala-b', 'konsultacije', 'teretana', 'bazen'].map(emajlResursa))
	expect(boje.size).toBeGreaterThan(2)
})
