import { useState } from 'react'
import { Link } from 'react-router'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { useSesija } from '../sesija/sesija.js'

const PORUKE = {
	serverNeOdgovara: 'Prijava nije poslana, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Prijava nije uspjela zbog greške na serveru. Pokušaj ponovo za minutu.',
}

export function Prijava() {
	const { prijavi } = useSesija()
	const [email, setEmail] = useState('')
	const [lozinka, setLozinka] = useState('')
	const [salje, setSalje] = useState(false)
	const [greska, setGreska] = useState(null)
	const poPoljima = greskePoPoljima(greska)

	// Poslije uspjeha ekran nestane (sesija pređe u 'prijavljen'), pa se salje ne vraća.
	async function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		setSalje(true)
		setGreska(null)
		try {
			await prijavi(email, lozinka)
		} catch (g) {
			setGreska(g)
			setSalje(false)
		}
	}

	// noValidate: provjeru radi backend, pa su poruke na bosanskom i na jednom mjestu,
	// a ne iz browsera, na jeziku sistema.
	return (
		<section>
			<h1>Prijava</h1>
			<form className="forma" onSubmit={posalji} noValidate>
				{greska && (
					<p role="alert" className="greska-forme">
						{porukaGreske(greska, PORUKE)}
					</p>
				)}
				<Polje oznaka="Email" ime="email" tip="email" autoComplete="email" vrijednost={email} promjena={setEmail} greska={poPoljima.email} />
				<Polje
					oznaka="Lozinka"
					ime="password"
					tip="password"
					autoComplete="current-password"
					vrijednost={lozinka}
					promjena={setLozinka}
					greska={poPoljima.password}
				/>
				<button className="dugme" type="submit" disabled={salje}>
					{salje ? 'Prijava u toku' : 'Prijavi se'}
				</button>
			</form>
			<p>
				Prvi put ovdje:{' '}
				<Link to="/registracija">Napravi nalog</Link>
			</p>
		</section>
	)
}
