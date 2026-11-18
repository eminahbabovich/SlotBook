import { apiFetch, provjeriPolja } from './apiFetch.js'

// AvailabilityResponse(timezone, rules). LocalTime stiže kao "08:00:00", a <input type="time">
// prikazuje i šalje "08:00", pa se sekunde ovdje odbace. Backend prima oba oblika.
export async function radnoVrijeme(resursId) {
	const putanja = `/api/resources/${resursId}/availability`
	const odgovor = provjeriPolja(await apiFetch(putanja), ['timezone', 'rules'], putanja)
	return {
		zona: odgovor.timezone,
		intervali: odgovor.rules.map((p) => {
			provjeriPolja(p, ['dayOfWeek', 'start', 'end'], putanja)
			return { dan: p.dayOfWeek, od: p.start.slice(0, 5), do: p.end.slice(0, 5) }
		}),
	}
}

// AvailabilityRequest: cijela sedmica odjednom, prazna lista znači da resurs ne radi.
// Redoslijed je bitan, jer greške stižu kao rules[3].end.
export async function sacuvajRadnoVrijeme(resursId, intervali) {
	const putanja = `/api/resources/${resursId}/availability`
	const tijelo = { rules: intervali.map((i) => ({ dayOfWeek: i.dan, start: i.od, end: i.do })) }
	return provjeriPolja(await apiFetch(putanja, { metoda: 'PUT', tijelo }), ['rules'], putanja)
}

// TimeOffResponse(id, startsAt, endsAt, reason) iz PageResponse. Backend vraća samo one koji
// još traju ili tek dolaze. Pedeset unaprijed je više nego što iko upiše.
export async function izuzeciResursa(resursId) {
	const putanja = `/api/resources/${resursId}/time-off?size=50`
	const stranica = provjeriPolja(await apiFetch(putanja), ['content'], putanja)
	return stranica.content.map((t) => {
		provjeriPolja(t, ['id', 'startsAt', 'endsAt'], putanja)
		return { id: t.id, od: t.startsAt, do: t.endsAt, razlog: t.reason ?? null }
	})
}

// TimeOffRequest traži OffsetDateTime ("2026-12-24T00:00:00+01:00"). Pomak računa
// uTrenutakSPomakom iz vrijeme.js, ovdje se samo šalje.
export async function noviIzuzetak(resursId, { od, do: doKada, razlog }) {
	const putanja = `/api/resources/${resursId}/time-off`
	const tijelo = { startsAt: od, endsAt: doKada, reason: razlog || null }
	return provjeriPolja(await apiFetch(putanja, { metoda: 'POST', tijelo }), ['id'], putanja)
}

export async function ukloniIzuzetak(resursId, izuzetakId) {
	await apiFetch(`/api/resources/${resursId}/time-off/${izuzetakId}`, { metoda: 'DELETE' })
}
