import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { promijeniNedolaske } from '../api/organizacije.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { KLJUC_MOJE_ORGANIZACIJE, useOrganizacija } from '../organizacije/upiti.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { Mrvice } from '../tema/Mrvice.jsx'
import { EkranNaVratima } from './EkranNaVratima.jsx'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Organizacija se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacija se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_CUVANJA = {
	serverNeOdgovara: 'Pravilo nije sačuvano, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Pravilo nije sačuvano zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stranica /o/{slug}/postavke, samo za admina. Backend ionako čuva PUT (@PreAuthorize ... 'ADMIN').
export function Postavke() {
	const { slug } = useParams()
	const { organizacija, isPending, isError, error, refetch } = useOrganizacija(slug)

	if (isPending) {
		return <Ucitava tekst="Učitava se organizacija" />
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	}
	if (organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}
	if (organizacija.uloga !== 'ADMIN') {
		return (
			<section>
				<h1>Postavke nisu dostupne</h1>
				<p>Pravila organizacije mijenja admin.</p>
				<Link to={`/o/${slug}`}>{organizacija.naziv}</Link>
			</section>
		)
	}

	return (
		<section>
			<Mrvice stavke={[{ naziv: 'Organizacije', to: '/' }, { naziv: organizacija.naziv, to: `/o/${slug}` }, { naziv: 'Postavke' }]} />
			<h1>Postavke</h1>
			<PraviloNedolazaka organizacija={organizacija} />
			<EkranNaVratima organizacija={organizacija} />
		</section>
	)
}

function PraviloNedolazaka({ organizacija }) {
	const queryClient = useQueryClient()
	const pocetno = organizacija.nedolasci
	const [p, setP] = useState(() => ({
		nedolazaka: String(pocetno?.nedolazaka ?? ''),
		periodDana: String(pocetno?.periodDana ?? ''),
		blokadaDana: String(pocetno?.blokadaDana ?? ''),
	}))
	const promijeni = (polje) => (vrijednost) => setP((staro) => ({ ...staro, [polje]: vrijednost }))
	const cuvanje = useMutation({
		mutationFn: (pravilo) => promijeniNedolaske(organizacija.id, pravilo),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: KLJUC_MOJE_ORGANIZACIJE }),
	})
	const poPoljima = greskePoPoljima(cuvanje.error)
	const ukida = p.nedolazaka === '' && p.periodDana === '' && p.blokadaDana === ''

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		cuvanje.mutate(p)
	}

	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<h2>Nedolasci</h2>
			<p className="napomena">
				Ko se ne pojavi toliko puta u zadanom periodu, ne može rezervisati u ovoj organizaciji dok blokada traje. Blokada se računa od zadnjeg nedolaska i
				prestaje sama. Admin i ko vodi termine nisu ograničeni. Ostavi sva tri polja prazna i pravila nema.
			</p>
			<p role="status">{cuvanje.isSuccess && (cuvanje.data === null ? 'Pravilo je ukinuto.' : 'Pravilo je sačuvano.')}</p>
			{cuvanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(cuvanje.error, PORUKE_CUVANJA)}
				</p>
			)}
			<Polje oznaka="Broj nedolazaka" ime="limit" tip="number" vrijednost={p.nedolazaka} promjena={promijeni('nedolazaka')} greska={poPoljima.limit} napomena="Npr. 3." />
			<Polje oznaka="U periodu od (dana)" ime="windowDays" tip="number" vrijednost={p.periodDana} promjena={promijeni('periodDana')} greska={poPoljima.windowDays} napomena="Npr. 30: broje se nedolasci iz zadnjih mjesec dana." />
			<Polje oznaka="Blokada traje (dana)" ime="blockDays" tip="number" vrijednost={p.blokadaDana} promjena={promijeni('blokadaDana')} greska={poPoljima.blockDays} napomena="Npr. 7." />
			<button className="dugme" type="submit" disabled={cuvanje.isPending}>
				{cuvanje.isPending ? 'Čuva se' : ukida ? 'Ukini pravilo' : 'Sačuvaj pravilo'}
			</button>
		</form>
	)
}
