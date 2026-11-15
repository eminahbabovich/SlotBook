import { apiFetch, postaviToken, provjeriPolja } from './apiFetch.js'

// LoginRequest(email, password) -> TokenResponse(accessToken, tokenType, expiresIn).
// Refresh token stiže u HttpOnly kolačiću, JavaScript ga ne vidi.
export async function prijava(email, lozinka) {
	const odgovor = await apiFetch('/api/auth/login', { metoda: 'POST', tijelo: { email, password: lozinka } })
	postaviToken(provjeriPolja(odgovor, ['accessToken'], '/api/auth/login').accessToken)
}

// Registracija vraća korisnika, ali ne i tokene, pa poslije nje ide obična prijava.
export async function registracija(ime, email, lozinka) {
	await apiFetch('/api/auth/register', { metoda: 'POST', tijelo: { email, password: lozinka, displayName: ime } })
	await prijava(email, lozinka)
}

// Token se briše tek kad server potvrdi odjavu. Refresh kolačić briše samo server, pa bi
// brisanje tokena bez toga bila lažna odjava: poslije osvježavanja stranice sesija se vrati.
export async function odjava() {
	await apiFetch('/api/auth/logout', { metoda: 'POST' })
	postaviToken(null)
}

// UserResponse(id, email, displayName, emailVerified) iz GET /api/me.
export async function mojNalog() {
	const n = provjeriPolja(await apiFetch('/api/me'), ['id', 'email', 'displayName', 'emailVerified'], '/api/me')
	return { id: n.id, email: n.email, ime: n.displayName, emailPotvrden: n.emailVerified }
}
