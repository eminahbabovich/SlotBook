import { apiFetch, postaviToken, provjeriPolja } from './apiFetch.js'

// LoginRequest(email, password) -> TokenResponse(accessToken, tokenType, expiresIn).
// Refresh token stiže u HttpOnly kolačiću, JavaScript ga ne vidi.
export async function prijava(email, lozinka) {
	const odgovor = await apiFetch('/api/auth/login', { metoda: 'POST', tijelo: { email, password: lozinka } })
	postaviToken(provjeriPolja(odgovor, ['accessToken'], '/api/auth/login').accessToken)
}

// 202 bez tijela, isto za nov i postojeći email, da se po odgovoru ne vidi ko ima nalog.
// Prijava ide tek poslije potvrde emaila iz maila.
export async function registracija(ime, email, lozinka) {
	await apiFetch('/api/auth/register', { metoda: 'POST', tijelo: { email, password: lozinka, displayName: ime } })
}

// Token iz linka /potvrda-emaila#token=... (VerifyEmailRequest)
export async function potvrdiEmail(token) {
	await apiFetch('/api/auth/verify-email', { metoda: 'POST', tijelo: { token } })
}

// Uvijek 204, i kad nalog ne postoji ili je već potvrđen, pa UI ne smije reći da je mail sigurno poslan
export async function noviLinkZaPotvrdu(email) {
	await apiFetch('/api/auth/email-verification', { metoda: 'POST', tijelo: { email } })
}

// Uvijek 204, i za email bez naloga, pa UI kaže "ako nalog postoji, mail je poslan"
export async function zatraziNovuLozinku(email) {
	await apiFetch('/api/auth/password-reset', { metoda: 'POST', tijelo: { email } })
}

// Token iz linka /nova-lozinka#token=... (NewPasswordRequest). 204, a sesije na drugim uređajima se gase.
export async function postaviNovuLozinku(token, lozinka) {
	await apiFetch('/api/auth/password-reset/confirm', { metoda: 'POST', tijelo: { token, password: lozinka } })
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
