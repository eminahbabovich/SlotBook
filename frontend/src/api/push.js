import { apiFetch, provjeriPolja } from './apiFetch.js'

// PushKeyResponse: enabled je false kad server nema VAPID ključeve, i tada se push ne nudi
export async function pushNaServeru() {
	const putanja = '/api/push/public-key'
	const o = provjeriPolja(await apiFetch(putanja), ['enabled'], putanja)
	return { ukljucen: o.enabled, javniKljuc: o.publicKey ?? null }
}

// PUT /api/me/push-subscriptions s onim što browser da u toJSON(): endpoint i ključevi. Isti browser
// pod drugim nalogom prelazi na nalog koji je sad prijavljen.
export async function sacuvajPretplatu(pretplata, uredjaj) {
	const { endpoint, keys } = pretplata.toJSON()
	await apiFetch('/api/me/push-subscriptions', { metoda: 'PUT', tijelo: { endpoint, keys: { p256dh: keys.p256dh, auth: keys.auth }, device: uredjaj } })
}

export async function obrisiPretplatu(endpoint) {
	await apiFetch(`/api/me/push-subscriptions?endpoint=${encodeURIComponent(endpoint)}`, { metoda: 'DELETE' })
}

// 204 kad je push servis primio poruku, 404 kad uređaj više nije prijavljen, 502 kad push servis ne radi
export async function probnaObavijest(endpoint) {
	await apiFetch('/api/me/push-subscriptions/test', { metoda: 'POST', tijelo: { endpoint } })
}
