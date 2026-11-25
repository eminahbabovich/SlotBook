import { expect, test } from 'vitest'
import { brojKarte } from './brojKarte.js'

test('broj karte je prvih osam znakova ID-ja rezervacije, velikim slovima i u dva dijela', () => {
	expect(brojKarte('c16c3710-5b2e-4a8f-9d61-0e7f3a2b9c44')).toBe('C16C-3710')
})

test('kratak ID iz testova ostaje cijeli', () => {
	expect(brojKarte('b1')).toBe('B1')
})
