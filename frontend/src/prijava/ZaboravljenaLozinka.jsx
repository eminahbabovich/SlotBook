import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { zatraziNovuLozinku } from '../api/auth.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Link nije poslan, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Link nije poslan zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Server uvijek vrati 204, pa tekst ne smije tvrditi da nalog postoji
export function ZaboravljenaLozinka() {
	const [email, setEmail] = useState('')
	const zahtjev = useMutation({ mutationFn: () => zatraziNovuLozinku(email) })
	const poPoljima = greskePoPoljima(zahtjev.error)

	if (zahtjev.isSuccess) {
		return (
			<section>
				<h1>Pogledaj inbox</h1>
				<p role="status">
					Ako {email} ima nalog, na njega je poslan link za novu lozinku. Link važi sat vremena. Ako mail ne stigne za
					par minuta, pogledaj i spam.
				</p>
				<Link to="/prijava">Na prijavu</Link>
			</section>
		)
	}

	return (
		<section>
			<h1>Nova lozinka</h1>
			<p>Upiši email s kojim se prijavljuješ. Poslaćemo link na kojem postavljaš novu lozinku.</p>
			<form
				className="forma"
				noValidate
				onSubmit={(d) => {
					d.preventDefault()
					zahtjev.mutate()
				}}
			>
				{zahtjev.isError && !poPoljima.email && (
					<p role="alert" className="greska-forme">
						{porukaGreske(zahtjev.error, PORUKE)}
					</p>
				)}
				<Polje oznaka="Email" ime="email" tip="email" autoComplete="email" vrijednost={email} promjena={setEmail} greska={poPoljima.email} />
				<button className="dugme" type="submit" disabled={zahtjev.isPending}>
					{zahtjev.isPending ? 'Šalje se' : 'Pošalji link'}
				</button>
			</form>
			<p>
				<Link to="/prijava">Nazad na prijavu</Link>
			</p>
		</section>
	)
}
