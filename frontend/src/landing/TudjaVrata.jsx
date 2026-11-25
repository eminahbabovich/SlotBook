import { motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

const BROJ = 33
// Nekoliko stvarnih ruta iz testa, da se vidi šta se pokušava, a ne samo tačkice
const PRIMJERI = [
	'GET /organizations/{id}/members',
	'POST /organizations/{id}/invites',
	'PUT /resources/{id}/availability',
	'DELETE /organizations/{id}/resources/{id}',
	'GET /organizations/{id}/stats',
	'POST /resources/{id}/bookings',
	'POST /bookings/{id}/confirm',
	'GET /organizations/{id}/audit-log',
]

// Admin teretane pokuša 33 zahtjeva u čitaonicu. Svaki udari u zid između dvije organizacije i
// vrati se sa 403. Brojač promijenjenih redova ostaje na nuli, kao u testu.
export function TudjaVrata() {
	const [poslano, setPoslano] = useState(0)
	const [ide, setIde] = useState(false)
	const tajmer = useRef(0)

	useEffect(() => () => clearInterval(tajmer.current), [])

	function pokusaj() {
		clearInterval(tajmer.current)
		setPoslano(0)
		setIde(true)
		let n = 0
		tajmer.current = setInterval(() => {
			n += 1
			setPoslano(n)
			if (n >= BROJ) {
				clearInterval(tajmer.current)
				setIde(false)
			}
		}, 70)
	}

	return (
		<section className="sekcija" aria-labelledby="vrata-naslov">
			<div className="sekcija-tekst">
				<h2 id="vrata-naslov" className="sekcija-naslov">
					Tuđa vrata
				</h2>
				<p>
					Čitaonica PMF i Teretana Ilidža su na istom serveru i u istoj bazi. Admin teretane je admin, ali samo u teretani. Kad
					pokuša išta u čitaonici, pročitati članove, promijeniti radno vrijeme, obrisati resurs, dobije 403.
				</p>
				<p className="dokaz">
					Dokaz: <code>OrganizationIsolationApiTest</code> pošalje svih 33 zahtjeva tokenom admina druge organizacije. Svaki
					dobije 403, a broj članova, resursa, pravila i rezervacija u bazi je poslije isti kao prije.
				</p>
				<button type="button" className="dugme dugme-mjed" onClick={pokusaj} disabled={ide}>
					{poslano === 0 ? 'Pokušaj kao admin teretane' : 'Pokušaj ponovo'}
				</button>
			</div>

			<div className="vrata" aria-hidden="true">
				<div className="vrata-org vrata-teretana">
					<p className="vrata-ime">Teretana Ilidža</p>
					<p className="vrata-uloga">admin: Bojan</p>
				</div>
				<div className="vrata-zid">
					{Array.from({ length: Math.min(poslano, BROJ) }, (_, i) => (
						<motion.span
							key={i}
							className="vrata-zahtjev"
							initial={{ x: '-120%', opacity: 0 }}
							animate={{ x: ['-120%', '0%', '-60%'], opacity: [0, 1, 0.85] }}
							transition={{ duration: 0.5, times: [0, 0.6, 1], ease: 'easeOut' }}
						>
							{PRIMJERI[i % PRIMJERI.length]} <b>403</b>
						</motion.span>
					)).slice(-6)}
				</div>
				<div className="vrata-org vrata-citaonica">
					<p className="vrata-ime">Čitaonica PMF</p>
					<p className="vrata-brojac">
						<span className="vrata-broj">{poslano}</span> od {BROJ} odbijeno
					</p>
					<p className="vrata-brojac">
						<span className="vrata-broj">0</span> promijenjenih redova
					</p>
				</div>
			</div>
			<p className="skriveno" aria-live="polite">
				{poslano === BROJ ? 'Svih 33 zahtjeva je dobilo 403, u čitaonici se ništa nije promijenilo.' : ''}
			</p>
		</section>
	)
}
