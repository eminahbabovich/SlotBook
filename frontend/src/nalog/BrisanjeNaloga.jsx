import { useMutation } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { useSesija } from '../sesija/sesija.js'
import '../forma/forma.css'

const PORUKE = {
	serverNeOdgovara: 'Nalog nije obrisan, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Nalog nije obrisan zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// 409 nosi nazive onoga što korisnik prvo mora predati, pa se nabroje ispod poruke
function Spisak({ naslov, stavke }) {
	const id = useId()
	if (!Array.isArray(stavke) || stavke.length === 0) return null
	return (
		<div>
			<p id={id}>{naslov}</p>
			<ul aria-labelledby={id}>
				{stavke.map((naziv) => (
					<li key={naziv}>{naziv}</li>
				))}
			</ul>
		</div>
	)
}

// Lozinka ponovo, pa drugi klik: ukraden ili ostavljen otvoren laptop ne smije obrisati nalog
// jednim klikom. Poslije uspjeha sesija završi i Čuvar vodi na prijavu.
export function BrisanjeNaloga() {
	const { obrisiNalog } = useSesija()
	const [lozinka, setLozinka] = useState('')
	const [pita, setPita] = useState(false)
	const odustani = useRef(null)
	const brisanje = useMutation({ mutationFn: () => obrisiNalog(lozinka), onError: () => setPita(false) })
	const poPoljima = greskePoPoljima(brisanje.error)
	const blokirano = brisanje.error instanceof ApiError && brisanje.error.status === 409 ? brisanje.error.problem : null

	// Fokus na odustajanje, da Enter ne potvrdi nešto što se ne može vratiti
	useEffect(() => {
		if (pita) odustani.current?.focus()
	}, [pita])

	function pitaj(dogadjaj) {
		dogadjaj.preventDefault()
		setPita(true)
	}

	return (
		<form className="forma nalog-dio" onSubmit={pitaj} noValidate>
			<h2>Brisanje naloga</h2>
			<p>
				Ime i email se brišu, sesije se zatvaraju, a buduće rezervacije otkazuju. Prošle rezervacije ostaju organizacijama
				pod imenom „Obrisan nalog”. Ovo se ne može vratiti.
			</p>
			{brisanje.isError && (
				<div role="alert" className="greska-forme">
					<p>{porukaGreske(brisanje.error, PORUKE)}</p>
					{blokirano && (
						<>
							<Spisak naslov="Organizacije u kojima si jedini admin" stavke={blokirano.organizacije} />
							<Spisak naslov="Resursi koje vodiš" stavke={blokirano.resursi} />
						</>
					)}
				</div>
			)}
			<Polje
				oznaka="Trenutna lozinka"
				ime="password"
				tip="password"
				autoComplete="current-password"
				vrijednost={lozinka}
				promjena={setLozinka}
				greska={poPoljima.password}
			/>
			{pita ? (
				<div role="group" aria-label="Potvrda brisanja naloga" className="potvrda-dugmad">
					<button className="dugme" type="button" onClick={() => brisanje.mutate()} disabled={brisanje.isPending}>
						{brisanje.isPending ? 'Nalog se briše' : 'Da, obriši nalog'}
					</button>
					<button className="dugme-tekst" type="button" ref={odustani} onClick={() => setPita(false)}>
						Odustani
					</button>
				</div>
			) : (
				<button className="dugme" type="submit">
					Obriši nalog
				</button>
			)}
		</form>
	)
}
