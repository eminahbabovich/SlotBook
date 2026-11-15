import { danUZoni, nazivDana, satUZoni } from './vrijeme.js'

// "ponedjeljak, 19. novembar, 08:00 do 09:00": cijela rečenica za čitač ekrana i poruke.
// bezDana kad je dan već jasan iz okoline ("Najbliži slobodan: 09:00 do 10:00").
export function opisTermina(termin, zona, { bezDana = false } = {}) {
	const vrijeme = `${satUZoni(termin.pocetak, zona)} do ${satUZoni(termin.kraj, zona)}`
	return bezDana ? vrijeme : `${nazivDana(danUZoni(termin.pocetak, zona))}, ${vrijeme}`
}

export function opisTrenutka(trenutak, zona) {
	return `${nazivDana(danUZoni(trenutak, zona))}, ${satUZoni(trenutak, zona)}`
}
