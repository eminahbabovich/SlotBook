import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { clanoviOrganizacije, promijeniUlogu, ukloniClana } from '../api/clanovi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { ULOGE, useOrganizacija } from '../organizacije/upiti.js'
import { useSesija } from '../sesija/sesija.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { OtvorenePozivnice } from './OtvorenePozivnice.jsx'
import { Pozivnica } from './Pozivnica.jsx'
import { PozivniLink } from './PozivniLink.jsx'
import './clanovi.css'
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
			<PozivniLink organizacija={organizacija} />
			<Pozivnica organizacija={organizacija} />
			<OtvorenePozivnice organizacija={organizacija} />
			<ListaClanova organizacija={organizacija} />
		</section>
	)
}

const PORUKE_IZMJENE = {
	serverNeOdgovara: 'Promjena nije sačuvana, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Promjena nije sačuvana zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const IZBOR_ULOGE = ['CLIENT', 'PROVIDER', 'ADMIN']

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
				<p className="napomena-blok">Za sada si jedini član. Kad neko uđe preko linka ili pozivnice, pojavit će se ovdje.</p>
			)}
			<ul className="lista-clanova" aria-label="Članovi">
				{data.clanovi.map((c) => (
					<Clan key={c.id} clan={c} organizacija={organizacija} ja={c.id === korisnik.id} />
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

// Uloga se mijenja odmah po izboru, a uklanjanje traži potvrdu, jer otkazuje i buduće termine.
// Zadnjeg admina i člana koji vodi resurse čuva backend (409), a poruka kaže šta prvo uraditi.
function Clan({ clan, organizacija, ja }) {
	const queryClient = useQueryClient()
	const [pitaj, setPitaj] = useState(false)
	const osvjezi = () => queryClient.invalidateQueries({ queryKey: ['clanovi', organizacija.id] })
	const uloga = useMutation({
		mutationFn: (nova) => promijeniUlogu(organizacija.id, clan.id, nova),
		onSettled: osvjezi,
	})
	const uklanjanje = useMutation({
		mutationFn: () => ukloniClana(organizacija.id, clan.id),
		onSettled: () => {
			setPitaj(false)
			osvjezi()
		},
	})
	const greska = uloga.error ?? uklanjanje.error
	const izbor = `uloga-${clan.id}`

	return (
		<li style={{ '--emajl': emajlResursa(clan.id) }}>
			<span className="clan-ime">
				<strong>
					<span>{clan.ime}</span>
					{ja && ' (ti)'}
				</strong>
				<span className="clan-sporedno">{clan.email}</span>
			</span>
			<div className="clan-radnje">
				<label className="skriveno" htmlFor={izbor}>
					Uloga za {clan.ime}
				</label>
				<select
					id={izbor}
					value={uloga.isPending ? uloga.variables : clan.uloga}
					onChange={(d) => uloga.mutate(d.target.value)}
					disabled={uloga.isPending}
				>
					{IZBOR_ULOGE.map((u) => (
						<option key={u} value={u}>
							{ULOGE[u]}
						</option>
					))}
				</select>
				{pitaj ? (
					<div className="potvrda-dugmad" role="group" aria-label={`Uklanjanje: ${clan.ime}`}>
						<p className="potvrda-pitanje">Ukloniti {clan.ime}? Buduće rezervacije se otkazuju, uz mail.</p>
						<button className="dugme" type="button" onClick={() => uklanjanje.mutate()} disabled={uklanjanje.isPending}>
							{uklanjanje.isPending ? 'Uklanja se' : 'Ukloni'}
						</button>
						<button className="dugme-tekst" type="button" onClick={() => setPitaj(false)}>
							Ne
						</button>
					</div>
				) : (
					// Sebe admin ne uklanja ovdje: odmah bi izgubio ovu stranicu usred klika
					!ja && (
						<button className="dugme-tekst" type="button" onClick={() => setPitaj(true)}>
							Ukloni
						</button>
					)
				)}
			</div>
			{greska && (
				<p role="alert" className="greska-forme">
					{porukaGreske(greska, PORUKE_IZMJENE)}
				</p>
			)}
		</li>
	)
}
