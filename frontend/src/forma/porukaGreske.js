import { ApiError } from '../api/apiFetch.js'

// 0: odgovora nije bilo. 502-504: proxy ili hosting kažu da backend ne odgovara.
export function serverNeOdgovara(greska) {
	return greska instanceof ApiError && [0, 502, 503, 504].includes(greska.status)
}

// Backend već piše poruke za korisnika (detail, na bosanskom), pa se one prikazuju.
// Vlastiti tekst ide samo kad backend nije odgovorio ili nije poslao poruku.
export function porukaGreske(greska, poruke) {
	if (serverNeOdgovara(greska)) return poruke.serverNeOdgovara
	if (greska instanceof ApiError && typeof greska.problem?.detail === 'string') return greska.problem.detail
	return poruke.opsta
}

// greske iz 400 (PoljeGreska: polje, poruka) kao { email: 'Unesi email.' }.
export function greskePoPoljima(greska) {
	const greske = greska instanceof ApiError ? greska.problem?.greske : null
	if (!Array.isArray(greske)) return {}
	return Object.fromEntries(greske.map(({ polje, poruka }) => [polje, poruka]))
}
