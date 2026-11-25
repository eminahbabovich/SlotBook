import { MotionConfig, motion } from 'motion/react'
import { useEffect } from 'react'
import { Link } from 'react-router'
import { Znak } from '../tema/Znak.jsx'
import { HeroDemo } from './HeroDemo.jsx'
import { Ikona } from './Ikona.jsx'
import { NocPomjeranja } from './NocPomjeranja.jsx'
import { Sudar } from './Sudar.jsx'
import { TudjaVrata } from './TudjaVrata.jsx'
import { UlazUDemo } from './UlazUDemo.jsx'
import './landing.css'

const ZA_KOGA = ['Čitaonice', 'Teretane', 'Konsultacije kod profesora', 'Sale za sastanke', 'Sportski tereni', 'Frizerski saloni']

const DANAS = [
	'Viber grupa od 200 ljudi i pitanje „ima li slobodan sto u 10?“',
	'Papir na vratima sale koji niko ne precrta na vrijeme',
	'Excel tabela koju vodi jedna osoba, kad stigne',
	'„Jesi li slobodan u 5?“ „Nisam, može u 6?“',
]

const SLOTBOOK = [
	'Slobodni termini su na telefonu i svako ih vidi sam',
	'Zauzet termin se odmah svima vidi kao zauzet',
	'Ko vodi salu, ima raspored za danas bez prepisivanja',
	'Klikneš slobodan termin i dobiješ kartu s vremenom',
]

const KORACI = [
	['Napraviš organizaciju', 'Dodaš ono što se rezerviše: sto, salu, teren ili termin konsultacija, i koliko termin traje.'],
	['Upišeš radno vrijeme', 'Za svaki dan posebno, a praznike i dane kad ne radiš dodaš kao izuzetke.'],
	['Pozoveš ljude', 'Pozivnica stiže mailom, a članovi od tada sami rezervišu slobodne termine.'],
]

const MOGUCNOSTI = [
	['uzivo', 'Kalendar uživo', 'Kad neko uzme termin, drugima se prikaže kao zauzet bez osvježavanja stranice.'],
	['cekanje', 'Lista čekanja', 'Upišeš se na zauzet termin, a kad se oslobodi, ponudi se prvom na listi.'],
	['qr', 'Dolazak s QR kodom', 'Na ulazu pokažeš kod s telefona, a dolazak se upiše jednim klikom.'],
	['statistika', 'Statistika popunjenosti', 'Vidiš koliko je prostor zauzet, kad je najveća gužva i ko ne dođe.'],
	['clanovi', 'Članovi i uloge', 'Ti određuješ ko samo rezerviše, ko vodi termine, a ko upravlja svime.'],
	['kalendar', 'Termini u tvom kalendaru', 'Rezervacije se same pojave u Google kalendaru, na iPhoneu ili u Outlooku.'],
]

const PITANJA = [
	['Koliko košta?', 'Ništa. SlotBook je studentski projekat i ne naplaćuje se. Demo možeš probati bez registracije.'],
	[
		'Šta ako dvoje klikne na isti termin?',
		'Termin dobije samo jedno. Drugo odmah vidi da je zauzet i dobije prijedlog najbližeg slobodnog termina.',
	],
	['Moram li sam potvrđivati svaku rezervaciju?', 'Ne moraš. Za svaki resurs biraš da li se rezervacija potvrdi sama ili je potvrđuješ ti.'],
	['Može li se otkazati u zadnji čas?', 'Za svaki resurs upišeš rok za otkazivanje u satima. Poslije roka termin se ne može sam otkazati.'],
	['Šta kad neko otkaže?', 'Termin se oslobodi. Ako je neko na listi čekanja, termin se prvo ponudi njemu.'],
	['Mogu li dobiti svoje podatke?', 'Možeš. U nalogu je izvoz svih podataka u jedan fajl, a nalog se može i obrisati.'],
]

// Ulaz hera: tekst i prozor aplikacije se pojave jednom, kratko. Uz smanjen pokret (MotionConfig) samo stoje.
const ULAZ = { initial: { opacity: 0, y: 14 }, animate: { opacity: 1, y: 0 } }

