import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { pozivniLinkovi } from '../api/clanovi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija } from '../organizacije/upiti.js'
import { QrSlika } from '../qr/QrSlika.jsx'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { kljucPozivnihLinkova } from './kljucevi.js'
import './clanovi.css'

const PORUKE = {
	serverNeOdgovara: 'Plakat se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Plakat se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// /o/{slug}/plakat: list A4 s QR kodom aktivnog pozivnog linka, za vrata čitaonice ili oglasnu tablu.
// Štampa ga browser (Ctrl+P), a print CSS sakrije sve osim lista.
export function Plakat() {
	const { slug } = useParams()
	const { organizacija, isPending, isError, error, refetch } = useOrganizacija(slug)

	if (isPending) return <Ucitava tekst="Učitava se organizacija" />
	if (isError) return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	if (organizacija === undefined) return <OrganizacijaNijeDostupna slug={slug} />
	if (organizacija.uloga !== 'ADMIN') {
		return (
			<section>
				<h1>Plakat nije dostupan</h1>
				<p>Plakat s pozivnim linkom pravi admin organizacije.</p>
				<Link to={`/o/${slug}`}>{organizacija.naziv}</Link>
			</section>
		)
	}
	return <PlakatOrganizacije organizacija={organizacija} />
}

function PlakatOrganizacije({ organizacija }) {
	const { data: linkovi, isPending, isError, error, refetch } = useQuery({
		queryKey: kljucPozivnihLinkova(organizacija.id),
		queryFn: () => pozivniLinkovi(organizacija.id),
	})

	if (isPending) return <Ucitava tekst="Učitava se pozivni link" />
	if (isError) return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />

	const aktivan = linkovi.find((l) => l.aktivan)
	if (!aktivan) {
		return (
			<section>
				<h1>Nema aktivnog linka</h1>
				<p>Plakat nosi QR kod pozivnog linka. Napravi link među članovima, pa se vrati ovdje.</p>
				<Link to={`/o/${organizacija.slug}/clanovi`}>Članovi i pozivni link</Link>
			</section>
		)
	}

	return (
		<section className="plakat-stranica">
			<div className="plakat-radnje radnje-linka">
				<Link to={`/o/${organizacija.slug}/clanovi`}>Nazad na članove</Link>
				<button className="dugme" type="button" onClick={() => window.print()}>
					Štampaj
				</button>
			</div>
			<article className="plakat" aria-label="Plakat za štampu">
				<h1>{organizacija.naziv}</h1>
				<p>Termine rezervišeš na telefonu. Skeniraj kod, napravi nalog i biraj slobodan termin.</p>
				<figure className="qr-plakata">
					<QrSlika tekst={aktivan.url} opis={`QR kod za ulazak u ${organizacija.naziv}`} />
				</figure>
				<p className="adresa-plakata">{aktivan.url}</p>
			</article>
		</section>
	)
}
