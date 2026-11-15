import { ApiError } from '../api/apiFetch.js'

// 0: odgovora nije bilo. 502-504: proxy ili hosting kažu da backend ne odgovara.
const SERVER_NE_ODGOVARA = [0, 502, 503, 504]

// Backend već piše poruke za korisnika (detail, na bosanskom), pa se one prikazuju.
// Vlastiti tekst ide samo kad backend nije odgovorio ili nije poslao poruku.
export function porukaGreske(greska, { serverNeOdgovara, opsta }) {
	if (greska instanceof ApiError) {
		if (SERVER_NE_ODGOVARA.includes(greska.status)) return serverNeOdgovara
		if (typeof greska.problem?.detail === 'string') return greska.problem.detail
	}
	return opsta
}

// greske iz 400 (PoljeGreska: polje, poruka) kao { email: 'Unesi email.' }.
export function greskePoPoljima(greska) {
	const greske = greska instanceof ApiError ? greska.problem?.greske : null
	if (!Array.isArray(greske)) return {}
	return Object.fromEntries(greske.map(({ polje, poruka }) => [polje, poruka]))
}
