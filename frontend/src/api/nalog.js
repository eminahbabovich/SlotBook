import { apiFetch, postaviToken } from './apiFetch.js'

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
