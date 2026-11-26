// Tamna tema je podrazumijevana, a svijetla je izbor. Izbor se pamti u browseru (samo udobnost,
// ništa ne zavisi od njega), a index.html ga postavi prije prvog crtanja, da ekran ne bljesne.
const KLJUC = 'slotbook-tema'

export function sacuvanaTema() {
	try {
		const tema = localStorage.getItem(KLJUC)
		return tema === 'svijetla' || tema === 'tamna' ? tema : null
	} catch {
		return null
	}
}

export function postaviTemu(tema) {
	document.documentElement.dataset.tema = tema
	try {
		localStorage.setItem(KLJUC, tema)
	} catch {
		// Privatni prozor ili blokirani podaci: tema važi do osvježavanja, i to je dovoljno
	}
}
