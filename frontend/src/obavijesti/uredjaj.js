// Sve što push traži od browsera je ovdje: service worker, dozvola i pretplata kod push servisa.
// Komponente ne diraju navigator direktno, a testovi ovaj modul zamijene lažnim.

export function podrzava() {
	return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

// Na iPhoneu i iPadu push radi samo u aplikaciji dodanoj na početni ekran (iOS 16.4+), ne u Safariju
export function naIPhoneu() {
	return /iPhone|iPad|iPod/.test(navigator.userAgent)
}

export function dozvola() {
	return Notification.permission
}

// Service worker se registruje tek poslije učitavanja stranice, a u dev modu ga uopšte nema.
// Zato se na njega čeka najviše pet sekundi, pa null.
async function registracija() {
	const istek = new Promise((razrijesi) => setTimeout(() => razrijesi(null), 5000))
	return Promise.race([navigator.serviceWorker.ready, istek])
}

export async function trenutnaPretplata() {
	const reg = await registracija()
	return reg ? reg.pushManager.getSubscription() : null
}

export async function pretplati(javniKljuc) {
	if ((await Notification.requestPermission()) !== 'granted') return null
	const reg = await registracija()
	if (!reg) throw new Error('Service worker nije registrovan')
	return reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: uBajtove(javniKljuc) })
}

// Kratak opis uređaja za listu na serveru, iz user agenta. Ne mora biti tačan, samo prepoznatljiv.
export function opisUredjaja() {
	const ua = navigator.userAgent
	const browser = /Edg\//.test(ua) ? 'Edge' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : 'browser'
	return `${/Mobi|iPhone|Android/.test(ua) ? 'Telefon' : 'Računar'}, ${browser}`
}

// VAPID ključ stiže kao base64url, a pushManager ga traži kao bajtove
function uBajtove(base64url) {
	const base64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
	return Uint8Array.from(atob(base64), (z) => z.charCodeAt(0))
}

// Bez čekanja: za odjavu, koja ne smije stati pet sekundi kad service workera nema
export async function pretplataAkoPostoji() {
	const reg = await navigator.serviceWorker.getRegistration()
	return reg ? reg.pushManager.getSubscription() : null
}
