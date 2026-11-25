import { Link, useParams } from 'react-router'
import { porukaGreske } from '../forma/porukaGreske.js'
import { feningeUKm } from '../resursi/cijena.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija, useResursi } from '../organizacije/upiti.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { Kalendar } from './Kalendar.jsx'
import './kalendar.css'

const PORUKE = {
	serverNeOdgovara: 'Resurs se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Resurs se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stranica /o/{slug}/r/{resursId}: prvo organizacija iz moje liste, pa resurs iz njene liste.
export function KalendarResursa() {
	const { slug, resursId } = useParams()
	const org = useOrganizacija(slug)
	const resursi = useResursi(org.organizacija)

	if (org.isPending || (org.organizacija !== undefined && resursi.isPending)) {
		return <Ucitava tekst="Učitava se kalendar" />
	}
	const greska = org.isError ? org : resursi.isError ? resursi : null
	if (greska) {
		return <NijeUcitano poruka={porukaGreske(greska.error, PORUKE)} ponovo={() => greska.refetch()} />
	}
	if (org.organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}

	const resurs = resursi.data.find((r) => r.id === resursId)
	if (resurs === undefined) {
		return (
			<section>
				<h1>Resurs nije dostupan</h1>
				<p>Ovaj resurs ne postoji ili nije u organizaciji {org.organizacija.naziv}.</p>
				<Link to={`/o/${slug}`}>Nazad na {org.organizacija.naziv}</Link>
			</section>
		)
	}

	return (
		<section>
			<Link to={`/o/${slug}`}>{org.organizacija.naziv}</Link>
			<h1>{resurs.naziv}</h1>
			{/* Ono što se pita prije izbora termina: koliko traje, koliko košta, koliko ljudi stane */}
			<p className="resurs-podaci">
				{resurs.trajanjeMinuta} min · {resurs.cijenaFeninga > 0 ? `${feningeUKm(resurs.cijenaFeninga)} KM` : 'besplatno'}
				{resurs.brojMjesta > 1 && ` · mjesta: ${resurs.brojMjesta}`}
			</p>
			{resurs.opis && <p>{resurs.opis}</p>}
			<Kalendar resursId={resurs.id} naziv={resurs.naziv} zona={org.organizacija.zona} cijenaFeninga={resurs.cijenaFeninga} />
		</section>
	)
}
