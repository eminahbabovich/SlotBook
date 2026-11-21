import { ApiError } from '../api/apiFetch.js'
import { satUZoni } from '../vrijeme/vrijeme.js'

// 0: odgovora nije bilo. 502-504: proxy ili hosting kažu da backend ne odgovara.
export function serverNeOdgovara(greska) {
	return greska instanceof ApiError && [0, 502, 503, 504].includes(greska.status)
}

// Backend već piše poruke za korisnika (detail, na bosanskom), pa se one prikazuju.
// Vlastiti tekst ide samo kad backend nije odgovorio ili nije poslao poruku.
// Kod 429 se doda i kad je sljedeći pokušaj, po satu korisnika, a ne organizacije: zaključana je
// prijava, ne termin. Sat, a ne "za 12 minuta", jer ne treba padež uz broj.
export function porukaGreske(greska, poruke, zona = Intl.DateTimeFormat().resolvedOptions().timeZone) {
	if (serverNeOdgovara(greska)) return poruke.serverNeOdgovara
	if (greska instanceof ApiError && typeof greska.problem?.detail === 'string') {
		if (greska.status === 429 && greska.retryAfter !== null) {
			const sat = satUZoni(Date.now() + greska.retryAfter * 1000, zona)
			return `${greska.problem.detail} Sljedeći pokušaj je moguć u ${sat}.`
		}
		return greska.problem.detail
	}
	return poruke.opsta
}

// greske iz 400 (PoljeGreska: polje, poruka) kao { email: 'Unesi email.' }.
export function greskePoPoljima(greska) {
	const greske = greska instanceof ApiError ? greska.problem?.greske : null
	if (!Array.isArray(greske)) return {}
	return Object.fromEntries(greske.map(({ polje, poruka }) => [polje, poruka]))
}
