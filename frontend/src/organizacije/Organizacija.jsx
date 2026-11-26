import { mozeDodatiResurs, vodiResurs } from '../raspored/vodiResurs.js'
import { useSesija } from '../sesija/sesija.js'
import { Link, useParams } from 'react-router'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { ULOGE, useOrganizacija, useResursi } from './upiti.js'
import { emajlResursa, inicijali } from '../resursi/emajl.js'
import { Mrvice } from '../tema/Mrvice.jsx'
import { PrelazLink } from '../tema/PrelazLink.jsx'

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
		return <Ucitava tekst="Učitava se organizacija" />
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	}
	if (organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}

	return (
		<section>
			<Mrvice stavke={[{ naziv: 'Organizacije', to: '/' }, { naziv: organizacija.naziv }]} />
			<h1>
				<span className="ime-prelaz" style={{ viewTransitionName: `org-${organizacija.id}` }}>
					{organizacija.naziv}
				</span>
			</h1>
			<div className="organizacija-podaci">
				<p>Tvoja uloga: {ULOGE[organizacija.uloga] ?? organizacija.uloga}.</p>
				<p>Vrijeme: {organizacija.zona.split('/').pop().replace('_', ' ')}</p>
			</div>
			{(organizacija.uloga === 'ADMIN' || mozeDodatiResurs(organizacija)) && (
				<p className="organizacija-veze">
					{organizacija.uloga === 'ADMIN' && (
						<Link className="veza-pilula" to={`/o/${organizacija.slug}/clanovi`}>
							Članovi i pozivnice
						</Link>
					)}
					{mozeDodatiResurs(organizacija) && (
						<Link className="veza-pilula" to={`/o/${organizacija.slug}/resursi/novi`}>
							Novi resurs
						</Link>
					)}
					{mozeDodatiResurs(organizacija) && (
						<Link className="veza-pilula" to={`/o/${organizacija.slug}/statistika`}>
							Statistika
						</Link>
					)}
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
		return <Ucitava tekst="Učitavaju se resursi" />
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE_RESURSI)} ponovo={() => refetch()} />
	}
	if (resursi.length === 0) {
		return mozeDodatiResurs(organizacija) ? (
			<p className="prazno">Ova organizacija još nema ništa za rezervaciju. Prvi resurs, npr. salu, sto ili termin konsultacija, dodaješ preko linka Novi resurs.</p>
		) : (
			<p className="prazno">
				Ova organizacija još nema ništa za rezervaciju. Kad admin doda salu, sto ili termin konsultacija, pojavit će se
				ovdje.
			</p>
		)
	}
	return (
		<ul className="tabla">
			{resursi.map((r) => (
				<li key={r.id} className="tabla-stavka" style={{ '--emajl': emajlResursa(r.id) }}>
					<PrelazLink to={`/o/${organizacija.slug}/r/${r.id}`} className="tabla-red" data-znak={inicijali(r.naziv)}>
						<span className="tabla-naziv" style={{ viewTransitionName: `resurs-${r.id}` }}>
							{r.naziv}
						</span>
						<span className="tabla-uloga">
							{!r.aktivan && 'deaktiviran, '}termini po {r.trajanjeMinuta} min
						</span>
					</PrelazLink>
					{vodiResurs(organizacija, r, korisnik.id) && (
						<>
							<Link to={`/o/${organizacija.slug}/r/${r.id}/raspored`} className="tabla-raspored" aria-label={`Raspored: ${r.naziv}`}>
								Raspored
							</Link>
							<Link to={`/o/${organizacija.slug}/r/${r.id}/uredi`} className="tabla-raspored" aria-label={`Uredi: ${r.naziv}`}>
								Uredi
							</Link>
						</>
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
