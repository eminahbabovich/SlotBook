import { apiFetch, provjeriPolja } from './apiFetch.js'

const putanja = (orgId, od, doDana) => `/api/organizations/${orgId}/stats?from=${od}&to=${doDana}`

// StatsResponse: od i do su datumi u zoni organizacije, do se ne uključuje (isto kao /slots).
// Provider dobije samo resurse koje vodi, a o tome odlučuje backend po ulozi.
export async function statistika(orgId, od, doDana) {
	const p = putanja(orgId, od, doDana)
	const odgovor = provjeriPolja(await apiFetch(p), ['timezone', 'byStatus', 'byDayAndHour', 'occupancy'], p)
	return {
		zona: odgovor.timezone,
		poStanju: odgovor.byStatus,
		poDanuISatu: odgovor.byDayAndHour.map((d) => ({ dan: d.dayOfWeek, sat: d.hour, broj: d.count })),
		popunjenost: odgovor.occupancy.map((r) => ({ resursId: r.resourceId, naziv: r.name, zauzeto: r.bookedMinutes, ponudjeno: r.offeredMinutes })),
	}
}

// Ista putanja, drugi oblik (Accept: text/csv). Blob, a ne tekst, jer bi text() bacio BOM,
// a bez njega Excel č i ć ne čita kao UTF-8.
export async function csvStatistike(orgId, od, doDana) {
	return apiFetch(putanja(orgId, od, doDana), { zaglavlja: { Accept: 'text/csv, application/problem+json' }, oblik: 'blob' })
}
