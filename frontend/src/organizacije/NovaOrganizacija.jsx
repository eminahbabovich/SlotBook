import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { novaOrganizacija } from '../api/organizacije.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { predloziAdresu } from './adresa.js'
import { KLJUC_MOJE_ORGANIZACIJE } from './upiti.js'

const PORUKE = {
	serverNeOdgovara: 'Organizacija nije napravljena, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacija nije napravljena zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Zone gradova, ne fiksni pomaci: backend odbija +01:00 i UTC, jer ne prate ljetno vrijeme.
const ZONE = Intl.supportedValuesOf('timeZone').filter((zona) => zona.includes('/'))

export function NovaOrganizacija() {
	const queryClient = useQueryClient()
	const navigate = useNavigate()
	const [naziv, setNaziv] = useState('')
	const [adresa, setAdresa] = useState('')
	const [adresaRucno, setAdresaRucno] = useState(false)
	const [zona, setZona] = useState('Europe/Sarajevo')

	const pravljenje = useMutation({
		mutationFn: novaOrganizacija,
		// Nova organizacija mora ući u listu, pa se lista učita ponovo prije prelaska na nju.
		onSuccess: async (organizacija) => {
			await queryClient.invalidateQueries({ queryKey: KLJUC_MOJE_ORGANIZACIJE })
			navigate(`/o/${organizacija.slug}`)
		},
	})
	const poPoljima = greskePoPoljima(pravljenje.error)

	// Adresa prati naziv dok je korisnik sam ne promijeni.
	function promijeniNaziv(novi) {
		setNaziv(novi)
		if (!adresaRucno) setAdresa(predloziAdresu(novi))
	}

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		pravljenje.mutate({ naziv, slug: adresa, zona })
	}

	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<h2>Nova organizacija</h2>
			{pravljenje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(pravljenje.error, PORUKE)}
				</p>
			)}
			<Polje oznaka="Naziv" ime="name" vrijednost={naziv} promjena={promijeniNaziv} greska={poPoljima.name} />
			<Polje
				oznaka="Adresa"
				ime="slug"
				vrijednost={adresa}
				promjena={(nova) => {
					setAdresaRucno(true)
					setAdresa(nova)
				}}
				greska={poPoljima.slug}
				napomena={`Dio linka: /o/${adresa || 'citaonica-pmf'}. Mala slova bez kvačica, brojevi i crtice.`}
			/>
			<div className="polje">
				<label htmlFor="nova-organizacija-zona">Vremenska zona</label>
				<select id="nova-organizacija-zona" name="timezone" value={zona} onChange={(d) => setZona(d.target.value)}>
					{ZONE.map((z) => (
						<option key={z}>{z}</option>
					))}
				</select>
				{poPoljima.timezone && <p className="greska-polja">{poPoljima.timezone}</p>}
			</div>
			<button className="dugme" type="submit" disabled={pravljenje.isPending}>
				{pravljenje.isPending ? 'Organizacija se pravi' : 'Napravi organizaciju'}
			</button>
		</form>
	)
}
