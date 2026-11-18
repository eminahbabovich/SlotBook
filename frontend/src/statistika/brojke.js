// Minute kao sati s jednom decimalom i zarezom: 240 -> "4 h", 90 -> "1,5 h"
export function sati(minuta) {
	return `${String(Math.round(minuta / 6) / 10).replace('.', ',')} h`
}

// Isto zaokruživanje kao OccupancyCsv na backendu, da ekran i CSV kažu isti broj
export function procenat(zauzeto, ponudjeno) {
	return ponudjeno === 0 ? null : Math.round((zauzeto * 100) / ponudjeno)
}

// 1 rezervacija, 2-4 rezervacije, 5+ rezervacija, ali 11-14 rezervacija, 21 rezervacija
export function brojRezervacija(broj) {
	const jedinica = broj % 10
	const desetica = broj % 100
	if (jedinica === 1 && desetica !== 11) return `${broj} rezervacija`
	if (jedinica >= 2 && jedinica <= 4 && (desetica < 12 || desetica > 14)) return `${broj} rezervacije`
	return `${broj} rezervacija`
}
