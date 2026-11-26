const EMAJLI = ['tirkiz', 'lapis', 'safran', 'sipak']

// Organizacija na listi dobije boju po redu, da dvije susjedne nikad nisu iste
export function emajlPoRedu(redniBroj) {
	return `var(--emajl-${EMAJLI[redniBroj % EMAJLI.length]})`
}

// Dva ili jedno slovo za kvadrat na pločici: "Čitaonica PMF" je ČP, "Sala" je S
export function inicijali(naziv) {
	return naziv
		.split(/\s+/)
		.filter((dio) => /\p{L}/u.test(dio[0] ?? ''))
		.slice(0, 2)
		.map((dio) => dio[0].toUpperCase())
		.join('')
}

// Boja resursa iz njegovog ID-ja: ista na svakom uređaju i poslije osvježavanja, a bez novog polja
// u bazi. Dva resursa mogu dobiti istu boju; boja je pomoć oku, resurs se prepoznaje po imenu.
export function emajlResursa(id) {
	let zbir = 0
	for (const znak of String(id)) zbir = (zbir * 31 + znak.charCodeAt(0)) >>> 0
	return `var(--emajl-${EMAJLI[zbir % EMAJLI.length]})`
}
