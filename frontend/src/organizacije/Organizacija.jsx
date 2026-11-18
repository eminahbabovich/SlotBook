import { vodiResurs } from '../raspored/vodiResurs.js'
import { useSesija } from '../sesija/sesija.js'
import { Link, useParams } from 'react-router'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { ULOGE, useOrganizacija, useResursi } from './upiti.js'

const PORUKE = {
	serverNeOdgovara: 'Organizacija se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacija se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_RESURSI = {
	serverNeOdgovara: 'Resursi se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Resursi se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

export function Organizacija() {
	const { slug } = useParams()
	const { organizacija, isPending, isError, error, refetch } = useOrganizacija(slug)

	if (isPending) {
		return <p aria-live="polite">Učitava se organizacija</p>
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	}
	if (organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}

	return (
		<section>
			<h1>{organizacija.naziv}</h1>
			<p>Tvoja uloga: {ULOGE[organizacija.uloga] ?? organizacija.uloga}.</p>
			<p className="napomena-blok">Vrijeme termina je po zoni {organizacija.zona}.</p>
			{organizacija.uloga === 'ADMIN' && (
				<p>
					<Link to={`/o/${organizacija.slug}/clanovi`}>Članovi i pozivnice</Link>
				</p>
			)}
			<Resursi organizacija={organizacija} />
		</section>
	)
}

function Resursi({ organizacija }) {
	const { data: resursi, isPending, isError, error, refetch } = useResursi(organizacija)
	const { korisnik } = useSesija()

	if (isPending) {
		return <p aria-live="polite">Učitavaju se resursi</p>
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE_RESURSI)} ponovo={() => refetch()} />
	}
	if (resursi.length === 0) {
		return (
			<p>
				Ova organizacija još nema ništa za rezervaciju. Kad admin doda salu, sto ili termin konsultacija, pojavit će se
				ovdje.
			</p>
		)
	}
	return (
		<ul className="tabla">
			{resursi.map((r) => (
				<li key={r.id} className="tabla-stavka">
					<Link to={`/o/${organizacija.slug}/r/${r.id}`} className="tabla-red">
						<span className="tabla-naziv">{r.naziv}</span>
						<span className="tabla-uloga">termini po {r.trajanjeMinuta} min</span>
					</Link>
					{vodiResurs(organizacija, r, korisnik.id) && (
						<Link to={`/o/${organizacija.slug}/r/${r.id}/raspored`} className="tabla-raspored" aria-label={`Raspored: ${r.naziv}`}>
							Raspored
						</Link>
					)}
				</li>
			))}
		</ul>
	)
}

// Isto se piše i kad organizacija ne postoji i kad korisnik nije član, kao što ni backend
// tuđoj osobi ne kaže da li organizacija postoji.
export function OrganizacijaNijeDostupna({ slug }) {
	return (
		<section>
			<h1>Organizacija nije dostupna</h1>
			<p>Organizacija sa adresom {slug} ne postoji ili nisi njen član. Pozivnicu za člana šalje admin organizacije.</p>
			<Link to="/">Tvoje organizacije</Link>
		</section>
	)
}
