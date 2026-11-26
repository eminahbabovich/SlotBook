import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { posaljiPozivnicu } from '../api/clanovi.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { ULOGE } from '../organizacije/upiti.js'
import { opisTrenutka } from '../vrijeme/opis.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Pozivnica nije poslana, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Pozivnica nije poslana zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Od najmanje do najviše prava. Prva je podrazumijevana, da pogrešan klik ne napravi admina.
const IZBOR_ULOGE = ['CLIENT', 'PROVIDER', 'ADMIN']

export function Pozivnica({ organizacija }) {
	const [email, setEmail] = useState('')
	const [uloga, setUloga] = useState(IZBOR_ULOGE[0])

	const queryClient = useQueryClient()
	// Lista članova se ne osvježava: pozvana osoba postaje član tek kad prihvati pozivnicu. Osvježi se
	// lista otvorenih pozivnica, jer nova ide na njen vrh.
	const slanje = useMutation({
		mutationFn: () => posaljiPozivnicu(organizacija.id, { email, uloga }),
		onSuccess: () => {
			setEmail('')
			queryClient.invalidateQueries({ queryKey: ['otvorenePozivnice', organizacija.id] })
		},
	})
	const poPoljima = greskePoPoljima(slanje.error)

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		slanje.mutate()
	}

	const p = slanje.data
	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<h2>Pozovi člana</h2>
			<p role="status">
				{slanje.isSuccess &&
					`Pozivnica je poslana na ${p.email}, uloga: ${ULOGE[p.uloga]}. Rok: ${opisTrenutka(p.vaziDo, organizacija.zona)}.`}
			</p>
			{slanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(slanje.error, PORUKE)}
				</p>
			)}
			<Polje
				oznaka="Email"
				ime="email"
				tip="email"
				vrijednost={email}
				promjena={setEmail}
				greska={poPoljima.email}
				napomena="Link stiže emailom i važi 7 dana. Osoba se prijavi ili napravi nalog s tim emailom."
				autoComplete="off"
			/>
			<div className="polje">
				<label htmlFor="pozivnica-uloga">Uloga</label>
				<select id="pozivnica-uloga" name="role" value={uloga} onChange={(d) => setUloga(d.target.value)}>
					{IZBOR_ULOGE.map((u) => (
						<option key={u} value={u}>
							{ULOGE[u]}
						</option>
					))}
				</select>
				<p className="napomena">Ko vodi termine, vidi raspored svojih resursa s imenima. Admin vodi i članove i sve resurse.</p>
				{poPoljima.role && <p className="greska-polja">{poPoljima.role}</p>}
			</div>
			<button className="dugme" type="submit" disabled={slanje.isPending}>
				{slanje.isPending ? 'Pozivnica se šalje' : 'Pošalji pozivnicu'}
			</button>
		</form>
	)
}