// Prvi ekran za odjavljene. Učitava se posebno (lazy u App.jsx), pa motion i lenis ne idu u
// bundle aplikacije za prijavljene dok ih ona sama ne zatreba.
export default function Landing() {
	useGlatkiSkrol()

	return (
		<MotionConfig reducedMotion="user">
			<div className="landing">
				<section className="hero" aria-labelledby="hero-naslov">
					<motion.div className="hero-tekst" {...ULAZ} transition={{ duration: 0.5, ease: [0.2, 0.7, 0.2, 1] }}>
						<h1 id="hero-naslov" className="hero-naslov">
							Termini za čitaonice, teretane i konsultacije
						</h1>
						<p className="hero-recenica">Ljudi sami vide slobodne termine i rezervišu ih s telefona, a ti više ne odgovaraš na poruke.</p>
						<div className="akcije">
							<a className="dugme dugme-mjed" href="#demo">
								Probaj demo
							</a>
							<Link className="dugme dugme-sporedno" to="/registracija">
								Napravi organizaciju
							</Link>
						</div>
						<p className="hero-napomena">Demo je bez registracije, s izmišljenom čitaonicom i teretanom.</p>
					</motion.div>
					<motion.div className="prozor" {...ULAZ} transition={{ duration: 0.6, delay: 0.12, ease: [0.2, 0.7, 0.2, 1] }}>
						<div className="prozor-traka" aria-hidden="true">
							<Znak />
							<span>SlotBook</span>
						</div>
						<HeroDemo />
					</motion.div>
				</section>

				<section className="za-koga" aria-label="Za koga je">
					<p>Za mjesta gdje se dijele termini</p>
					<ul>
						{ZA_KOGA.map((mjesto) => (
							<li key={mjesto}>{mjesto}</li>
						))}
					</ul>
				</section>

				<section className="sekcija" aria-labelledby="prije-naslov">
					<h2 id="prije-naslov" className="sekcija-naslov">
						Umjesto Viber grupe i papira na vratima
					</h2>
					<div className="prije-poslije">
						<div className="prije kartica">
							<h3>Danas</h3>
							<ul>
								{DANAS.map((t) => (
									<li key={t}>{t}</li>
								))}
							</ul>
						</div>
						<div className="poslije kartica">
							<h3>Sa SlotBookom</h3>
							<ul>
								{SLOTBOOK.map((t) => (
									<li key={t}>{t}</li>
								))}
							</ul>
						</div>
					</div>
				</section>

				<section className="kako-radi" aria-labelledby="koraci-naslov">
					<div className="kako-radi-unutra">
						<h2 id="koraci-naslov" className="sekcija-naslov">
							Kako radi
						</h2>
						<ol className="koraci">
							{KORACI.map(([naslov, opis], i) => (
								<li key={naslov} className="kartica">
									<span className="korak-broj" aria-hidden="true">
										{i + 1}
									</span>
									<h3>{naslov}</h3>
									<p>{opis}</p>
								</li>
							))}
						</ol>
					</div>
				</section>

				<section className="sekcija" aria-labelledby="mogucnosti-naslov">
					<h2 id="mogucnosti-naslov" className="sekcija-naslov">
						Šta još radi
					</h2>
					<ul className="mogucnosti">
						{MOGUCNOSTI.map(([ikona, naslov, opis]) => (
							<li key={naslov} className="kartica">
								<Ikona ime={ikona} />
								<h3>{naslov}</h3>
								<p>{opis}</p>
							</li>
						))}
					</ul>
				</section>

				<section className="sekcija" aria-labelledby="oslonac-naslov">
					<h2 id="oslonac-naslov" className="sekcija-naslov">
						Na ovo se možeš osloniti
					</h2>
					<div className="oslonac">
						<Sudar />
						<NocPomjeranja />
						<TudjaVrata />
					</div>
				</section>

				<section className="sekcija" aria-labelledby="pitanja-naslov">
					<h2 id="pitanja-naslov" className="sekcija-naslov">
						Česta pitanja
					</h2>
					<div className="pitanja">
						{PITANJA.map(([pitanje, odgovor]) => (
							<details key={pitanje}>
								<summary>
									{pitanje}
									<span className="pitanje-znak" aria-hidden="true" />
								</summary>
								<p>{odgovor}</p>
							</details>
						))}
					</div>
				</section>

				<UlazUDemo />

				<section className="poziv" aria-labelledby="poziv-naslov">
					<h2 id="poziv-naslov" className="sekcija-naslov">
						Napravi organizaciju za svoj prostor
					</h2>
					<p>Treba ti samo email. Prvi resurs i radno vrijeme upisuješ odmah poslije.</p>
					<div className="akcije">
						<Link className="dugme dugme-mjed" to="/registracija">
							Napravi organizaciju
						</Link>
						<a className="dugme dugme-sporedno" href="#demo">
							Probaj demo
						</a>
					</div>
				</section>

				<footer className="napravljeno" aria-labelledby="napravljeno-naslov">
					<h2 id="napravljeno-naslov">Kako je napravljeno</h2>
					<p>SlotBook je studentski projekat. Odluke, testovi i ono što još nije urađeno su opisani u README-u.</p>
					<ul className="cipovi" aria-label="Tehnologije">
						<li>Java i Spring Boot</li>
						<li>PostgreSQL</li>
						<li>React</li>
					</ul>
					<div className="akcije">
						<a className="dugme dugme-sporedno" href="https://github.com/eminahbabovich/SlotBook">
							Pogledaj kod na GitHubu
						</a>
						<a href="https://github.com/eminahbabovich/SlotBook#readme">Pročitaj README</a>
					</div>
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
