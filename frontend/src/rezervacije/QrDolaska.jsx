import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { linkZaDolazak } from '../api/rezervacije.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { QrSlika } from '../qr/QrSlika.jsx'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'

const PORUKE = {
	serverNeOdgovara: 'QR kod se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'QR kod se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Kod se otvara na klik, ne za svaku rezervaciju u listi, jer link traži poziv backendu
export function QrDolaska({ rezervacija }) {
	const [otvoren, setOtvoren] = useState(false)

	return (
		<>
			<button className="dugme" type="button" aria-expanded={otvoren} onClick={() => setOtvoren(!otvoren)}>
				{otvoren ? 'Sakrij QR kod' : 'QR kod za dolazak'}
			</button>
			{otvoren && <Kod rezervacija={rezervacija} />}
		</>
	)
}

function Kod({ rezervacija }) {
	const { data: link, isPending, isError, error, refetch } = useQuery({
		queryKey: ['linkZaDolazak', rezervacija.id],
		queryFn: () => linkZaDolazak(rezervacija.id),
		// Link se ne mijenja, isti je za istu rezervaciju
		staleTime: Infinity,
	})

	if (isPending) return <Ucitava tekst="Učitava se QR kod" />
	if (isError) return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	return (
		<figure className="qr-dolaska">
			<QrSlika tekst={link} opis={`QR kod za dolazak: ${rezervacija.resurs}`} />
			<figcaption className="napomena">
				Pokaži ga osobi koja vodi termin. Kad ga skenira, dolazak je upisan.
			</figcaption>
		</figure>
	)
}
