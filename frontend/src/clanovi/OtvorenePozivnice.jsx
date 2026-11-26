import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { otvorenePozivnice, povuciPozivnicu } from '../api/clanovi.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { ULOGE } from '../organizacije/upiti.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { opisTrenutka } from '../vrijeme/opis.js'
import './clanovi.css'

const PORUKE = {
	serverNeOdgovara: 'Pozivnice se nisu učitale, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Pozivnice se nisu učitale zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_POVLACENJA = {
	serverNeOdgovara: 'Pozivnica nije povučena, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Pozivnica nije povučena zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Pozivnice na email koje još niko nije prihvatio. Kad je lista prazna, odjeljka nema: nema šta
// pokazati, a forma iznad već kaže kako se poziva.
export function OtvorenePozivnice({ organizacija }) {
	const queryClient = useQueryClient()
	// Trenutak otvaranja stranice je dovoljan za oznaku "istekla"
	const [sada] = useState(() => Date.now())
	const kljuc = ['otvorenePozivnice', organizacija.id]
	const { data: pozivnice, isPending, isError, error, refetch } = useQuery({
		queryKey: kljuc,
		queryFn: () => otvorenePozivnice(organizacija.id),
	})
	const povlacenje = useMutation({
		mutationFn: (id) => povuciPozivnicu(organizacija.id, id),
		// I na 404: drugi admin ju je već povukao ili je osoba upravo prihvatila
		onSettled: () => queryClient.invalidateQueries({ queryKey: kljuc }),
	})

	if (isPending) return <Ucitava tekst="Učitavaju se pozivnice" />
	if (isError) return <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	if (pozivnice.length === 0) return null

	return (
		<section aria-labelledby="otvorene-pozivnice">
			<h2 id="otvorene-pozivnice">Čekaju odgovor</h2>
			{povlacenje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(povlacenje.error, PORUKE_POVLACENJA)}
				</p>
			)}
			<ul className="lista-clanova" aria-label="Otvorene pozivnice">
				{pozivnice.map((p) => (
					<li key={p.id}>
						<span className="clan-ime">
							<strong>{p.email}</strong>
							<span className="clan-sporedno">
								{ULOGE[p.uloga] ?? p.uloga},{' '}
								{Date.parse(p.vaziDo) <= sada ? 'istekla' : `važi do ${opisTrenutka(p.vaziDo, organizacija.zona)}`}
							</span>
						</span>
						<div className="clan-radnje">
							<button
								className="dugme-tekst"
								type="button"
								onClick={() => povlacenje.mutate(p.id)}
								disabled={povlacenje.isPending && povlacenje.variables === p.id}
							>
								Povuci pozivnicu
							</button>
						</div>
					</li>
				))}
			</ul>
		</section>
	)
}
