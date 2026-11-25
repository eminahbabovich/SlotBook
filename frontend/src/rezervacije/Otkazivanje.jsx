import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { otkazi } from '../api/rezervacije.js'
import { porukaGreske, serverNeOdgovara } from '../forma/porukaGreske.js'
import { feningeUKm } from '../resursi/cijena.js'
import { opisTermina } from '../vrijeme/opis.js'
import { placena } from './status.js'

const PORUKE = {
	serverNeOdgovara:
		'Nije jasno da li je otkazivanje stiglo, server ne odgovara. Pokušaj ponovo: ako je stiglo, rezervacija ostaje otkazana.',
	opsta: 'Otkazivanje nije uspjelo zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Otkazivanje nema Idempotency-Key, ali ga ni ne treba: druga ista promjena dobije 409 sa
// trenutniStatus CANCELLED. Kad se to desi poslije pada mreže, prvi zahtjev je prošao, i to je
// uspjeh, a ne greška.
async function otkaziJednom(id) {
	try {
		return await otkazi(id)
	} catch (greska) {
		if (greska instanceof ApiError && greska.status === 409 && greska.problem?.trenutniStatus === 'CANCELLED') {
			return { id, status: 'CANCELLED' }
		}
		throw greska
	}
}

export function Otkazivanje({ rezervacija, naZatvori, naUspjeh }) {
	const queryClient = useQueryClient()
	const naslov = useId()
	const dugme = useRef(null)

	const slanje = useMutation({
		mutationFn: () => otkaziJednom(rezervacija.id),
		onSuccess: () => {
			// Termin se oslobodio, pa i kalendar resursa mora pokazati novo stanje
			queryClient.invalidateQueries({ queryKey: ['mojeRezervacije'] })
			queryClient.invalidateQueries({ queryKey: ['kalendar'] })
			naUspjeh(rezervacija)
		},
		onError: (greska) => {
			if (greska instanceof ApiError && greska.status === 409) {
				queryClient.invalidateQueries({ queryKey: ['mojeRezervacije'] })
			}
		},
	})

	useEffect(() => {
		dugme.current?.focus()
	}, [])

	const odbijeno = slanje.error instanceof ApiError && slanje.error.status === 409

	return (
		<div
			className="potvrda"
			role="group"
			aria-labelledby={naslov}
			onKeyDown={(d) => {
				if (d.key === 'Escape') naZatvori()
			}}
		>
			<p id={naslov} className="potvrda-pitanje">
				Otkazati {rezervacija.resurs}, {opisTermina(rezervacija, rezervacija.zona)}?
			</p>
			{placena(rezervacija) && (
				<p className="napomena">Plaćeno je {feningeUKm(rezervacija.cijenaFeninga)} KM. Cijeli iznos se vraća na karticu.</p>
			)}
			{slanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(slanje.error, PORUKE)}
				</p>
			)}
			<div className="potvrda-dugmad">
				{!odbijeno && (
					<button ref={dugme} className="dugme dugme-opasno" type="button" onClick={() => slanje.mutate()} disabled={slanje.isPending}>
						{slanje.isPending ? 'Otkazivanje u toku' : serverNeOdgovara(slanje.error) ? 'Pokušaj ponovo' : 'Otkaži rezervaciju'}
					</button>
				)}
				<button className="dugme-tekst" type="button" onClick={naZatvori}>
					{odbijeno ? 'Zatvori' : 'Ne, ostavi je'}
				</button>
			</div>
		</div>
	)
}
