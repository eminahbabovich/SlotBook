import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { izmijeniResurs } from '../api/resursi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija, useResursi } from '../organizacije/upiti.js'
import { vodiResurs } from '../raspored/vodiResurs.js'
import { useSesija } from '../sesija/sesija.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { Deaktivacija } from './Deaktivacija.jsx'
import { ResursForma } from './ResursForma.jsx'
import { Voditelj } from './Voditelj.jsx'

const PORUKE_UCITAVANJE = {
	serverNeOdgovara: 'Resurs se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Resurs se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE = {
	serverNeOdgovara: 'Promjene nisu sačuvane, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Promjene nisu sačuvane zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stranica /o/{slug}/r/{resursId}/uredi
export function IzmjenaResursa() {
	const { slug, resursId } = useParams()
	const { korisnik } = useSesija()
	const org = useOrganizacija(slug)
	const resursi = useResursi(org.organizacija)
	const queryClient = useQueryClient()

	const izmjena = useMutation({
		mutationFn: (resurs) => izmijeniResurs(org.organizacija.id, resursId, resurs),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ['resursi', org.organizacija.id] }),
	})

	if (org.isPending || (org.organizacija !== undefined && resursi.isPending)) {
		return <Ucitava tekst="Učitava se resurs" />
	}
	const greska = org.isError ? org : resursi.isError ? resursi : null
	if (greska) {
		return <NijeUcitano poruka={porukaGreske(greska.error, PORUKE_UCITAVANJE)} ponovo={() => greska.refetch()} />
	}
	if (org.organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}
	const resurs = resursi.data.find((r) => r.id === resursId)
	if (resurs === undefined || !vodiResurs(org.organizacija, resurs, korisnik.id)) {
		return (
			<section>
				<h1>Izmjena nije dostupna</h1>
				<p>Resurs mijenjaju admin organizacije i osoba koja ga vodi.</p>
				<Link to={`/o/${slug}`}>{org.organizacija.naziv}</Link>
			</section>
		)
	}

	return (
		<section>
			<Link to={`/o/${slug}`}>{org.organizacija.naziv}</Link>
			<h1>Izmjena: {resurs.naziv}</h1>
			<p>
				<Link to={`/o/${slug}/r/${resurs.id}/radno-vrijeme`}>Radno vrijeme i izuzeci</Link>
			</p>
			<ResursForma pocetno={resurs} mutacija={izmjena} dugme="Sačuvaj promjene" dugmeSalje="Promjene se čuvaju" poruke={PORUKE} uspjeh="Promjene su sačuvane." />
			{org.organizacija.uloga === 'ADMIN' ? (
				<Voditelj organizacija={org.organizacija} resurs={resurs} />
			) : (
				<p>Resurs vodiš ti. Osobu koja ga vodi mijenja admin organizacije.</p>
			)}
			<Deaktivacija organizacija={org.organizacija} resurs={resurs} />
		</section>
	)
}
