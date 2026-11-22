import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { noviResurs } from '../api/resursi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { OrganizacijaNijeDostupna } from '../organizacije/Organizacija.jsx'
import { useOrganizacija } from '../organizacije/upiti.js'
import { mozeDodatiResurs } from '../raspored/vodiResurs.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { ResursForma } from './ResursForma.jsx'

// Sat je najčešći termin (čitaonica, sala), a bez roka klijent otkazuje do početka
const PRAZAN_RESURS = { naziv: '', opis: '', trajanjeMinuta: '60', autoPotvrda: true, rokOtkazivanjaSati: '0', brojMjesta: '1', cijenaFeninga: 0 }

const PORUKE_ORGANIZACIJA = {
	serverNeOdgovara: 'Organizacija se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Organizacija se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE = {
	serverNeOdgovara: 'Resurs nije napravljen, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Resurs nije napravljen zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stranica /o/{slug}/resursi/novi
export function NoviResurs() {
	const { slug } = useParams()
	const { organizacija, isPending, isError, error, refetch } = useOrganizacija(slug)
	const queryClient = useQueryClient()
	const navigate = useNavigate()

	// Bez radnog vremena resurs nema termina, pa se odmah ide na njega. Lista se osvježi prije,
	// jer stranica radnog vremena resurs traži u njoj. refetchType 'all': ovdje lista nije
	// prikazana, a bez toga bi invalidateQueries učitao samo prikazane upite i odmah se završio.
	const pravljenje = useMutation({
		mutationFn: (resurs) => noviResurs(organizacija.id, resurs),
		onSuccess: async (resurs) => {
			await queryClient.invalidateQueries({ queryKey: ['resursi', organizacija.id], refetchType: 'all' })
			navigate(`/o/${slug}/r/${resurs.id}/radno-vrijeme`)
		},
	})

	if (isPending) {
		return <p aria-live="polite">Učitava se organizacija</p>
	}
	if (isError) {
		return <NijeUcitano poruka={porukaGreske(error, PORUKE_ORGANIZACIJA)} ponovo={() => refetch()} />
	}
	if (organizacija === undefined) {
		return <OrganizacijaNijeDostupna slug={slug} />
	}
	if (!mozeDodatiResurs(organizacija)) {
		return (
			<section>
				<h1>Resurs ne možeš dodati</h1>
				<p>Resurse dodaju admin organizacije i osobe koje vode termine.</p>
				<Link to={`/o/${slug}`}>{organizacija.naziv}</Link>
			</section>
		)
	}

	return (
		<section>
			<Link to={`/o/${slug}`}>{organizacija.naziv}</Link>
			<h1>Novi resurs</h1>
			<p className="napomena-blok">
				Resurs vodiš ti, a admin ga kasnije može dati drugoj osobi. Poslije ovoga se upisuje radno vrijeme, jer bez
				njega kalendar ne nudi termine.
			</p>
			<ResursForma pocetno={PRAZAN_RESURS} mutacija={pravljenje} dugme="Napravi resurs" dugmeSalje="Resurs se pravi" poruke={PORUKE} />
		</section>
	)
}
