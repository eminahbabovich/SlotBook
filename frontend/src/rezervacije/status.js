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
