// Ista pravila kao ProvjeraLozinke na backendu, da korisnik vidi grešku prije slanja. Listu čestih
// lozinaka zna samo backend (nekoliko stotina riječi ne treba svakom ko otvori formu), pa se ta
// poruka vidi tek poslije slanja.
const NAJMANJE = 10
const NIZOVI = [
	'01234567890123456789',
	'abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz',
	'qwertzuiopšđasdfghjklčćžyxcvbnm',
	'qwertyuiopasdfghjklzxcvbnm',
	'1qaz2wsx3edc4rfv5tgb6zhn7ujm8ik9ol0p',
]

// null kad je lozinka u redu, inače poruka (ista kao s backenda)
export function problemLozinke(lozinka, email = '') {
	if ([...lozinka].length < NAJMANJE) return `Lozinka treba imati najmanje ${NAJMANJE} znakova.`
	const mala = lozinka.toLowerCase()
	if (new Set(mala).size < 4) return 'Lozinka ne smije biti jedan ili dva znaka koji se ponavljaju.'
	const obrnuto = (niz) => [...niz].reverse().join('')
	if (NIZOVI.some((niz) => niz.includes(mala) || obrnuto(niz).includes(mala))) {
		return 'Lozinka ne smije biti niz s tastature ili redom slova i cifara.'
	}
	const ime = email.split('@')[0].toLowerCase()
	if (email.includes('@') && ime.length >= 4 && mala.includes(ime)) return 'Lozinka ne smije sadržati tvoj email.'
	return null
}

export const NAPOMENA_LOZINKE = 'Najmanje 10 znakova. Ne česta lozinka, niz s tastature ni tvoj email. Dobra je npr. tri riječi i broj.'
