const EMAJLI = ['tirkiz', 'lapis', 'safran', 'sipak']

// Boja resursa iz njegovog ID-ja: ista na svakom uređaju i poslije osvježavanja, a bez novog polja
// u bazi. Dva resursa mogu dobiti istu boju; boja je pomoć oku, resurs se prepoznaje po imenu.
export function emajlResursa(id) {
	let zbir = 0
	for (const znak of String(id)) zbir = (zbir * 31 + znak.charCodeAt(0)) >>> 0
	return `var(--emajl-${EMAJLI[zbir % EMAJLI.length]})`
}
