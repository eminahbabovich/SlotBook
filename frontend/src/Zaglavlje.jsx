import { useState } from 'react'
import { Link } from 'react-router'
import { porukaGreske } from './forma/porukaGreske.js'
import { useSesija } from './sesija/sesija.js'
import { PrekidacTeme } from './tema/PrekidacTeme.jsx'
import { Znak } from './tema/Znak.jsx'

const PORUKE = {
	serverNeOdgovara: 'Odjava nije uspjela, server ne odgovara. Sesija je i dalje otvorena, pokušaj ponovo za minutu.',
	opsta: 'Odjava nije uspjela zbog greške na serveru. Sesija je i dalje otvorena, pokušaj ponovo za minutu.',
}

export function Zaglavlje() {
	const { status, korisnik, odjavi } = useSesija()
	const [salje, setSalje] = useState(false)
	const [greska, setGreska] = useState(null)

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

	return (
		<header className="zaglavlje">
			<div className="zaglavlje-red">
				<Link to="/" className="znak">
					<Znak />
					<span className="znak-ime">SlotBook</span>
				</Link>
				<div className="zaglavlje-desno">
					{status === 'prijavljen' && (
						<div className="zaglavlje-nalog">
							<Link to="/rezervacije">Moje rezervacije</Link>
							{/* Ime je ujedno link na nalog: četiri stavke se na telefonu lome u dva reda */}
							<Link to="/nalog">{korisnik.ime}</Link>
							<button className="dugme-tekst" type="button" onClick={odjaviSe} disabled={salje}>
								{salje ? 'Odjava u toku' : 'Odjavi se'}
							</button>
						</div>
					)}
					{status === 'odjavljen' && (
						<div className="zaglavlje-nalog">
							<Link to="/prijava">Prijava</Link>
						</div>
					)}
					<PrekidacTeme />
				</div>
			</div>
			{greska && (
				<p role="alert" className="greska-forme">
					{porukaGreske(greska, PORUKE)}
				</p>
			)}
		</header>
	)
}
