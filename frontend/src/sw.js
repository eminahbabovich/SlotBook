// Service worker SlotBooka. Radi tri stvari i ništa više:
// 1. čuva statiku iz builda (JS, CSS, fontove, ikone), da se aplikacija otvori brzo i poslije deploya
//    pređe na novu verziju;
// 2. kad stranica ne može do mreže, pokaže offline.html umjesto greške browsera;
// 3. prima push obavijesti i otvara stranicu na klik.
// /api ne dira nikad: termini, rezervacije i tokeni idu uvijek na server, jer bi stari odgovor iz keša
// pokazao slobodan termin koji je već zauzet.
import { clientsClaim } from 'workbox-core'
import { cleanupOutdatedCaches, matchPrecache, precacheAndRoute } from 'workbox-precaching'

// Spisak fajlova iz builda s heševima. vite-plugin-pwa ga upiše ovdje pri buildu (injectManifest).
precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

// Nova verzija preuzme otvorene stranice odmah, a ne tek kad se zatvore svi tabovi
self.skipWaiting()
clientsClaim()

self.addEventListener('fetch', (dogadjaj) => {
	const zahtjev = dogadjaj.request
	if (zahtjev.mode !== 'navigate' || new URL(zahtjev.url).pathname.startsWith('/api/')) return
	// Navigacija ide prvo na mrežu, pa se stranica uvijek učita svježa. Keš je samo rezerva bez mreže.
	dogadjaj.respondWith(fetch(zahtjev).catch(async () => (await matchPrecache('/offline.html')) ?? Response.error()))
})

self.addEventListener('push', (dogadjaj) => {
	let poruka = {}
	try {
		poruka = dogadjaj.data?.json() ?? {}
	} catch {
		poruka = { body: dogadjaj.data?.text() }
	}
	dogadjaj.waitUntil(
		self.registration.showNotification(poruka.title ?? 'SlotBook', {
			body: poruka.body,
			icon: '/ikone/ikona-192.png',
			badge: '/ikone/znak-96.png',
			lang: 'bs',
			data: { url: poruka.url ?? '/' },
		}),
	)
})

// Klik otvara stranicu iz obavijesti. Ako je SlotBook već otvoren, koristi se taj tab.
self.addEventListener('notificationclick', (dogadjaj) => {
	dogadjaj.notification.close()
	const adresa = new URL(dogadjaj.notification.data?.url ?? '/', self.location.origin)
	// Samo stranice SlotBooka, i kad bi poruka nekako nosila tuđu adresu
	if (adresa.origin !== self.location.origin) return
	dogadjaj.waitUntil(
		self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((tabovi) => {
			const otvoren = tabovi.find((t) => new URL(t.url).origin === adresa.origin)
			if (otvoren) return otvoren.navigate(adresa.href).then((t) => (t ?? otvoren).focus())
			return self.clients.openWindow(adresa.href)
		}),
	)
})
