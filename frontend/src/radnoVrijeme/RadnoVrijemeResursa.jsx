import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { radnoVrijeme } from '../api/radnoVrijeme.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija, useResursi } from '../organizacije/upiti.js'
import { vodiResurs } from '../raspored/vodiResurs.js'
import { useSesija } from '../sesija/sesija.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Izuzeci } from './Izuzeci.jsx'
import { Sedmica } from './Sedmica.jsx'
import './radnoVrijeme.css'

const PORUKE_RESURS = {
	serverNeOdgovara: 'Resurs se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Resurs se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_PRAVILA = {
	serverNeOdgovara: 'Radno vrijeme se nije učitalo, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Radno vrijeme se nije učitalo zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stranica /o/{slug}/r/{resursId}/radno-vrijeme. Mijenjati smiju admin i ko vodi resurs
// (canManage), a izuzeci se drugima ni ne čitaju, jer razlog može biti ličan.
export function RadnoVrijemeResursa() {
	const { slug, resursId } = useParams()
	const { korisnik } = useSesija()
	const org = useOrganizacija(slug)
	const resursi = useResursi(org.organizacija)

	if (org.isPending || (org.organizacija !== undefined && resursi.isPending)) {
		return <p aria-live="polite">Učitava se resurs</p>
	}
	const greska = org.isError ? org : resursi.isError ? resursi : null
	if (greska) {
		return <NijeUcitano poruka={porukaGreske(greska.error, PORUKE_RESURS)} ponovo={() => greska.refetch()} />
	}
	if (org.organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}
	const resurs = resursi.data.find((r) => r.id === resursId)
	if (resurs === undefined || !vodiResurs(org.organizacija, resurs, korisnik.id)) {
		return (
			<section>
				<h1>Radno vrijeme nije dostupno</h1>
				<p>Radno vrijeme i izuzetke mijenjaju admin organizacije i osoba koja vodi resurs.</p>
				<Link to={`/o/${slug}`}>{org.organizacija.naziv}</Link>
			</section>
		)
	}

	return (
		<section>
			<Link to={`/o/${slug}/r/${resurs.id}/uredi`}>Izmjena: {resurs.naziv}</Link>
			<h1>Radno vrijeme: {resurs.naziv}</h1>
			<p className="napomena-blok">
				Sati su po zoni {org.organizacija.zona}. Kalendar nudi termine samo unutar radnog vremena, a u izuzecima ni tada.
			</p>
			<Pravila resursId={resurs.id} />
			<Izuzeci resursId={resurs.id} zona={org.organizacija.zona} />
		</section>
	)
}

function Pravila({ resursId }) {
	const { data, isPending, isError, error, refetch } = useQuery({ queryKey: ['radnoVrijeme', resursId], queryFn: () => radnoVrijeme(resursId) })

	if (isPending) {
		return <p aria-live="polite">Učitava se radno vrijeme</p>
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE_PRAVILA)} ponovo={() => refetch()} />
	}
	// Sedmica drži svoju kopiju za uređivanje. Pravi se tek kad pravila stignu, pa ih ne
	// gazi osvježavanje poslije čuvanja.
	return <Sedmica resursId={resursId} intervali={data.intervali} />
}
