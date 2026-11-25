import { MotionConfig } from 'motion/react'
import { useEffect, useRef } from 'react'
import { HeroDemo } from './HeroDemo.jsx'
import { HeroNaslov } from './HeroNaslov.jsx'
import { NocPomjeranja } from './NocPomjeranja.jsx'
import { Sudar } from './Sudar.jsx'
import { TudjaVrata } from './TudjaVrata.jsx'
import { UlazUDemo } from './UlazUDemo.jsx'
import './landing.css'

// Prvi ekran za odjavljene. Učitava se posebno (lazy u App.jsx), pa motion i lenis ne idu u
// bundle aplikacije za prijavljene dok ih ona sama ne zatreba.
export default function Landing() {
	const hero = useRef(null)
	useGlatkiSkrol()

	return (
		<MotionConfig reducedMotion="user">
			<div className="landing">
				<section className="hero" ref={hero} aria-labelledby="hero-naslov">
					<div className="hero-tekst" id="hero-naslov">
						<HeroNaslov redovi={['Termin', 'je tvoj.']} povrsina={hero} />
						<p className="hero-recenica">
							Sto u čitaonici, termin konsultacija, sala u teretani. Rezervišeš za pola minute, a kad dvoje klikne na isti termin u
							istoj sekundi, dobije ga tačno jedno.
						</p>
						<a className="dugme dugme-mjed hero-akcija" href="#demo">
							Uđi u demo bez registracije
						</a>
					</div>
					<HeroDemo />
				</section>

				<div className="sekcije">
					<p className="sekcije-uvod">Tri stvari koje rezervacije obično rade pogrešno, i kako ih SlotBook dokazuje testom.</p>
					<Sudar />
					<NocPomjeranja />
					<TudjaVrata />
				</div>

				<UlazUDemo />

				<footer className="podnozje">
					<p>
						SlotBook je studentski projekat: Spring Boot, PostgreSQL i React. Kod, testovi i odluke su na{' '}
						<a href="https://github.com/eminahbabovich/SlotBook">GitHubu</a>.
					</p>
				</footer>
			</div>
		</MotionConfig>
	)
}

// Lenis samo na landingu i samo kad korisnik ne traži manje pokreta. Učitava se tek kad zatreba.
function useGlatkiSkrol() {
	useEffect(() => {
		// Bez ResizeObservera (stari browser, jsdom u testovima) Lenis ne radi, a običan skrol radi svuda
		if (typeof ResizeObserver === 'undefined' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined
		let lenis = null
		let okvir = 0
		let ugasen = false
		import('lenis').then(({ default: Lenis }) => {
			if (ugasen) return
			lenis = new Lenis({ anchors: true })
			const korak = (vrijeme) => {
				lenis.raf(vrijeme)
				okvir = requestAnimationFrame(korak)
			}
			okvir = requestAnimationFrame(korak)
		})
		return () => {
			ugasen = true
			cancelAnimationFrame(okvir)
			lenis?.destroy()
		}
	}, [])
}
