// jsdom nema matchMedia, a motion i prekidač teme ga pitaju za prefers-reduced-motion i temu.
// Odgovor je uvijek "ne": testovi gledaju stanje poslije akcije, a ne animaciju.
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
	window.matchMedia = (upit) => ({
		matches: false,
		media: upit,
		onchange: null,
		addEventListener() {},
		removeEventListener() {},
		addListener() {},
		removeListener() {},
		dispatchEvent: () => false,
	})
}
