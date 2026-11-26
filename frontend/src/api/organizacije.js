import { apiFetch, provjeriPolja } from './apiFetch.js'

const POLJA = ['id', 'name', 'slug', 'timezone', 'role']

// NoShowRules: sva tri broja ili nijedan. null znači da pravila nema.
const izNedolazaka = (n) => (n?.limit == null ? null : { nedolazaka: n.limit, periodDana: n.windowDays, blokadaDana: n.blockDays })

// MyOrganizationResponse(id, name, slug, timezone, role, noShowRules) iz GET /api/me/organizations.
// noShowRules vidi svaki član, da klijent zna pravilo prije nego što ga prekrši.
export async function mojeOrganizacije() {
	const lista = await apiFetch('/api/me/organizations')
	if (!Array.isArray(lista)) {
		throw new Error('/api/me/organizations: odgovor nije lista')
	}
	return lista.map((o) => {
		provjeriPolja(o, POLJA, '/api/me/organizations')
		return { id: o.id, naziv: o.name, slug: o.slug, zona: o.timezone, uloga: o.role, nedolasci: izNedolazaka(o.noShowRules) }
	})
}

// CreateOrganizationRequest(name, slug, timezone) -> OrganizationResponse. Ko napravi
// organizaciju, postaje njen ADMIN.
export async function novaOrganizacija({ naziv, slug, zona }) {
	const o = await apiFetch('/api/organizations', { metoda: 'POST', tijelo: { name: naziv, slug, timezone: zona } })
	return provjeriPolja(o, ['slug'], '/api/organizations')
}

// PUT /api/organizations/{id}/no-show-rules. Prazna polja idu kao null: sva tri prazna ukidaju pravilo.
export async function promijeniNedolaske(organizacijaId, { nedolazaka, periodDana, blokadaDana }) {
	const broj = (v) => (v === '' || v == null ? null : Number(v))
	const odgovor = await apiFetch(`/api/organizations/${organizacijaId}/no-show-rules`, {
		metoda: 'PUT',
		tijelo: { limit: broj(nedolazaka), windowDays: broj(periodDana), blockDays: broj(blokadaDana) },
	})
	return izNedolazaka(odgovor)
}

// GET /api/me/blocks: organizacije u kojima sam sada blokiran zbog nedolazaka
export async function mojeBlokade() {
	const lista = await apiFetch('/api/me/blocks')
	if (!Array.isArray(lista)) {
		throw new Error('/api/me/blocks: odgovor nije lista')
	}
	return lista.map((b) => {
		provjeriPolja(b, ['organizationId', 'organizationName', 'timezone', 'blockedUntil'], '/api/me/blocks')
		return { organizacijaId: b.organizationId, naziv: b.organizationName, zona: b.timezone, do: b.blockedUntil, nedolazaka: b.noShows }
	})
}
