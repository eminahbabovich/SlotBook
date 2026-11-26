// Za jedan resurs na ekranu: termin koji je sada u toku, prvi sljedeći i ostatak dana poslije njega.
// Termini stižu poredani po početku. sada je broj milisekundi (Date.now s pomakom servera).
export function rasporedResursa(termini, sada) {
	const trenutni = termini.find((t) => Date.parse(t.pocetak) <= sada && sada < Date.parse(t.kraj)) ?? null
	const buduci = termini.filter((t) => Date.parse(t.pocetak) > sada)
	return { trenutni, sljedeci: buduci[0] ?? null, ostali: buduci.slice(1) }
}

// Isti tekst za ekran i čitač ekrana. Grupni termin kaže koliko je mjesta ostalo.
export function stanjeTermina(termin, brojMjesta) {
	if (termin.slobodnih === 0) return brojMjesta > 1 ? 'popunjeno' : 'zauzeto'
	if (brojMjesta > 1) return `slobodno, ${termin.slobodnih} od ${brojMjesta} mjesta`
	return 'slobodno'
}
