import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { useSesija } from '../sesija/sesija.js'

const PORUKE = {
	serverNeOdgovara: 'Nalog nije napravljen, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Nalog nije napravljen zbog greške na serveru. Pokušaj ponovo za minutu.',
}

export function Registracija() {
	// Adresa s koje je korisnik došao (npr. link pozivnice) ide i na drugu formu, da se ne izgubi.
	const lokacija = useLocation()
	const { registruj } = useSesija()
	const [ime, setIme] = useState('')
	const [email, setEmail] = useState('')
	const [lozinka, setLozinka] = useState('')
	const [salje, setSalje] = useState(false)
	const [greska, setGreska] = useState(null)
	const poPoljima = greskePoPoljima(greska)

	async function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		setSalje(true)
		setGreska(null)
		try {
			await registruj(ime, email, lozinka)
		} catch (g) {
			setGreska(g)
			setSalje(false)
		}
	}

	return (
		<section>
			<h1>Novi nalog</h1>
			<form className="forma" onSubmit={posalji} noValidate>
				{greska && (
					<p role="alert" className="greska-forme">
						{porukaGreske(greska, PORUKE)}
					</p>
				)}
				<Polje
					oznaka="Ime koje drugi vide"
					ime="displayName"
					autoComplete="name"
					vrijednost={ime}
					promjena={setIme}
					greska={poPoljima.displayName}
				/>
				<Polje oznaka="Email" ime="email" tip="email" autoComplete="email" vrijednost={email} promjena={setEmail} greska={poPoljima.email} />
				<Polje
					oznaka="Lozinka"
					ime="password"
					tip="password"
					autoComplete="new-password"
					vrijednost={lozinka}
					promjena={setLozinka}
					greska={poPoljima.password}
					napomena="Najmanje 8 znakova."
				/>
				<button className="dugme" type="submit" disabled={salje}>
					{salje ? 'Nalog se pravi' : 'Napravi nalog'}
				</button>
			</form>
			<p>
				Imaš nalog:{' '}
				<Link to="/prijava" state={lokacija.state}>Prijavi se</Link>
			</p>
		</section>
	)
}
