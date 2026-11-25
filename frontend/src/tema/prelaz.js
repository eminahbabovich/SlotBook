import { flushSync } from 'react-dom'

// Promjena React stanja kao View Transition: browser uslika stari i novi izgled i animira razliku.
// Bez podrške u browseru ili uz smanjen pokret promjena se desi odmah, isto kao bez ovoga.
// vrsta ide u data-prelaz na <html>, da CSS zna koji je prelaz (žeton, ekran).
// let: element koji preleti na mjesto elementa s istim imenom u novom izgledu (žeton u kartu).
export function uzPrelaz(promjena, { vrsta, let: polaziste } = {}) {
	const bezPokreta = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
	if (typeof document.startViewTransition !== 'function' || bezPokreta) {
		promjena()
		return
	}
	const html = document.documentElement
	if (vrsta) html.dataset.prelaz = vrsta
	if (polaziste) {
		// Ime mora biti jedinstveno u starom izgledu, a stara karta ga već nosi. Ona ionako nestaje.
		for (const el of document.querySelectorAll(`[data-let="${polaziste.ime}"]`)) el.style.viewTransitionName = 'none'
		polaziste.element.style.viewTransitionName = polaziste.ime
	}
	const prelaz = document.startViewTransition(() => {
		if (polaziste) polaziste.element.style.viewTransitionName = ''
		// React mora upisati novi izgled odmah, unutar poziva, inače browser uslika stari dvaput
		flushSync(promjena)
	})
	prelaz.finished.finally(() => {
		if (html.dataset.prelaz === vrsta) delete html.dataset.prelaz
	})
}
