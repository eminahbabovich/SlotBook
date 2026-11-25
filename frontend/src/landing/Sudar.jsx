import { motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

const BROJ = 20
const RADIJUS = 0.42 // dio širine polja, od centra do žetona u krugu

// Dvadeset žetona krene na isti termin u centru. Jedan ga dobije, ostali se odbiju. Animacija onoga
// što test radi na pravoj bazi (README), ne poziv servera.
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
		<article className="oslonac-kartica oslonac-sudar">
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
							className={`sudar-zeton ${jePobjednik ? 'sudar-dobio' : ''} ${gotovo && !jePobjednik ? 'sudar-odbijen' : ''}`}
							initial={false}
							animate={uCentru ? { x: 0, y: 0, scale: 0.7, opacity: jePobjednik ? 0 : 1 } : { x, y, scale: 1, opacity: 1 }}
							transition={
								faza === 'juri'
									? { duration: 0.42, delay: (i % 7) * 0.025, ease: [0.5, 0, 0.75, 0] }
									: { type: 'spring', stiffness: 260, damping: 14, delay: (i % 5) * 0.02 }
							}
 />
					)
				})}
			</div>
			<h3>Nema duplih rezervacija</h3>
			<p>Kad dvoje klikne na isti termin u istoj sekundi, dobije ga samo jedno, a drugo odmah vidi da je zauzet.</p>
			<button type="button" className="dugme-tekst" onClick={posalji} disabled={faza === 'juri'}>
				{faza === 'miruje' ? 'Pusti dvadeset klikova odjednom' : 'Pusti ponovo'}
			</button>
			<p className="skriveno" aria-live="polite">
				{gotovo ? 'Jedan klik je dobio termin, ostalih devetnaest je vidjelo da je zauzet.' : ''}
			</p>
		</article>
	)
}
