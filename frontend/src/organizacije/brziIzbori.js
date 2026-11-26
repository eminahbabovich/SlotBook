import { danUZoni, dodajDane, satUZoni } from '../vrijeme/vrijeme.js'

// Brzi izbori za "Nađi slobodno". Sat je u zoni organizacije, od uključivo do isključivo, po početku termina.
export const IZBORI = [
	{ id: 'sad', naziv: 'Sad' },
	{ id: 'danas-16', naziv: 'Danas poslije 16h' },
	{ id: 'sutra-ujutro', naziv: 'Sutra ujutro' },
	{ id: 'sutra-popodne', naziv: 'Sutra poslije podne' },
]

// "Sad" su naredna tri sata, računajući i ovaj u kome jesmo: u 16:40 je to 16 do 19, a backend
// ionako ne nudi termin koji je već počeo.
export function brziIzbor(id, sada, zona) {
	const danas = danUZoni(sada, zona)
	const sutra = dodajDane(danas, 1)
	const sat = Number(satUZoni(sada, zona).slice(0, 2))
	switch (id) {
		case 'sad':
			return { id, dan: danas, odSata: sat, doSata: Math.min(24, sat + 3) }
		case 'danas-16':
			return { id, dan: danas, odSata: 16, doSata: 24 }
		case 'sutra-ujutro':
			return { id, dan: sutra, odSata: 6, doSata: 12 }
		case 'sutra-popodne':
			return { id, dan: sutra, odSata: 12, doSata: 18 }
		default:
			throw new Error(`Nepoznat izbor ${id}`)
	}
}

// Termini istog početka idu u jedan red: "16:00" pa resursi koji su tada slobodni
export function poSatu(termini) {
	const redovi = []
	for (const t of termini) {
		const zadnji = redovi.at(-1)
		if (zadnji?.pocetak === t.pocetak) zadnji.termini.push(t)
		else redovi.push({ pocetak: t.pocetak, termini: [t] })
	}
	return redovi
}
