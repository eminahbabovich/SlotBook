import { useEffect } from 'react'

// Reflektor na karticama landinga: kartica ispod miša dobije --mis-x i --mis-y (položaj u pikselima
// od svog gornjeg lijevog ugla), a CSS tu nacrta blagu mjedenu svjetlost. Jedan slušač na cijeloj
// stranici umjesto jednog po kartici. Prst i olovka ne pale reflektor, jer na dodir nema hovera.
export function useReflektor(ref) {
	useEffect(() => {
		const stranica = ref.current
		if (!stranica) return undefined
		const pomjeri = (dogadjaj) => {
			if (dogadjaj.pointerType && dogadjaj.pointerType !== 'mouse') return
			const kartica = dogadjaj.target.closest?.('.kartica')
			if (!kartica) return
			const okvir = kartica.getBoundingClientRect()
			kartica.style.setProperty('--mis-x', `${dogadjaj.clientX - okvir.left}px`)
			kartica.style.setProperty('--mis-y', `${dogadjaj.clientY - okvir.top}px`)
		}
		stranica.addEventListener('pointermove', pomjeri)
		return () => stranica.removeEventListener('pointermove', pomjeri)
	}, [ref])
}
