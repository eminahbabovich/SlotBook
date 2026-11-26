import { LayoutGroup, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { Karta } from '../karta/Karta.jsx'
import { danUZoni, dodajDane, nazivDana, uTrenutakSPomakom } from '../vrijeme/vrijeme.js'

const ZONA = 'Europe/Sarajevo'
// Isti raspored kao u demo podacima za sutra: sto je zauzet u 9, 12, 13 i 17
const SATI = ['08', '09', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19']
const ZAUZETO = new Set(['09', '12', '13', '17'])
const OPRUGA = { type: 'spring', stiffness: 420, damping: 34 }
// Dok posjetilac ništa ne klikne, poslije 2,5 s se sama odštampa karta za 10:00, da prozor ne stoji
// s praznim okvirom. Uz smanjen pokret karta se samo pojavi (karta.css i MotionConfig).
const PRIMJER = '10'
const PRIMJER_POSLIJE_MS = 2500

// Mini kalendar u heru. Ništa ne šalje na server: samo pokazuje šta se dešava kad uzmeš termin.
// Uzet žeton preleti s ploče na kartu (isti layoutId na oba mjesta), a karta se odštampa.
export function HeroDemo() {
	const [sutra] = useState(() => dodajDane(danUZoni(new Date(), ZONA), 1))
	const [uzet, setUzet] = useState(null)
	// Čitač ekrana najavi kartu samo kad ju je posjetilac sam uzeo, ne i primjer
	const [rukom, setRukom] = useState(false)

	useEffect(() => {
		const sat = setTimeout(() => setUzet((trenutni) => trenutni ?? PRIMJER), PRIMJER_POSLIJE_MS)
		return () => clearTimeout(sat)
	}, [])

	function uzmi(sat) {
		setRukom(true)
		setUzet(sat)
	}

	const pocetak = uzet && uTrenutakSPomakom(sutra, `${uzet}:00`, ZONA)
	const kraj = uzet && uTrenutakSPomakom(sutra, `${String(Number(uzet) + 1).padStart(2, '0')}:00`, ZONA)

	return (
		<LayoutGroup>
			<div className="hero-demo">
				<div className="ploca hero-ploca">
					<div className="hero-ploca-zaglavlje">
						<p className="hero-mjesto">Čitaonica PMF</p>
						<p className="hero-resurs">Sto 4, tihi dio</p>
						<p className="hero-dan">{nazivDana(sutra)}</p>
					</div>
					<ul className="zetoni" aria-label="Termini za sutra">
						{SATI.map((sat) => {
							const zauzet = ZAUZETO.has(sat)
							const moj = uzet === sat
							return (
								<li key={sat} className="zeton-mjesto">
									{zauzet ? (
										<span className="zeton zeton-zauzet">
											<time>{sat}:00</time>
											<span className="zeton-oznaka">zauzeto</span>
										</span>
									) : moj ? (
										<span className="zeton zeton-prazan" aria-hidden="true" />
									) : (
										<motion.button
											type="button"
											layoutId={`zeton-${sat}`}
											transition={OPRUGA}
											className="zeton zeton-slobodan"
											whileTap={{ scale: 0.94, y: 2 }}
											onClick={() => uzmi(sat)}
										>
											<time>{sat}:00</time>
											<span className="zeton-oznaka">slobodno</span>
										</motion.button>
									)}
								</li>
							)
						})}
					</ul>
				</div>

				<div className="hero-karta" aria-live={rukom ? 'polite' : 'off'}>
					{uzet ? (
						<Karta
							key={uzet}
							pocetak={pocetak}
							kraj={kraj}
							zona={ZONA}
							resurs="Sto 4, tihi dio"
							mjesto="Čitaonica PMF"
							nagib
							className="karta-odstampana"
							kupon={
								<motion.span layoutId={`zeton-${uzet}`} transition={OPRUGA} className="zeton zeton-moj zeton-u-karti">
									<time>{uzet}:00</time>
									<span className="zeton-oznaka">tvoj</span>
								</motion.span>
							}
						>
							<button type="button" className="dugme-tekst hero-vrati" onClick={() => uzmi(null)}>
								Vrati termin na ploču
							</button>
						</Karta>
					) : (
						<p className="hero-karta-prazno">Uzmi slobodan termin s ploče. Ovdje se odštampa karta, a ništa se ne šalje na server.</p>
					)}
				</div>
			</div>
		</LayoutGroup>
	)
}
