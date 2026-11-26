import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { registracija } from '../api/auth.js'
import { Polje } from '../forma/Polje.jsx'
import { NAPOMENA_LOZINKE, problemLozinke } from '../forma/lozinka.js'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { NoviLinkZaPotvrdu } from './NoviLinkZaPotvrdu.jsx'
import { Uvod } from './Uvod.jsx'

const PORUKE = {
	serverNeOdgovara: 'Nalog nije napravljen, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Nalog nije napravljen zbog greške na serveru. Pokušaj ponovo za minutu.',
}

export function Registracija() {
	// Adresa s koje je korisnik došao (npr. link pozivnice) ide i na drugu formu, da se ne izgubi.
	const lokacija = useLocation()
	const [ime, setIme] = useState('')
	const [email, setEmail] = useState('')
	const [lozinka, setLozinka] = useState('')
	const [salje, setSalje] = useState(false)
	const [greska, setGreska] = useState(null)
	// Email na koji je otišao link. Sesija ostaje odjavljena, jer prijava radi tek poslije potvrde.
	const [poslano, setPoslano] = useState(null)
	const [slabaLozinka, setSlabaLozinka] = useState(null)
	const poPoljima = greskePoPoljima(greska)

	async function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		const problem = problemLozinke(lozinka, email)
		setSlabaLozinka(problem)
		if (problem) return
		setSalje(true)
		setGreska(null)
		try {
			await registracija(ime, email, lozinka)
			setPoslano(email)
		} catch (g) {
			setGreska(g)
		}
		setSalje(false)
	}

	// Isti 202 i za email koji već ima nalog. Tada stigne mail koji to kaže, pa tekst pokriva oba slučaja.
	if (poslano) {
		return (
			<section>
				<h1>Pogledaj inbox</h1>
				<p>
					Na {poslano} je poslan mail s linkom za potvrdu. Link važi 24 sata, a prijava radi tek kad potvrdiš email.
				</p>
				<p>Ako ovaj email već ima nalog, mail to kaže. Tada se prijavi postojećom lozinkom.</p>
				<NoviLinkZaPotvrdu poznatEmail={poslano} />
				<p>
					<Link to="/prijava" state={lokacija.state}>
						Na prijavu
					</Link>
				</p>
			</section>
		)
	}

	return (
		<section className="pocetna">
			<Uvod />
			<div className="pocetna-forma">
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
						greska={slabaLozinka ?? poPoljima.password}
						napomena={NAPOMENA_LOZINKE}
					/>
					<button className="dugme" type="submit" disabled={salje}>
						{salje ? 'Nalog se pravi' : 'Napravi nalog'}
					</button>
				</form>
				<p>
					Imaš nalog:{' '}
					<Link to="/prijava" state={lokacija.state}>Prijavi se</Link>
				</p>
			</div>
		</section>
	)
}
