import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { noviLinkZaPotvrdu } from '../api/auth.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Link nije poslan, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Link nije poslan zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Odmah poslije registracije email je poznat, pa je tu samo dugme. Inače (link iz maila ne
// važi, prijava ne prolazi) korisnik upiše email.
// Backend uvijek vrati 204, i za nepoznat ili potvrđen email i preko granice od tri maila na
// sat, pa poruka ne smije tvrditi da je mail sigurno poslan.
export function NoviLinkZaPotvrdu({ poznatEmail }) {
	const [email, setEmail] = useState('')
	const slanje = useMutation({ mutationFn: noviLinkZaPotvrdu })
	const poPoljima = greskePoPoljima(slanje.error)

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		slanje.mutate(poznatEmail ?? email)
	}

	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<p role="status">
				{slanje.isSuccess &&
					`Ako nalog s emailom ${slanje.variables} čeka potvrdu, na njega ide novi link. Na isti email idu najviše tri maila na sat, pa ako ne stigne, pogledaj i spam.`}
			</p>
			{slanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(slanje.error, PORUKE)}
				</p>
			)}
			{!poznatEmail && (
				<Polje oznaka="Email" ime="email" tip="email" autoComplete="email" vrijednost={email} promjena={setEmail} greska={poPoljima.email} />
			)}
			<button className="dugme" type="submit" disabled={slanje.isPending}>
				{slanje.isPending ? 'Link se šalje' : 'Pošalji novi link'}
			</button>
		</form>
	)
}
