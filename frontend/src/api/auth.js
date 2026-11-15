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

// Token se briše i kad poziv ne uspije, da korisnik na ovom računaru sigurno bude odjavljen.
export async function odjava() {
	try {
		await apiFetch('/api/auth/logout', { metoda: 'POST' })
	} finally {
		postaviToken(null)
	}
}

// UserResponse(id, email, displayName, emailVerified) iz GET /api/me.
export async function mojNalog() {
	const n = provjeriPolja(await apiFetch('/api/me'), ['id', 'email', 'displayName', 'emailVerified'], '/api/me')
	return { id: n.id, email: n.email, ime: n.displayName, emailPotvrden: n.emailVerified }
}
