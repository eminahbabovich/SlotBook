// Jedino mjesto u frontendu koje zove fetch. Komponente zovu funkcije iz src/api/,
// a svaka greška stiže kao ApiError, pa UI ima jedan način da je pročita.

export class ApiError extends Error {
	constructor(status, problem) {
		super(problem?.detail ?? `HTTP ${status}`)
		this.name = 'ApiError'
		// Status iz HTTP odgovora. 0 znači da odgovora nije ni bilo (server ne radi, nema mreže).
		this.status = status
		// Problem Details iz tijela (title, detail, greske...) ili null kad tijelo nije JSON.
		this.problem = problem
	}
}

export async function apiFetch(putanja, { metoda = 'GET', tijelo, zaglavlja = {} } = {}) {
	const headers = { Accept: 'application/json, application/problem+json', ...zaglavlja }
	const opcije = { method: metoda, headers }
	if (tijelo !== undefined) {
		headers['Content-Type'] = 'application/json'
		opcije.body = JSON.stringify(tijelo)
	}

	let odgovor
	try {
		odgovor = await fetch(putanja, opcije)
	} catch {
		throw new ApiError(0, null)
	}

	if (!odgovor.ok) {
		throw new ApiError(odgovor.status, await procitajProblem(odgovor))
	}
	if (odgovor.status === 204) {
		return null
	}
	return odgovor.json()
}

// Kad backend ne radi, Vite proxy ili hosting vrate HTML ili prazno tijelo, ne Problem Details.
async function procitajProblem(odgovor) {
	try {
		return await odgovor.json()
	} catch {
		return null
	}
}

// Polja bez kojih UI ne može se provjere odmah po dolasku. Kad backend promijeni oblik,
// greška kaže koja putanja i koje polje, umjesto da ekran tiho prikaže undefined.
export function provjeriPolja(odgovor, polja, putanja) {
	for (const polje of polja) {
		if (odgovor?.[polje] === undefined || odgovor[polje] === null) {
			throw new Error(`${putanja}: nema polja ${polje}`)
		}
	}
	return odgovor
}
