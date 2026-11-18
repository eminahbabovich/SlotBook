import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { deaktivirajResurs } from '../api/resursi.js'
import { porukaGreske } from '../forma/porukaGreske.js'

const PORUKE = {
	serverNeOdgovara: 'Resurs nije deaktiviran, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Resurs nije deaktiviran zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Ponovna aktivacija ne postoji (docs/ODLUKE.md), pa deaktivacija traži potvrdu.
// DELETE je idempotentan, pa ponovni pokušaj poslije pada mreže ne smeta.
export function Deaktivacija({ organizacija, resurs }) {
	const [pita, setPita] = useState(false)
	const odustani = useRef(null)
	const queryClient = useQueryClient()

	const deaktivacija = useMutation({
		mutationFn: () => deaktivirajResurs(organizacija.id, resurs.id),
		onSuccess: () => {
			setPita(false)
			return queryClient.invalidateQueries({ queryKey: ['resursi', organizacija.id] })
		},
	})

	// Fokus na odustajanje, da Enter ne potvrdi nešto što se ne može vratiti
	useEffect(() => {
		if (pita) odustani.current?.focus()
	}, [pita])

	return (
		<section className="forma">
			{/* Najava za čitač ekrana, jer dugme na kojem je bio fokus nestane */}
			<p role="status" className="skriveno">
				{deaktivacija.isSuccess && 'Resurs je deaktiviran.'}
			</p>
			{!resurs.aktivan ? (
				<>
					<h2>Resurs je deaktiviran</h2>
					<p>Klijenti ga ne vide i ne mogu ga rezervisati. Ponovno uključivanje za sada ne postoji.</p>
				</>
			) : (
				<>
					<h2>Deaktivacija</h2>
					<p>
						Klijenti deaktiviran resurs ne vide i ne mogu ga rezervisati. Rezervacije koje već postoje se ne otkazuju same.
						Ponovno uključivanje za sada ne postoji.
					</p>
					{deaktivacija.isError && (
						<p role="alert" className="greska-forme">
							{porukaGreske(deaktivacija.error, PORUKE)}
						</p>
					)}
					{pita ? (
						<div role="group" aria-label="Potvrda deaktivacije" className="potvrda-dugmad">
							<button className="dugme" type="button" onClick={() => deaktivacija.mutate()} disabled={deaktivacija.isPending}>
								{deaktivacija.isPending ? 'Resurs se deaktivira' : 'Da, deaktiviraj'}
							</button>
							<button className="dugme-tekst" type="button" ref={odustani} onClick={() => setPita(false)}>
								Odustani
							</button>
						</div>
					) : (
						<button className="dugme" type="button" onClick={() => setPita(true)}>
							Deaktiviraj resurs
						</button>
					)}
				</>
			)}
		</section>
	)
}
