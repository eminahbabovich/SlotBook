import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { ispisiSaListe, upisiNaListu } from '../api/listaCekanja.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { opisTermina } from '../vrijeme/opis.js'

const PORUKE = {
	serverNeOdgovara: 'Server ne odgovara, pa nije jasno da li je promjena stigla. Pokušaj ponovo: dvaput se neće upisati.',
	opsta: 'Promjena nije uspjela zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Red ispod zauzetog termina, isti oblik kao potvrda rezervacije. Ako sam već na listi, ovdje se ispisujem.
export function ListaCekanja({ resursId, termin, zona, naZatvori, naUspjeh }) {
	const queryClient = useQueryClient()
	const naslov = useId()
	const dugme = useRef(null)
	const naListi = termin.upisId !== null && termin.upisId !== undefined
	const opis = opisTermina(termin, zona)

	const slanje = useMutation({
		mutationFn: () => (naListi ? ispisiSaListe(termin.upisId) : upisiNaListu(resursId, termin.pocetak)),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
			queryClient.invalidateQueries({ queryKey: ['listaCekanja'] })
			naUspjeh(
				naListi
					? `Više nisi na listi čekanja za ${opis}.`
					: `Na listi čekanja si za ${opis}. Ako se termin oslobodi, stiže ti mail i 30 minuta da ga rezervišeš.`,
			)
		},
		onError: (greska) => {
			// Termin se oslobodio ili je postao moj: kalendar je zastario, pa se učita ponovo
			if (greska instanceof ApiError && greska.status === 409) {
				queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
			}
		},
	})

	useEffect(() => {
		dugme.current?.focus()
	}, [])

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
				{naListi ? `Na listi čekanja si za ${opis}.` : `Termin ${opis} je zauzet.`}
			</p>
			<p className="napomena">
				{naListi
					? 'Kad se oslobodi, prvi na listi dobija mail i 30 minuta u kojima ga niko drugi ne može uzeti.'
					: 'Ako se oslobodi, javimo ti mailom. Ako si prvi na listi, termin je 30 minuta samo tvoj.'}
			</p>
			{slanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(slanje.error, PORUKE)}
				</p>
			)}
			<div className="potvrda-dugmad">
				<button ref={dugme} className="dugme" type="button" onClick={() => slanje.mutate()} disabled={slanje.isPending}>
					{slanje.isPending ? 'Šalje se' : naListi ? 'Ispiši me sa liste' : 'Javi mi ako se oslobodi'}
				</button>
				<button className="dugme-tekst" type="button" onClick={naZatvori}>
					Zatvori
				</button>
			</div>
		</div>
	)
}
