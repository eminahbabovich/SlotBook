import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { clanoviOrganizacije } from '../api/clanovi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { ULOGE, useOrganizacija } from '../organizacije/upiti.js'
import { useSesija } from '../sesija/sesija.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { Pozivnica } from './Pozivnica.jsx'
import '../organizacije/organizacije.css'
import { emajlResursa } from '../resursi/emajl.js'

const PORUKE = {
	serverNeOdgovara: 'Organizacija se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacija se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_CLANOVI = {
	serverNeOdgovara: 'Članovi se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Članovi se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stranica /o/{slug}/clanovi. Uloga se provjerava ovdje samo da ne-admin ne gleda grešku 403,
// a članove i pozivnice i dalje čuva backend (@PreAuthorize ... 'ADMIN').
export function Clanovi() {
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
				<h1>Članovi nisu dostupni</h1>
				<p>Listu članova i pozivnice vodi admin organizacije.</p>
				<Link to={`/o/${slug}`}>{organizacija.naziv}</Link>
			</section>
		)
	}

	return (
		<section>
			<Link to={`/o/${slug}`}>{organizacija.naziv}</Link>
			<h1>Članovi</h1>
			<Pozivnica organizacija={organizacija} />
			<ListaClanova organizacija={organizacija} />
		</section>
	)
}

function ListaClanova({ organizacija }) {
	const [stranica, setStranica] = useState(0)
	const { korisnik } = useSesija()
	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['clanovi', organizacija.id, stranica],
		queryFn: () => clanoviOrganizacije(organizacija.id, stranica),
	})

	if (isPending) {
		return <Ucitava tekst="Učitavaju se članovi" />
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE_CLANOVI)} ponovo={() => refetch()} />
	}

	return (
		<>
			<h2>Ko je u organizaciji</h2>
			{data.ukupnoStranica === 1 && data.clanovi.length === 1 && (
				<p className="napomena-blok">Za sada si jedini član. Kad neko prihvati pozivnicu, pojavit će se ovdje.</p>
			)}
			<ul className="tabla tabla-zbijena" aria-label="Članovi">
				{data.clanovi.map((c) => (
					<li key={c.id} className="tabla-red" style={{ '--emajl': emajlResursa(c.id) }}>
						<span>
							<span className="tabla-naziv">{c.ime}</span>
							{c.id === korisnik.id && ' (ti)'}{' '}
							<span className="tabla-uloga">{c.email}</span>
						</span>
						<span className="tabla-uloga">{ULOGE[c.uloga] ?? c.uloga}</span>
					</li>
				))}
			</ul>
			{data.ukupnoStranica > 1 && (
				<div className="stranice">
					<button className="dugme-tekst" type="button" onClick={() => setStranica(stranica - 1)} disabled={stranica === 0}>
						Prethodna stranica
					</button>
					<button
						className="dugme-tekst"
						type="button"
						onClick={() => setStranica(stranica + 1)}
						disabled={stranica >= data.ukupnoStranica - 1}
					>
						Sljedeća stranica
					</button>
				</div>
			)}
		</>
	)
}
