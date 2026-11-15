import { useState } from 'react'
import { porukaGreske } from '../forma/porukaGreske.js'
import { useSesija } from '../sesija/sesija.js'

const PORUKE = {
	serverNeOdgovara: 'Odjava nije uspjela, server ne odgovara. Sesija je i dalje otvorena, pokušaj ponovo za minutu.',
	opsta: 'Odjava nije uspjela zbog greške na serveru. Sesija je i dalje otvorena, pokušaj ponovo za minutu.',
}

// Privremeni ekran poslije prijave. U koraku 5 ga zamjenjuje izbor organizacije.
export function Nalog() {
	const { korisnik, odjavi } = useSesija()
	const [salje, setSalje] = useState(false)
	const [greska, setGreska] = useState(null)

	async function odjaviSe() {
		setSalje(true)
		setGreska(null)
		try {
			await odjavi()
		} catch (g) {
			setGreska(g)
			setSalje(false)
		}
	}

	return (
		<section className="forma">
			<h1>Tvoj nalog</h1>
			<div>
				<p className="ime-naloga">{korisnik.ime}</p>
				<p className="napomena">{korisnik.email}</p>
			</div>
			{!korisnik.emailPotvrden && <p>Email još nije potvrđen. Link za potvrdu je poslan na {korisnik.email} poslije registracije.</p>}
			{greska && (
				<p role="alert" className="greska-forme">
					{porukaGreske(greska, PORUKE)}
				</p>
			)}
			<button className="dugme" type="button" onClick={odjaviSe} disabled={salje}>
				{salje ? 'Odjava u toku' : 'Odjavi se'}
			</button>
		</section>
	)
}
