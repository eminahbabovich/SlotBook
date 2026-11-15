import { useState } from 'react'
import { Link } from 'react-router'
import { porukaGreske } from './forma/porukaGreske.js'
import { useSesija } from './sesija/sesija.js'

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
					SlotBook
				</Link>
				{status === 'prijavljen' && (
					<div className="zaglavlje-nalog">
						<span>{korisnik.ime}</span>
						<button className="dugme-tekst" type="button" onClick={odjaviSe} disabled={salje}>
							{salje ? 'Odjava u toku' : 'Odjavi se'}
						</button>
					</div>
				)}
			</div>
			{greska && (
				<p role="alert" className="greska-forme">
					{porukaGreske(greska, PORUKE)}
				</p>
			)}
		</header>
	)
}
