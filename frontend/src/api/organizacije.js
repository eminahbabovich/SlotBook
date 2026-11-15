import { apiFetch, provjeriPolja } from './apiFetch.js'

const POLJA = ['id', 'name', 'slug', 'timezone', 'role']

// MyOrganizationResponse(id, name, slug, timezone, role) iz GET /api/me/organizations.
export async function mojeOrganizacije() {
	const lista = await apiFetch('/api/me/organizations')
	if (!Array.isArray(lista)) {
		throw new Error('/api/me/organizations: odgovor nije lista')
	}
	return lista.map((o) => {
		provjeriPolja(o, POLJA, '/api/me/organizations')
		return { id: o.id, naziv: o.name, slug: o.slug, zona: o.timezone, uloga: o.role }
	})
}

// CreateOrganizationRequest(name, slug, timezone) -> OrganizationResponse. Ko napravi
// organizaciju, postaje njen ADMIN.
export async function novaOrganizacija({ naziv, slug, zona }) {
	const o = await apiFetch('/api/organizations', { metoda: 'POST', tijelo: { name: naziv, slug, timezone: zona } })
	return provjeriPolja(o, ['slug'], '/api/organizations')
}
