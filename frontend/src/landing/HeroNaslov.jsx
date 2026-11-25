import { useEffect, useRef } from 'react'

// Naslov čija slova mijenjaju širinu i težinu (ose fonta Anybody) prema kursoru, a na telefonu
// prema skrolu. Vrijednosti idu direktno u style svakog slova, u requestAnimationFrame petlji:
// kroz React stanje bi se 15 slova crtalo 60 puta u sekundi.
// Čitač ekrana čita cijeli naslov iz skrivenog teksta, a slova su mu sakrivena.
const MIRNO = { sirina: 100, tezina: 760 }
const BLIZU = { sirina: 140, tezina: 900 }
const DALEKO = { sirina: 72, tezina: 420 }
const DOMET = 260

export function HeroNaslov({ redovi, povrsina }) {
	const naslov = useRef(null)

	useEffect(() => {
		const h1 = naslov.current
		const okvir = povrsina.current
		if (!h1 || !okvir) return undefined
		if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined

		const slova = [...h1.querySelectorAll('.hn-slovo')]
		const stanje = slova.map(() => ({ ...MIRNO }))
		let centri = []
		let kursor = null
		let okvirId = 0
		const dodir = window.matchMedia?.('(hover: none)').matches

		function izmjeri() {
			centri = slova.map((s) => {
				const r = s.getBoundingClientRect()
				return { x: r.left + r.width / 2, y: r.top + r.height / 2 }
			})
		}

		function cilj(i) {
			if (dodir) {
				// Talas koji prolazi kroz slova dok se skrola
				const faza = window.scrollY / 90 - i * 0.55
				const t = (Math.sin(faza) + 1) / 2
				return { sirina: DALEKO.sirina + (BLIZU.sirina - DALEKO.sirina) * t, tezina: 560 + 340 * t }
			}
			if (!kursor) return MIRNO
			const c = centri[i]
			const d = Math.hypot(kursor.x - c.x, (kursor.y - c.y) * 1.4)
			const t = Math.max(0, 1 - d / DOMET)
			const blizu = t * t * (3 - 2 * t)
			return {
				sirina: DALEKO.sirina + (BLIZU.sirina - DALEKO.sirina) * blizu,
				tezina: DALEKO.tezina + (BLIZU.tezina - DALEKO.tezina) * blizu,
			}
		}

		function okret() {
			let miruje = true
			slova.forEach((s, i) => {
				const c = cilj(i)
				const st = stanje[i]
				st.sirina += (c.sirina - st.sirina) * 0.14
				st.tezina += (c.tezina - st.tezina) * 0.14
				if (Math.abs(c.sirina - st.sirina) > 0.3) miruje = false
				s.style.fontStretch = `${st.sirina.toFixed(1)}%`
				s.style.fontWeight = st.tezina.toFixed(0)
			})
			// Kad se sve smiri, petlja stane, da stranica ne troši bateriju u prazno
			okvirId = miruje && !dodir ? 0 : requestAnimationFrame(okret)
		}

		function pokreni() {
			if (!okvirId) okvirId = requestAnimationFrame(okret)
		}

		function pomak(d) {
			kursor = { x: d.clientX, y: d.clientY }
			pokreni()
		}

		function izlaz() {
			kursor = null
			pokreni()
		}

		function promjena() {
			izmjeri()
			pokreni()
		}

		izmjeri()
		okvir.addEventListener('pointermove', pomak)
		okvir.addEventListener('pointerleave', izlaz)
		window.addEventListener('resize', promjena)
		window.addEventListener('scroll', promjena, { passive: true })
		pokreni()
		return () => {
			cancelAnimationFrame(okvirId)
			okvir.removeEventListener('pointermove', pomak)
			okvir.removeEventListener('pointerleave', izlaz)
			window.removeEventListener('resize', promjena)
			window.removeEventListener('scroll', promjena)
		}
	}, [povrsina])

	return (
		<h1 className="hero-naslov" ref={naslov}>
			<span className="skriveno">{redovi.join(' ')}</span>
			{redovi.map((red) => (
				<span key={red} className="hn-red" aria-hidden="true">
					{[...red].map((slovo, i) => (
						<span key={i} className="hn-slovo">
							{slovo === ' ' ? ' ' : slovo}
						</span>
					))}
				</span>
			))}
		</h1>
	)
}
