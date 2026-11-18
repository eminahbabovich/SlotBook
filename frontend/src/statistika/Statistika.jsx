import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { statistika } from '../api/statistika.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija } from '../organizacije/upiti.js'
import { mozeDodatiResurs } from '../raspored/vodiResurs.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { dodajMjesece, mjesecUZoni, nazivMjeseca } from '../vrijeme/vrijeme.js'
import { CsvIzvoz } from './CsvIzvoz.jsx'
import { Najtrazeniji, Popunjenost, PoStanju } from './Tabele.jsx'
import './statistika.css'

const PORUKE_ORGANIZACIJA = {
	serverNeOdgovara: 'Organizacija se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacija se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE = {
	serverNeOdgovara: 'Statistika se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Statistika se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const MJESEC = /^\d{4}-(0[1-9]|1[0-2])$/

// Stranica /o/{slug}/statistika. Mjesec je u adresi (?mjesec=2035-11), pa se link može poslati,
// a dugme za nazad u browseru vraća prethodni mjesec.
export function Statistika() {
	const { slug } = useParams()
	const org = useOrganizacija(slug)

	if (org.isPending) {
		return <p aria-live="polite">Učitava se organizacija</p>
	}
	if (org.isError) {
		return <NijeUcitano poruka={porukaGreske(org.error, PORUKE_ORGANIZACIJA)} ponovo={() => org.refetch()} />
	}
	if (org.organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}
	// Isto kao @PreAuthorize na /stats: admin i ko vodi termine
	if (!mozeDodatiResurs(org.organizacija)) {
		return (
			<section>
				<h1>Statistika nije dostupna</h1>
				<p>Statistiku vide admin organizacije i osobe koje vode termine.</p>
				<Link to={`/o/${slug}`}>{org.organizacija.naziv}</Link>
			</section>
		)
	}
	return <StatistikaOrganizacije organizacija={org.organizacija} />
}

function StatistikaOrganizacije({ organizacija }) {
	const [parametri] = useSearchParams()
	// Tekući mjesec je po zoni organizacije: 31.12. u 23:30 UTC je u Sarajevu već januar
	const [tekuci] = useState(() => mjesecUZoni(new Date(), organizacija.zona))
	const izAdrese = parametri.get('mjesec')
	const mjesec = izAdrese !== null && MJESEC.test(izAdrese) ? izAdrese : tekuci
	const od = `${mjesec}-01`
	const doDana = `${dodajMjesece(mjesec, 1)}-01`
	const admin = organizacija.uloga === 'ADMIN'

	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['statistika', organizacija.id, mjesec],
		queryFn: () => statistika(organizacija.id, od, doDana),
	})

	let sadrzaj
	if (isPending) {
		sadrzaj = <p aria-live="polite">Učitava se statistika za {nazivMjeseca(mjesec)}</p>
	} else if (isError) {
		sadrzaj = <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	} else {
		sadrzaj = (
			<>
				<PoStanju poStanju={data.poStanju} mjesec={nazivMjeseca(mjesec)} />
				<Popunjenost
					popunjenost={data.popunjenost}
					nemaResursa={admin ? 'Organizacija još nema resursa, pa nema ni popunjenosti.' : 'Ne vodiš nijedan resurs, pa nema ni popunjenosti.'}
					csv={<CsvIzvoz orgId={organizacija.id} od={od} doDana={doDana} />}
				/>
				<Najtrazeniji poDanuISatu={data.poDanuISatu} />
			</>
		)
	}

	const opis = admin ? 'Sve rezervacije i svi resursi organizacije.' : 'Samo resursi koje vodiš.'
	return (
		<section>
			<Link to={`/o/${organizacija.slug}`}>{organizacija.naziv}</Link>
			<h1>Statistika: {organizacija.naziv}</h1>
			<p className="napomena-blok">{`${opis} Dani i sati su po zoni ${organizacija.zona}.`}</p>
			<nav className="mjesec" aria-label="Mjesec">
				<Link to={`?mjesec=${dodajMjesece(mjesec, -1)}`}>Prethodni mjesec</Link>
				<h2>{nazivMjeseca(mjesec)}</h2>
				<Link to={`?mjesec=${dodajMjesece(mjesec, 1)}`}>Sljedeći mjesec</Link>
			</nav>
			{sadrzaj}
		</section>
	)
}
