import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { izuzeciResursa, noviIzuzetak, ukloniIzuzetak } from '../api/radnoVrijeme.js'
import { Polje } from '../forma/Polje.jsx'
import { greskePoPoljima, porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { opisTrenutka } from '../vrijeme/opis.js'
import { uTrenutakSPomakom } from '../vrijeme/vrijeme.js'
import '../organizacije/organizacije.css'

const PORUKE_LISTA = {
	serverNeOdgovara: 'Izuzeci se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Izuzeci se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_DODAVANJE = {
	serverNeOdgovara: 'Izuzetak nije dodan, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Izuzetak nije dodan zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_UKLANJANJE = {
	serverNeOdgovara: 'Izuzetak nije uklonjen, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Izuzetak nije uklonjen zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// 404 znači da izuzetka više nema (uklonjen u drugom tabu ili prvi pokušaj ipak prošao),
// a to je upravo ono što je korisnik htio.
async function ukloniJednom(resursId, izuzetakId) {
	try {
		await ukloniIzuzetak(resursId, izuzetakId)
	} catch (greska) {
		if (!(greska instanceof ApiError && greska.status === 404)) throw greska
	}
}

export function Izuzeci({ resursId, zona }) {
	const [poruka, setPoruka] = useState(null)
	const queryClient = useQueryClient()
	const lista = useQuery({ queryKey: ['izuzeci', resursId], queryFn: () => izuzeciResursa(resursId) })

	function osvjezi() {
		queryClient.invalidateQueries({ queryKey: ['izuzeci', resursId] })
		// Izuzetak uklanja termine iz kalendara, pa se i kalendar učita ponovo
		queryClient.invalidateQueries({ queryKey: ['kalendar'] })
	}

	const uklanjanje = useMutation({
		mutationFn: (izuzetakId) => ukloniJednom(resursId, izuzetakId),
		onSuccess: () => {
			setPoruka('Izuzetak je uklonjen.')
			osvjezi()
		},
	})

	let sadrzaj
	if (lista.isPending) {
		sadrzaj = <Ucitava tekst="Učitavaju se izuzeci" />
	} else if (lista.isError) {
		sadrzaj = <NijeUcitano poruka={porukaGreske(lista.error, PORUKE_LISTA)} ponovo={() => lista.refetch()} />
	} else if (lista.data.length === 0) {
		sadrzaj = <p className="prazno">Nema izuzetaka. Kad resurs ne radi zbog godišnjeg, praznika ili kvara, upiši to ovdje.</p>
	} else {
		sadrzaj = (
			<ul className="tabla" aria-label="Izuzeci">
				{lista.data.map((i) => {
					const opis = `${opisTrenutka(i.od, zona)} do ${opisTrenutka(i.do, zona)}`
					return (
						<li key={i.id} className="tabla-red">
							<span>
								<span className="tabla-naziv">{opis}</span>
								{i.razlog && (
									<>
										<br />
										<span className="tabla-uloga">{i.razlog}</span>
									</>
								)}
							</span>
							<button
								className="dugme-tekst"
								type="button"
								aria-label={`Ukloni izuzetak ${opis}`}
								onClick={() => uklanjanje.mutate(i.id)}
								disabled={uklanjanje.isPending}
							>
								Ukloni
							</button>
						</li>
					)
				})}
			</ul>
		)
	}

	return (
		<section>
			<h2>Izuzeci</h2>
			<p className="napomena-blok">U izuzetku kalendar ne nudi termine, ni kad je po radnom vremenu otvoreno. Prošli izuzeci se ne prikazuju.</p>
			{uklanjanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(uklanjanje.error, PORUKE_UKLANJANJE)}
				</p>
			)}
			{sadrzaj}
			<NoviIzuzetak
				resursId={resursId}
				zona={zona}
				poruka={poruka}
				naUspjeh={() => {
					setPoruka('Izuzetak je dodan.')
					osvjezi()
				}}
				naPocetak={() => setPoruka(null)}
			/>
		</section>
	)
}

