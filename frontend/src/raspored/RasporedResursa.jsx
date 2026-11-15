import { Link, useParams } from 'react-router'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija, useResursi } from '../organizacije/upiti.js'
import { useSesija } from '../sesija/sesija.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Raspored } from './Raspored.jsx'
import { vodiResurs } from './vodiResurs.js'

const PORUKE = {
	serverNeOdgovara: 'Resurs se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Resurs se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stranica /o/{slug}/r/{resursId}/raspored
export function RasporedResursa() {
	const { slug, resursId } = useParams()
	const { korisnik } = useSesija()
	const org = useOrganizacija(slug)
	const resursi = useResursi(org.organizacija)

	if (org.isPending || (org.organizacija !== undefined && resursi.isPending)) {
		return <p aria-live="polite">Učitava se raspored</p>
	}
	const greska = org.isError ? org : resursi.isError ? resursi : null
	if (greska) {
		return <NijeUcitano poruka={porukaGreske(greska.error, PORUKE)} ponovo={() => greska.refetch()} />
	}
	if (org.organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}
	const resurs = resursi.data.find((r) => r.id === resursId)
	if (resurs === undefined || !vodiResurs(org.organizacija, resurs, korisnik.id)) {
		return (
			<section>
				<h1>Raspored nije dostupan</h1>
				<p>Raspored s imenima vide admin organizacije i osoba koja vodi ovaj termin.</p>
				{resurs !== undefined && (
					<Link to={`/o/${slug}/r/${resurs.id}`} aria-label={`Kalendar: ${resurs.naziv}`}>
						Slobodni termini za {resurs.naziv}
					</Link>
				)}
			</section>
		)
	}

	return (
		<section>
			<Link to={`/o/${slug}`}>{org.organizacija.naziv}</Link>
			<p className="raspored-nadnaslov">Raspored</p>
			<h1>{resurs.naziv}</h1>
			<Raspored resursId={resurs.id} zona={org.organizacija.zona} />
		</section>
	)
}
