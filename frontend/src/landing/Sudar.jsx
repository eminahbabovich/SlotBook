import { motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

const BROJ = 20
const RADIJUS = 0.42 // dio širine polja, od centra do žetona u krugu

// Dvadeset žetona krene na isti termin u centru. Jedan se zaključa (201), ostali se odbiju (409).
// Ovo je animacija onoga što test radi na pravoj bazi, ne poziv servera: piše i ispod.
export function Sudar() {
	const [faza, setFaza] = useState('miruje')
	const [pobjednik, setPobjednik] = useState(0)
	const tajmeri = useRef([])
	const polje = useRef(null)
	const [sirina, setSirina] = useState(320)

	useEffect(() => () => tajmeri.current.forEach(clearTimeout), [])

	// Žetoni stoje u centru i pomjeraju se transformom (x, y), a ne left/top, pa treba prava širina polja
	useEffect(() => {
		const el = polje.current
		if (!el || typeof ResizeObserver === 'undefined') return undefined
		const posmatrac = new ResizeObserver(([unos]) => setSirina(unos.contentRect.width))
		posmatrac.observe(el)
		return () => posmatrac.disconnect()
	}, [])

	function posalji() {
		tajmeri.current.forEach(clearTimeout)
		setPobjednik(Math.floor(Math.random() * BROJ))
		setFaza('juri')
		tajmeri.current = [setTimeout(() => setFaza('gotovo'), 750)]
	}

	const gotovo = faza === 'gotovo'

	return (
		<section className="sekcija" aria-labelledby="sudar-naslov">
			<div className="sekcija-tekst">
				<h2 id="sudar-naslov" className="sekcija-naslov">
					Dvadeset ruku, jedan termin
				</h2>
				<p>
					Konsultacije kod profesora, ponedjeljak u 10:00. Dvadeset studenata klikne u istoj sekundi. Prođe tačno jedan, a ostalih
					devetnaest dobije poruku da je termin upravo zauzet.
				</p>
				<p>
					To ne čuva Java kod, nego baza: <code>EXCLUDE</code> constraint ne pušta dvije rezervacije koje se preklapaju na istom
					resursu, ma koliko ih stiglo odjednom.
				</p>
				<p className="dokaz">
					Dokaz: <code>BookingIstovremenoApiTest</code> pušta 20 niti na pravi Postgres, u četiri runde. Svaki put jedan odgovor
					201 i devetnaest 409, nijedan 500.
				</p>
				<button type="button" className="dugme dugme-mjed" onClick={posalji} disabled={faza === 'juri'}>
					{faza === 'miruje' ? 'Pošalji 20 zahtjeva odjednom' : 'Pošalji ponovo'}
				</button>
			</div>

			<div className="sudar-polje" aria-hidden="true" ref={polje}>
				<div className={`sudar-termin ${gotovo ? 'sudar-termin-uzet' : ''}`}>
					<time>10:00</time>
				</div>
				{Array.from({ length: BROJ }, (_, i) => {
					const ugao = (i / BROJ) * Math.PI * 2 - Math.PI / 2
					const x = Math.cos(ugao) * RADIJUS * sirina
					const y = Math.sin(ugao) * RADIJUS * sirina
					const jePobjednik = gotovo && i === pobjednik
					const uCentru = faza === 'juri' || jePobjednik
					return (
						<motion.span
							key={i}
							className={`sudar-zeton ${jePobjednik ? 'sudar-201' : ''} ${gotovo && !jePobjednik ? 'sudar-409' : ''}`}
							initial={false}
							animate={uCentru ? { x: 0, y: 0, scale: 0.7, opacity: jePobjednik ? 0 : 1 } : { x, y, scale: 1, opacity: 1 }}
							transition={
								faza === 'juri'
									? { duration: 0.42, delay: (i % 7) * 0.025, ease: [0.5, 0, 0.75, 0] }
									: { type: 'spring', stiffness: 260, damping: 14, delay: (i % 5) * 0.02 }
							}
						>
							{gotovo && !jePobjednik ? '409' : ''}
						</motion.span>
					)
				})}
			</div>
			<p className="skriveno" aria-live="polite">
				{gotovo ? 'Jedan zahtjev je prošao s 201, devetnaest je dobilo 409.' : ''}
			</p>
		</section>
	)
}
