import { useRef, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { porukaGreske } from './forma/porukaGreske.js'
import { useSesija } from './sesija/sesija.js'
import { PrekidacTeme } from './tema/PrekidacTeme.jsx'
import { Znak } from './tema/Znak.jsx'
import { PrelazLink } from './tema/PrelazLink.jsx'

const PORUKE = {
	serverNeOdgovara: 'Odjava nije uspjela, server ne odgovara. Sesija je i dalje otvorena, pokušaj ponovo za minutu.',
	opsta: 'Odjava nije uspjela zbog greške na serveru. Sesija je i dalje otvorena, pokušaj ponovo za minutu.',
}

// Demo računi iz profila demo (DemoSeed na backendu) su svi na ovoj domeni
const DEMO_DOMENA = '@slotbook.test'
const TELEFON = '(max-width: 640px)'

export function Zaglavlje() {
	const { status, korisnik, odjavi } = useSesija()
	const [salje, setSalje] = useState(false)
	const [greska, setGreska] = useState(null)
	const telefon = useMedij(TELEFON)
	const prijavljen = status === 'prijavljen'

	async function odjaviSe() {
		setSalje(true)
		setGreska(null)
		try {
			await odjavi()
		} catch (g) {
			setGreska(g)
		}
		setSalje(false)
	}

	const odjava = (
		<button className="dugme-tekst" type="button" onClick={odjaviSe} disabled={salje}>
			{salje ? 'Odjava u toku' : 'Odjavi se'}
		</button>
	)

	return (
		<header className="zaglavlje">
			<div className="zaglavlje-red">
				<PrelazLink to="/" className="znak">
					<Znak />
					<span className="znak-ime">SlotBook</span>
				</PrelazLink>
				<div className="zaglavlje-desno">
					{prijavljen && telefon && <MeniNaloga korisnik={korisnik} odjava={odjava} />}
					{prijavljen && !telefon && (
						<div className="zaglavlje-nalog">
							<PrelazLink to="/rezervacije">Moje rezervacije</PrelazLink>
							{/* Ime je ujedno link na nalog */}
							<PrelazLink to="/nalog">{korisnik.ime}</PrelazLink>
							{odjava}
						</div>
					)}
					{status === 'odjavljen' && (
						<div className="zaglavlje-nalog">
							<PrelazLink to="/prijava">Prijava</PrelazLink>
						</div>
					)}
					{!(prijavljen && telefon) && <PrekidacTeme />}
				</div>
			</div>
			{prijavljen && korisnik.email.endsWith(DEMO_DOMENA) && (
				<p className="demo-traka">
					Demo nalog: podaci se vraćaju svaki dan. <Link to="/registracija">Napravi svoju organizaciju</Link>
				</p>
			)}
			{greska && (
				<p role="alert" className="greska-forme">
					{porukaGreske(greska, PORUKE)}
				</p>
			)}
		</header>
	)
}

// Na telefonu je nalog jedan krug s inicijalima, a stavke su u meniju ispod njega. Meni je
// popover: browser ga sam zatvara na Escape i na klik van njega, i drži ga iznad svega.
function MeniNaloga({ korisnik, odjava }) {
	const meni = useRef(null)
	const [otvoren, setOtvoren] = useState(false)
	const zatvori = () => meni.current?.hidePopover?.()
	const inicijali = korisnik.ime
		.split(/\s+/)
		.slice(0, 2)
		.map((dio) => dio[0])
		.join('')
		.toUpperCase()

	return (
		<>
			<button
				type="button"
				className="avatar"
				aria-label={`Meni naloga: ${korisnik.ime}`}
				aria-expanded={otvoren}
				aria-controls="meni-naloga"
				onClick={() => meni.current?.togglePopover?.()}
			>
				{inicijali}
			</button>
			<div id="meni-naloga" className="meni-naloga" popover="auto" ref={meni} onToggle={(d) => setOtvoren(d.newState === 'open')}>
				<p className="meni-ime">{korisnik.ime}</p>
				<nav aria-label="Nalog" onClick={(d) => d.target.closest('a') && zatvori()}>
					<PrelazLink to="/rezervacije">Moje rezervacije</PrelazLink>
					<PrelazLink to="/nalog">Nalog</PrelazLink>
				</nav>
				<div className="meni-tema">
					<span>Tema</span>
					<PrekidacTeme />
				</div>
				{odjava}
			</div>
		</>
	)
}

// Prati media query, da se na telefonu pokaže meni umjesto tri linka
function useMedij(upit) {
	return useSyncExternalStore(
		(javi) => {
			const lista = window.matchMedia(upit)
			lista.addEventListener('change', javi)
			return () => lista.removeEventListener('change', javi)
		},
		() => window.matchMedia(upit).matches,
	)
}
