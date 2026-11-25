import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, test } from 'vitest'

const SRC = fileURLToPath(new URL('.', import.meta.url))
const DIZAJN = fileURLToPath(new URL('../../docs/DIZAJN.md', import.meta.url))

// Redovi tabela u DIZAJN.md: | `--ime` | `vrijednost` | ...
function izDizajna(uslov) {
	const redovi = readFileSync(DIZAJN, 'utf-8').matchAll(/^\| `(--[a-z0-9-]+)` \| `([^`]+)` \|/gm)
	return izdvoji(redovi, uslov)
}

// Redovi u tokeni.css: --ime: vrijednost;
function izTokena(uslov) {
	const redovi = readFileSync(join(SRC, 'tokeni.css'), 'utf-8').matchAll(/(--[a-z0-9-]+):\s*([^;]+);/g)
	return izdvoji(redovi, uslov)
}

function izdvoji(redovi, uslov) {
	const parovi = [...redovi].map(([, ime, vrijednost]) => [ime, vrijednost.trim().toLowerCase()])
	return Object.fromEntries(parovi.filter(([ime, vrijednost]) => uslov(ime, vrijednost)))
}

function fajloviSaDirektnom(uzorak) {
	const fajlovi = readdirSync(SRC, { recursive: true }).filter(
		(put) => /\.(css|jsx?)$/.test(put) && !put.endsWith('.test.js') && put !== 'tokeni.css',
	)
	expect(fajlovi.length).toBeGreaterThan(0)
	return fajlovi.filter((put) => uzorak.test(readFileSync(join(SRC, put), 'utf-8')))
}

const jeBoja = (_ime, vrijednost) => vrijednost.startsWith('#')
const jeVelicina = (ime) => ime.startsWith('--velicina-')

describe('vrijednosti iz docs/DIZAJN.md', () => {
	test('tokeni imaju tačno boje iz tabele u DIZAJN.md, ni jednu više ni manje', () => {
		const boje = izDizajna(jeBoja)

		expect(Object.keys(boje)).toHaveLength(7)
		expect(izTokena(jeBoja)).toEqual(boje)
	})

	test('tokeni imaju tačno veličine teksta iz tabele u DIZAJN.md', () => {
		const velicine = izDizajna(jeVelicina)

		expect(Object.keys(velicine)).toHaveLength(6)
		expect(izTokena(jeVelicina)).toEqual(velicine)
	})

	test('nijedan fajl osim tokeni.css ne piše boju direktno', () => {
		expect(fajloviSaDirektnom(/#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(/)).toEqual([])
	})

	test('nijedan fajl osim tokeni.css ne piše veličinu teksta brojem', () => {
		expect(fajloviSaDirektnom(/font-size:\s*\d|fontSize:\s*\d/)).toEqual([])
	})
})
