import { apiFetch, provjeriPolja } from './apiFetch.js'

// MemberResponse(userId, displayName, email, role, joinedAt) iz PageResponse. joinedAt ekran ne
// prikazuje. Backend ih slaže po datumu ulaska i sam bira sort, pa ga ovdje ne šaljemo.
export async function clanoviOrganizacije(organizacijaId, stranica, velicina = 50) {
	const putanja = `/api/organizations/${organizacijaId}/members?page=${stranica}&size=${velicina}`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['content', 'totalPages'], putanja)
	return {
		clanovi: odgovor.content.map((c) => {
			provjeriPolja(c, ['userId', 'displayName', 'email', 'role'], putanja)
			return { id: c.userId, ime: c.displayName, email: c.email, uloga: c.role }
		}),
		ukupnoStranica: odgovor.totalPages,
	}
}

// CreateInviteRequest(email, role) -> InviteResponse(id, email, role, expiresAt). Email u
// odgovoru je već normalizovan (mala slova), pa se njega prikazuje, a ne ono što je upisano.
export async function posaljiPozivnicu(organizacijaId, { email, uloga }) {
	const putanja = `/api/organizations/${organizacijaId}/invites`
	const p = provjeriPolja(await apiFetch(putanja, { metoda: 'POST', tijelo: { email, role: uloga } }), ['email', 'role', 'expiresAt'], putanja)
	return { email: p.email, uloga: p.role, vaziDo: p.expiresAt }
}

// AcceptInviteRequest(token) -> AcceptedInviteResponse(organizationId, slug, role). Token je
// iz linka u mailu. Pravo daje sam token, uz email prijavljenog naloga.
export async function prihvatiPozivnicu(token) {
	const putanja = '/api/invites/accept'
	const p = provjeriPolja(await apiFetch(putanja, { metoda: 'POST', tijelo: { token } }), ['slug', 'role'], putanja)
	return { slug: p.slug, uloga: p.role }
}
