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

// Access token je samo u memoriji, ne u localStorage, da ga XSS ne može pročitati.
// Poslije osvježavanja stranice ga nema, pa se vraća preko refresh kolačića (obnoviSesiju).
let accessToken = null
let obnovaUToku = null
let javiIstek = () => {}

export function postaviToken(token) {
	accessToken = token
}

export function naIstekSesije(funkcija) {
	javiIstek = funkcija
}

// oblik 'blob' je za fajlove (CSV), sve ostalo je JSON
export async function apiFetch(putanja, { metoda = 'GET', tijelo, zaglavlja = {}, oblik = 'json' } = {}) {
	const poslaniToken = accessToken
	const odgovor = await posalji(putanja, metoda, tijelo, zaglavlja, poslaniToken)
	if (odgovor.status !== 401 || poslaniToken === null || putanja.startsWith('/api/auth/')) {
		return procitaj(odgovor, oblik)
	}

	// Ako je neki drugi zahtjev već obnovio token dok je ovaj čekao, dovoljno je ponoviti.
	if (accessToken === poslaniToken && !(await obnoviSesiju())) {
		return procitaj(odgovor, oblik)
	}
	return procitaj(await posalji(putanja, metoda, tijelo, zaglavlja, accessToken), oblik)
}

// Više zahtjeva može dobiti 401 u isto vrijeme. Svi čekaju isti refresh, jer bi drugi
// refresh sa već iskorištenim kolačićem backend vidio kao krađu i prekinuo sesiju.
export function obnoviSesiju() {
	if (obnovaUToku === null) {
		obnovaUToku = uBravi(posaljiRefresh).finally(() => {
			obnovaUToku = null
		})
	}
	return obnovaUToku
}

// Tabovi dijele kolačić, ali ne i memoriju. Brava (Web Locks) pušta jedan refresh u
// isto vrijeme kroz sve tabove, pa sljedeći tab pošalje već novi kolačić.
// Browser bez navigator.locks radi kao prije, s ograničenjem na jedan tab.
function uBravi(posao) {
	if (globalThis.navigator?.locks === undefined) {
		return posao()
	}
	return navigator.locks.request('slotbook-refresh', posao)
}

async function posaljiRefresh() {
	const odgovor = await posalji('/api/auth/refresh', 'POST', undefined, {}, null)
	if (odgovor.status === 401) {
		accessToken = null
		javiIstek()
		return false
	}
	const { accessToken: novi } = provjeriPolja(await procitaj(odgovor), ['accessToken'], '/api/auth/refresh')
	accessToken = novi
	return true
}

async function posalji(putanja, metoda, tijelo, zaglavlja, token) {
	const headers = { Accept: 'application/json, application/problem+json', ...zaglavlja }
	const opcije = { method: metoda, headers }
	if (token !== null) {
		headers.Authorization = `Bearer ${token}`
	}
	if (tijelo !== undefined) {
		headers['Content-Type'] = 'application/json'
		opcije.body = JSON.stringify(tijelo)
	}
	try {
		return await fetch(putanja, opcije)
	} catch {
		throw new ApiError(0, null)
	}
}

async function procitaj(odgovor, oblik) {
	if (!odgovor.ok) {
		throw new ApiError(odgovor.status, await procitajProblem(odgovor))
	}
	if (odgovor.status === 204) {
		return null
	}
	return oblik === 'blob' ? odgovor.blob() : odgovor.json()
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
