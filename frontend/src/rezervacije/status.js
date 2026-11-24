// BookingStatus riječima, isto za klijenta i za onoga ko vodi termin
export const STATUS = {
	PENDING: 'čeka potvrdu',
	CONFIRMED: 'potvrđena',
	REJECTED: 'odbijena',
	EXPIRED: 'istekla, niko je nije potvrdio',
	CANCELLED: 'otkazana',
	COMPLETED: 'završena',
	NO_SHOW: 'nedolazak',
}

// Samo ova dva stanja drže termin (BookingStatus.DRZE_TERMIN)
export const DRZE_TERMIN = ['PENDING', 'CONFIRMED']

// Plaćenu rezervaciju potvrđuje uplata, a ne osoblje, pa i stanje kaže to. rokUplate postoji samo
// dok čeka uplatu (backend), a istekla s cijenom nikad nije plaćena, jer plaćena ne ističe.
export function opisStatusa(r) {
	if (r.status === 'PENDING' && r.rokUplate) return 'čeka uplatu'
	if (r.status === 'EXPIRED' && r.cijenaFeninga > 0) return 'istekla, nije plaćena na vrijeme'
	return STATUS[r.status] ?? r.status
}

// Potvrđena s cijenom je plaćena: plaćenu potvrđuje samo uplata (backend, BookingService.changeStatus)
export function placena(r) {
	return r.status === 'CONFIRMED' && r.cijenaFeninga > 0
}
