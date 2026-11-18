// Isto pravilo kao ResourceAccess.canManage na backendu. Ovdje samo odlučuje šta se prikazuje,
// a backend i dalje odbije svakog drugog sa 403.
export function vodiResurs(organizacija, resurs, korisnikId) {
	return organizacija.uloga === 'ADMIN' || (organizacija.uloga === 'PROVIDER' && resurs.vodi === korisnikId)
}

// Kao @PreAuthorize na POST /api/organizations/{id}/resources: admin i ko vodi termine.
export function mozeDodatiResurs(organizacija) {
	return organizacija.uloga === 'ADMIN' || organizacija.uloga === 'PROVIDER'
}
