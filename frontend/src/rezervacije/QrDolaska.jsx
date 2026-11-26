import { useQuery } from '@tanstack/react-query'
import { create } from 'qrcode'
import { useState } from 'react'
import { linkZaDolazak } from '../api/rezervacije.js'
import { porukaGreske } from '../forma/porukaGreske.js'
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

// Moduli se crtaju kao SVG kvadrati u boji teksta, pa boja dolazi iz tokena, a ne iz biblioteke.
// Okvir od 4 modula oko koda traži standard, inače ga neki čitači ne nađu.
function QrSlika({ tekst, opis }) {
	const { modules } = create(tekst, { errorCorrectionLevel: 'M' })
	const okvir = 4
	const strana = modules.size + 2 * okvir
	let putanja = ''
	for (let red = 0; red < modules.size; red++) {
		for (let kolona = 0; kolona < modules.size; kolona++) {
			if (modules.get(red, kolona)) putanja += `M${kolona + okvir} ${red + okvir}h1v1h-1z`
		}
	}
	return (
		<svg role="img" aria-label={opis} viewBox={`0 0 ${strana} ${strana}`} shapeRendering="crispEdges">
			<path d={putanja} fill="currentColor" />
		</svg>
	)
}
