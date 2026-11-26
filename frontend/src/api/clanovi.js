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

// PUT .../members/{userId}/role -> MemberResponse. 409 kad bi organizacija ostala bez admina ili kad
// član vodi resurse (tada problem nosi i "resursi", spisak naziva).
export async function promijeniUlogu(organizacijaId, clanId, uloga) {
	const putanja = `/api/organizations/${organizacijaId}/members/${clanId}/role`
	const c = provjeriPolja(await apiFetch(putanja, { metoda: 'PUT', tijelo: { role: uloga } }), ['userId', 'role'], putanja)
	return { id: c.userId, uloga: c.role }
}

// 204. Buduće rezervacije uklonjenog člana backend otkaže i javi mu mailom.
export async function ukloniClana(organizacijaId, clanId) {
	await apiFetch(`/api/organizations/${organizacijaId}/members/${clanId}`, { metoda: 'DELETE' })
}

// InviteResponse(id, email, role, expiresAt), samo neprihvaćene, i istekle, da admin vidi da treba novu
export async function otvorenePozivnice(organizacijaId) {
	const putanja = `/api/organizations/${organizacijaId}/invites?size=50`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['content'], putanja)
	return odgovor.content.map((p) => {
		provjeriPolja(p, ['id', 'email', 'role', 'expiresAt'], putanja)
		return { id: p.id, email: p.email, uloga: p.role, vaziDo: p.expiresAt }
	})
}

export async function povuciPozivnicu(organizacijaId, pozivnicaId) {
	await apiFetch(`/api/organizations/${organizacijaId}/invites/${pozivnicaId}`, { metoda: 'DELETE' })
}

// InviteLinkResponse(id, url, maxUses, uses, expiresAt, revokedAt, createdAt, active)
function uPozivniLink(l, putanja) {
	provjeriPolja(l, ['id', 'url', 'maxUses', 'uses', 'expiresAt', 'active'], putanja)
	return { id: l.id, url: l.url, mjesta: l.maxUses, iskoristeno: l.uses, vaziDo: l.expiresAt, povucen: l.revokedAt !== null, aktivan: l.active }
}

export async function pozivniLinkovi(organizacijaId) {
	const putanja = `/api/organizations/${organizacijaId}/invite-links?size=20`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['content'], putanja)
	return odgovor.content.map((l) => uPozivniLink(l, putanja))
}

export async function napraviPozivniLink(organizacijaId, { mjesta, dana }) {
	const putanja = `/api/organizations/${organizacijaId}/invite-links`
	return uPozivniLink(await apiFetch(putanja, { metoda: 'POST', tijelo: { maxUses: mjesta, days: dana } }), putanja)
}

export async function povuciPozivniLink(organizacijaId, linkId) {
	await apiFetch(`/api/organizations/${organizacijaId}/invite-links/${linkId}`, { metoda: 'DELETE' })
}

// Javno, i bez prijave: InviteLinkPreviewResponse(organizationName, slug, usable)
export async function pregledPozivnogLinka(token) {
	const putanja = `/api/invite-links/${encodeURIComponent(token)}`
	const p = provjeriPolja(await apiFetch(putanja), ['organizationName', 'slug', 'usable'], putanja)
	return { naziv: p.organizationName, slug: p.slug, upotrebljiv: p.usable }
}

// JoinedResponse(organizationId, slug, name, alreadyMember). Već član nije greška: backend vrati
// alreadyMember true, a uloga mu ostane kakva je bila.
export async function pridruziSe(token) {
	const putanja = '/api/invite-links/join'
	const p = provjeriPolja(await apiFetch(putanja, { metoda: 'POST', tijelo: { token } }), ['slug', 'name', 'alreadyMember'], putanja)
	return { slug: p.slug, naziv: p.name, vecClan: p.alreadyMember }
}
