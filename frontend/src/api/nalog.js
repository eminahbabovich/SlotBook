import { apiFetch, postaviToken, provjeriPolja } from './apiFetch.js'

// MyDataExport iz GET /api/me/export. UI ga ne čita, nego ga daje korisniku kao fajl, pa ide kao blob.
export function izvozPodataka() {
	return apiFetch('/api/me/export', { oblik: 'blob' })
}

// 204 kad je nalog anonimiziran. 400 (greske.password) za pogrešnu lozinku, 409 s listama
// organizacije i resursi, 429 kad je prijava zaključana.
export async function obrisiNalog(lozinka) {
	await apiFetch('/api/me', { metoda: 'DELETE', tijelo: { password: lozinka } })
	postaviToken(null)
}

// CalendarFeedResponse: token je tu samo u odgovoru na POST, kad je link upravo napravljen.
function link(odgovor, putanja, polja) {
	provjeriPolja(odgovor, ['active', ...polja], putanja)
	return { aktivan: odgovor.active, napravljen: odgovor.createdAt ?? null, token: odgovor.token ?? null }
}

export async function linkZaKalendar() {
	const putanja = '/api/me/calendar-feed'
	return link(await apiFetch(putanja), putanja, [])
}

export async function noviLinkZaKalendar() {
	const putanja = '/api/me/calendar-feed'
	return link(await apiFetch(putanja, { metoda: 'POST' }), putanja, ['createdAt', 'token'])
}

export function ugasiLinkZaKalendar() {
	return apiFetch('/api/me/calendar-feed', { metoda: 'DELETE' })
}

// Adresa frontenda, ne backenda: na produkciji frontend prosljeđuje /api backendu, kao Vite lokalno
export function adresaKalendara(token, lokacija) {
	return `${lokacija.origin}/api/me/calendar.ics?token=${encodeURIComponent(token)}`
}
