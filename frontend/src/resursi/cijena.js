// Backend čuva cijenu u feninzima (1500 je 15 KM), a osoba je upisuje u KM, sa zarezom ili tačkom.
// Najviše dvije decimale: 7,555 KM ne postoji, a ne treba ga tiho zaokružiti.
const IZNOS = /^(\d{1,6})(?:[.,](\d{1,2}))?$/

// null znači da unos nije iznos. Ne smije postati 0, jer bi plaćen termin tiho postao besplatan.
export function kmUFeninge(tekst) {
	const unos = tekst.trim()
	if (unos === '') return 0
	const dijelovi = IZNOS.exec(unos)
	if (!dijelovi) return null
	// Cijeli dio i feninzi odvojeno, bez množenja decimalnog broja (0,29 * 100 nije tačno 29)
	return Number(dijelovi[1]) * 100 + Number((dijelovi[2] ?? '').padEnd(2, '0'))
}

export function feningeUKm(feninzi) {
	const km = Math.floor(feninzi / 100)
	const ostatak = feninzi % 100
	return ostatak === 0 ? String(km) : `${km},${String(ostatak).padStart(2, '0')}`
}
