import { Link, useParams } from 'react-router'
import { porukaGreske } from '../forma/porukaGreske.js'
import { feningeUKm } from '../resursi/cijena.js'
import { emajlResursa } from '../resursi/emajl.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija, useResursi } from '../organizacije/upiti.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { Kalendar } from './Kalendar.jsx'
import { opisiPravila } from './pravila.js'
import './kalendar.css'
import { Mrvice } from '../tema/Mrvice.jsx'

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
		// Emajl boja resursa ide kroz cijelu stranicu: pločica uz naslov i rub svakog slobodnog žetona
		<section className="resurs-stranica" style={{ '--emajl': emajlResursa(resurs.id) }}>
			<Mrvice stavke={[{ naziv: 'Organizacije', to: '/' }, { naziv: org.organizacija.naziv, to: `/o/${slug}` }, { naziv: resurs.naziv }]} />
			<h1 className="resurs-naslov">
				<span className="resurs-plocica" aria-hidden="true" />
				{/* Isto ime kao naziv na pločici resursa, pa naziv preleti s liste u naslov */}
				<span style={{ viewTransitionName: `resurs-${resurs.id}` }}>{resurs.naziv}</span>
			</h1>
			{/* Ono što se pita prije izbora termina: koliko traje, koliko košta, koliko ljudi stane */}
			<ul className="resurs-podaci">
				<li>{resurs.trajanjeMinuta} min</li>
				<li>{resurs.cijenaFeninga > 0 ? `${feningeUKm(resurs.cijenaFeninga)} KM` : 'besplatno'}</li>
				{resurs.brojMjesta > 1 && <li>mjesta: {resurs.brojMjesta}</li>}
				{opisiPravila(resurs.pravila).map((opis) => (
					<li key={opis} className="resurs-pravilo">
						{opis}
					</li>
				))}
			</ul>
			{resurs.opis && <p className="resurs-opis">{resurs.opis}</p>}
			<Kalendar
				resursId={resurs.id}
				naziv={resurs.naziv}
				zona={org.organizacija.zona}
				cijenaFeninga={resurs.cijenaFeninga}
				pravila={org.organizacija.uloga === 'CLIENT' ? resurs.pravila : null}
			/>
		</section>
	)
}
