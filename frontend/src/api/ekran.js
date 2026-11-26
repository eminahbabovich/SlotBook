import { apiFetch, provjeriPolja } from './apiFetch.js'
import { pratiTok } from './tok.js'

// ScreenView: javno, bez prijave. Termin nosi samo vrijeme i broj slobodnih mjesta (0 je pun).
export async function ekran(token) {
	const putanja = `/api/screens/${encodeURIComponent(token)}`
	const e = provjeriPolja(await apiFetch(putanja), ['organizationName', 'timezone', 'now', 'resources'], putanja)
	return {
		naziv: e.organizationName,
		zona: e.timezone,
		dan: e.date,
		// Koliko sat uređaja kasni ili žuri za serverom, izmjereno kad je odgovor stigao
		pomakSata: Date.parse(e.now) - Date.now(),
		pozivniLink: e.inviteUrl ?? null,
		resursi: e.resources.map((r) => ({
			id: r.id,
			naziv: r.name,
			brojMjesta: r.capacity,
			termini: r.slots.map((t) => ({ pocetak: t.start, kraj: t.end, slobodnih: t.seatsLeft })),
		})),
	}
}

export function pratiEkran(token, opcije) {
	return pratiTok(`/api/screens/${encodeURIComponent(token)}/stream`, opcije)
}

// DoorScreenResponse, za admina u postavkama
const izOdgovora = (e) => ({ id: e.id, resursId: e.resourceId ?? null, resurs: e.resourceName ?? null, adresa: e.url, napravljen: e.createdAt })

export async function ekraniOrganizacije(organizacijaId) {
	const lista = await apiFetch(`/api/organizations/${organizacijaId}/screens`)
	return lista.map(izOdgovora)
}

// Bez resursa: ekran za cijelu organizaciju. Novi ekran za isto mjesto gasi stari.
export async function napraviEkran(organizacijaId, resursId) {
	const putanja = `/api/organizations/${organizacijaId}/screens`
	return izOdgovora(await apiFetch(putanja, { metoda: 'POST', tijelo: resursId ? { resourceId: resursId } : {} }))
}

export async function ugasiEkran(organizacijaId, ekranId) {
	await apiFetch(`/api/organizations/${organizacijaId}/screens/${ekranId}`, { metoda: 'DELETE' })
}
