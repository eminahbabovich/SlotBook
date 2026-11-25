import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { clanoviOrganizacije } from '../api/clanovi.js'
import { promijeniVoditelja } from '../api/resursi.js'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { ULOGE } from '../organizacije/upiti.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'

const PORUKE_CLANOVI = {
	serverNeOdgovara: 'Članovi se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Članovi se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE = {
	serverNeOdgovara: 'Osoba nije promijenjena, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Osoba nije promijenjena zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Samo za admina (PUT .../provider). Nudi se prvih 100 članova, što je i najveća stranica
// na backendu. Organizacija s više admina i providera od toga za sada ne postoji.
export function Voditelj({ organizacija, resurs }) {
	const [izabran, setIzabran] = useState(resurs.vodi)
	const queryClient = useQueryClient()
	const clanovi = useQuery({
		queryKey: ['clanovi', organizacija.id, 'mogu voditi'],
		queryFn: () => clanoviOrganizacije(organizacija.id, 0, 100),
	})
	const promjena = useMutation({
		mutationFn: () => promijeniVoditelja(organizacija.id, resurs.id, izabran),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ['resursi', organizacija.id] }),
	})

	if (clanovi.isPending) {
		return <Ucitava tekst="Učitavaju se članovi" />
	}
	if (clanovi.isError) {
		return <NijeUcitano poruka={porukaGreske(clanovi.error, PORUKE_CLANOVI)} ponovo={() => clanovi.refetch()} />
	}

	// Klijenta backend odbije sa 400, pa se ni ne nudi
	const mogu = clanovi.data.clanovi.filter((c) => c.uloga !== 'CLIENT')
	const novi = promjena.isSuccess ? mogu.find((c) => c.id === promjena.data.vodi) : undefined
	const greska = greskePoPoljima(promjena.error).providerId

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		promjena.mutate()
	}

	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<h2>Ko vodi resurs</h2>
			<p role="status">{novi && `Resurs sada vodi ${novi.ime}.`}</p>
			{promjena.isError && !greska && (
				<p role="alert" className="greska-forme">
					{porukaGreske(promjena.error, PORUKE)}
				</p>
			)}
			<div className="polje">
				<label htmlFor="resurs-voditelj">Osoba koja vodi resurs</label>
				<select id="resurs-voditelj" name="providerId" value={izabran ?? ''} onChange={(d) => setIzabran(d.target.value)} aria-invalid={greska ? 'true' : 'false'}>
					{mogu.map((c) => (
						<option key={c.id} value={c.id}>
							{c.ime} ({ULOGE[c.uloga]})
						</option>
					))}
				</select>
				<p className="napomena">Ta osoba vidi raspored s imenima i potvrđuje rezervacije. Može biti admin ili neko ko vodi termine.</p>
				{greska && <p className="greska-polja">{greska}</p>}
			</div>
			<button className="dugme" type="submit" disabled={promjena.isPending}>
				{promjena.isPending ? 'Osoba se mijenja' : 'Promijeni osobu'}
			</button>
		</form>
	)
}
