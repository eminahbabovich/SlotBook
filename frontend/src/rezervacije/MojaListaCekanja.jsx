import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Link } from 'react-router'
import { ispisiSaListe, mojaListaCekanja } from '../api/listaCekanja.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { opisTermina, opisTrenutka } from '../vrijeme/opis.js'
import { danUZoni, nazivDana } from '../vrijeme/vrijeme.js'

const PORUKE = {
	serverNeOdgovara: 'Lista čekanja se nije učitala, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Lista čekanja se nije učitala zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_ISPISA = {
	serverNeOdgovara: 'Server ne odgovara, pa nije jasno da li je ispis stigao. Pokušaj ponovo.',
	opsta: 'Ispis nije uspio zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Prva stranica je dovoljna: živih upisa ima malo, a stariji termini ionako istječu
export function MojaListaCekanja({ sada, naPoruku }) {
	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['listaCekanja', 0],
		queryFn: () => mojaListaCekanja(0),
	})

	let sadrzaj
	if (isPending) {
		sadrzaj = <p aria-live="polite">Učitava se tvoja lista čekanja</p>
	} else if (isError) {
		sadrzaj = <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	} else if (data.upisi.length === 0) {
		sadrzaj = <p>Nisi ni na jednoj listi čekanja. Kad je termin u kalendaru zauzet, možeš tražiti da ti javimo ako se oslobodi.</p>
	} else {
		sadrzaj = (
			<ul className="rezervacije" aria-label="Lista čekanja">
				{data.upisi.map((u) => (
					<Upis key={u.id} upis={u} sada={sada} naPoruku={naPoruku} />
				))}
			</ul>
		)
	}

	return (
		<>
			<h2>Lista čekanja</h2>
			{sadrzaj}
		</>
	)
}

function Upis({ upis: u, sada, naPoruku }) {
	const queryClient = useQueryClient()
	const [pita, setPita] = useState(false)
	const naslov = useId()
	const ponudaTraje = u.status === 'OFFERED' && u.rokPonude !== null && Date.parse(u.rokPonude) > sada

	const ispis = useMutation({
		mutationFn: () => ispisiSaListe(u.id),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['listaCekanja'] })
			queryClient.invalidateQueries({ queryKey: ['kalendar', u.resursId] })
			naPoruku(`Više nisi na listi čekanja: ${u.resurs}, ${opisTermina(u, u.zona)}.`)
		},
	})

	return (
		<li className={`rezervacija upis-${ponudaTraje ? 'ponudjen' : 'ceka'}`}>
			<time dateTime={u.pocetak}>
				<span className="rezervacija-dan">{nazivDana(danUZoni(u.pocetak, u.zona))}, </span>
				<span className="rezervacija-sat">{opisTermina(u, u.zona, { bezDana: true })}</span>
			</time>
			<p className="rezervacija-mjesto">
				{u.resurs} · {u.organizacija}
			</p>
			<p className="rezervacija-status">
				{ponudaTraje
					? `termin se oslobodio. Rok za rezervaciju: ${opisTrenutka(u.rokPonude, u.zona)}.`
					: u.status === 'OFFERED'
						? 'rok za ponudu je prošao, termin ide sljedećem na listi'
						: 'čekaš da se termin oslobodi'}
			</p>
			{ponudaTraje && <Link to={`/o/${u.slug}/r/${u.resursId}`}>Rezerviši u kalendaru</Link>}
			{!pita && (
				<button className="dugme-tekst" type="button" onClick={() => setPita(true)}>
					Ispiši me sa liste
				</button>
			)}
			{pita && (
				<div className="potvrda" role="group" aria-labelledby={naslov}>
					<p id={naslov} className="potvrda-pitanje">
						Ispisati se sa liste za {opisTermina(u, u.zona)}? Ako se predomisliš, ponovo ideš na kraj liste.
					</p>
					{ispis.isError && (
						<p role="alert" className="greska-forme">
							{porukaGreske(ispis.error, PORUKE_ISPISA)}
						</p>
					)}
					<div className="potvrda-dugmad">
						<button className="dugme" type="button" onClick={() => ispis.mutate()} disabled={ispis.isPending}>
							{ispis.isPending ? 'Ispis se šalje' : 'Ispiši me'}
						</button>
						<button className="dugme-tekst" type="button" onClick={() => setPita(false)}>
							Odustani
						</button>
					</div>
				</div>
			)}
		</li>
	)
}