const PRAZNA_FORMA = { odDan: '', odSat: '00:00', doDan: '', doSat: '00:00', razlog: '' }

function NoviIzuzetak({ resursId, zona, poruka, naUspjeh, naPocetak }) {
	const [f, setF] = useState(PRAZNA_FORMA)
	const [greskeForme, setGreskeForme] = useState({})
	const promijeni = (polje) => (vrijednost) => setF((staro) => ({ ...staro, [polje]: vrijednost }))

	const dodavanje = useMutation({
		mutationFn: (izuzetak) => noviIzuzetak(resursId, izuzetak),
		onSuccess: () => {
			setF(PRAZNA_FORMA)
			naUspjeh()
		},
	})
	// Backend zna samo startsAt i endsAt, a na ekranu je svaki od njih dan i sat
	const sServera = greskePoPoljima(dodavanje.error)
	const greske = { odDan: sServera.startsAt, doDan: sServera.endsAt, razlog: sServera.reason, ...greskeForme }

	function posalji(dogadjaj) {
		dogadjaj.preventDefault()
		naPocetak()
		const nove = {}
		const od = provjeri(f.odDan, f.odSat, 'odDan', 'odSat', 'početka', nove)
		const doKada = provjeri(f.doDan, f.doSat, 'doDan', 'doSat', 'kraja', nove)
		setGreskeForme(nove)
		if (Object.keys(nove).length > 0) {
			dodavanje.reset()
			return
		}
		dodavanje.mutate({ od, do: doKada, razlog: f.razlog })
	}

	// Dan i sat u zoni organizacije u OffsetDateTime. Greška ide u nove, pod ime polja.
	function provjeri(dan, sat, poljeDana, poljeSata, sta, nove) {
		if (!dan) {
			nove[poljeDana] = `Izaberi dan ${sta}.`
			return null
		}
		const trenutak = uTrenutakSPomakom(dan, sat || '00:00', zona)
		if (trenutak === null) nove[poljeSata] = `${sat} tog dana ne postoji, jer se sat pomjera na ljetno vrijeme. Izaberi kasniji sat.`
		return trenutak
	}

	return (
		<form className="forma" onSubmit={posalji} noValidate>
			<h3>Novi izuzetak</h3>
			<p role="status">{poruka}</p>
			{dodavanje.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(dodavanje.error, PORUKE_DODAVANJE)}
				</p>
			)}
			<div className="dan-i-sat">
				<Polje oznaka="Od dana" ime="startsAt" tip="date" vrijednost={f.odDan} promjena={promijeni('odDan')} greska={greske.odDan} />
				<Polje oznaka="Od sata" ime="startsAtTime" tip="time" vrijednost={f.odSat} promjena={promijeni('odSat')} greska={greske.odSat} />
			</div>
			<div className="dan-i-sat">
				<Polje oznaka="Do dana" ime="endsAt" tip="date" vrijednost={f.doDan} promjena={promijeni('doDan')} greska={greske.doDan} />
				<Polje oznaka="Do sata" ime="endsAtTime" tip="time" vrijednost={f.doSat} promjena={promijeni('doSat')} greska={greske.doSat} />
			</div>
			<p className="napomena">Kraj se ne računa: od 24.12. u 00:00 do 27.12. u 00:00 su tri cijela dana.</p>
			<Polje oznaka="Razlog" ime="reason" vrijednost={f.razlog} promjena={promijeni('razlog')} greska={greske.razlog} napomena="Nije obavezan. Vide ga samo admin i osoba koja vodi resurs." />
			<button className="dugme" type="submit" disabled={dodavanje.isPending}>
				{dodavanje.isPending ? 'Izuzetak se dodaje' : 'Dodaj izuzetak'}
			</button>
		</form>
	)
}
