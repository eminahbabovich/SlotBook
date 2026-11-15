import { Link, useParams } from 'react-router'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { ULOGE, useMojeOrganizacije } from './upiti.js'

const PORUKE = {
	serverNeOdgovara: 'Organizacija se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacija se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Privremena stranica organizacije. U koraku 6 ovdje dolazi kalendar termina.
// Organizacija se traži u mojoj listi: ako je nema, isto se piše i kad ne postoji i kad
// korisnik nije član, kao što ni backend tuđoj osobi ne kaže da li organizacija postoji.
export function Organizacija() {
	const { slug } = useParams()
	const { data: organizacije, isPending, isError, error, refetch } = useMojeOrganizacije()

	if (isPending) {
		return <p aria-live="polite">Učitava se organizacija</p>
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	}

	const organizacija = organizacije.find((o) => o.slug === slug)
	if (organizacija === undefined) {
		return (
			<section>
				<h1>Organizacija nije dostupna</h1>
				<p>Organizacija sa adresom {slug} ne postoji ili nisi njen član. Pozivnicu za člana šalje admin organizacije.</p>
				<Link to="/">Tvoje organizacije</Link>
			</section>
		)
	}

	return (
		<section>
			<h1>{organizacija.naziv}</h1>
			<p>Tvoja uloga: {ULOGE[organizacija.uloga] ?? organizacija.uloga}.</p>
			<p className="napomena-blok">Vrijeme termina je po zoni {organizacija.zona}.</p>
			<Link to="/">Sve organizacije</Link>
		</section>
	)
}
