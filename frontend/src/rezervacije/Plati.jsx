import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../api/apiFetch.js'
import { platiti } from '../api/rezervacije.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { feningeUKm } from '../resursi/cijena.js'
import { idiNaStripe } from './idiNaStripe.js'

const PORUKE = {
	serverNeOdgovara:
		'Plaćanje se nije otvorilo, server ili Stripe ne odgovara. Termin je i dalje tvoj do roka za uplatu, pa pokušaj ponovo za minutu.',
	opsta: 'Plaćanje se nije otvorilo zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Kartica se upisuje na Stripe stranici, ne kod nas. Drugi klik dok je ista sesija otvorena dobije
// istu adresu (backend), pa se ne može platiti dvaput. naOdbijeno dobije poruku kad rezervacija više
// ne čeka uplatu (409): rok je prošao ili je već plaćena, pa roditelj osvježi svoj prikaz.
export function Plati({ rezervacijaId, cijenaFeninga, naOdbijeno }) {
	const slanje = useMutation({
		mutationFn: () => platiti(rezervacijaId),
		onSuccess: (adresa) => idiNaStripe(adresa),
		onError: (greska) => {
			if (greska instanceof ApiError && greska.status === 409) naOdbijeno(porukaGreske(greska, PORUKE))
		},
	})
	// Poslije uspjeha browser odlazi na Stripe, pa dugme ostaje isključeno dok se stranica ne promijeni
	const otvara = slanje.isPending || slanje.isSuccess

	return (
		<>
			{slanje.isError && !(slanje.error instanceof ApiError && slanje.error.status === 409) && (
				<p role="alert" className="greska-forme">
					{porukaGreske(slanje.error, PORUKE)}
				</p>
			)}
			<button className="dugme" type="button" onClick={() => slanje.mutate()} disabled={otvara}>
				{otvara ? 'Otvara se plaćanje' : `Plati ${feningeUKm(cijenaFeninga)} KM`}
			</button>
		</>
	)
}
