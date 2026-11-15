import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, test } from 'vitest'

const SRC = fileURLToPath(new URL('.', import.meta.url))
const DIZAJN = fileURLToPath(new URL('../../docs/DIZAJN.md', import.meta.url))

function bojeIzTabeleDizajna() {
	const redovi = readFileSync(DIZAJN, 'utf-8').matchAll(/^\| `(--[a-z-]+)` \| `(#[0-9a-fA-F]{6})` \|/gm)
	return Object.fromEntries([...redovi].map(([, ime, hex]) => [ime, hex.toLowerCase()]))
}

function bojeIzTokena() {
	const redovi = readFileSync(join(SRC, 'tokeni.css'), 'utf-8').matchAll(/(--[a-z-]+):\s*(#[0-9a-fA-F]{6});/g)
	return Object.fromEntries([...redovi].map(([, ime, hex]) => [ime, hex.toLowerCase()]))
}

describe('boje iz docs/DIZAJN.md', () => {
	test('tokeni imaju tačno boje iz tabele u DIZAJN.md, ni jednu više ni manje', () => {
		const izDizajna = bojeIzTabeleDizajna()

		expect(Object.keys(izDizajna)).toHaveLength(5)
		expect(bojeIzTokena()).toEqual(izDizajna)
	})

	test('nijedan fajl osim tokeni.css ne piše boju direktno', () => {
		const fajlovi = readdirSync(SRC, { recursive: true }).filter(
			(put) => /\.(css|jsx?)$/.test(put) && !put.endsWith('.test.js') && put !== 'tokeni.css',
		)
		const sBojom = fajlovi.filter((put) => /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/.test(readFileSync(join(SRC, put), 'utf-8')))

		expect(fajlovi.length).toBeGreaterThan(0)
		expect(sBojom).toEqual([])
	})
})
