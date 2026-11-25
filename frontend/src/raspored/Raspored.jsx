import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { promijeniStatus, rasporedResursa } from '../api/raspored.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { STATUS } from '../rezervacije/status.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { opisTermina, opisTrenutka } from '../vrijeme/opis.js'
import { danUZoni, dodajDane, nazivDana } from '../vrijeme/vrijeme.js'
import '../kalendar/kalendar.css'
import '../rezervacije/rezervacije.css'
import './raspored.css'

const PORUKE = {
	serverNeOdgovara: 'Raspored se nije učitao, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Raspored se nije učitao zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const PORUKE_PROMJENE = {
	serverNeOdgovara: 'Nije jasno da li je promjena stigla, server ne odgovara. Osvježi raspored za minutu.',
	opsta: 'Promjena nije uspjela zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const USPJEH = {
	potvrdi: 'Rezervacija potvrđena',
	odbij: 'Zahtjev odbijen',
	dosao: 'Dolazak upisan',
	nijeDosao: 'Nedolazak upisan',
}

export function Raspored({ resursId, zona }) {
	// Jednom, pri otvaranju, u zoni organizacije
	const [sada] = useState(() => Date.now())
	const [dan, setDan] = useState(() => danUZoni(new Date(sada).toISOString(), zona))
	const [poruka, setPoruka] = useState(null)
	const porukaRef = useRef(null)

	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['raspored', resursId, dan],
		queryFn: () => rasporedResursa(resursId, dan, dodajDane(dan, 1)),
	})

	function promijenjeno(akcija, rezervacija) {
		setPoruka(`${USPJEH[akcija]}: ${rezervacija.klijent}, ${opisTermina(rezervacija, zona, { bezDana: true })}.`)
		porukaRef.current?.focus()
	}

	function idiNaDan(novi) {
		setDan(novi)
		setPoruka(null)
	}

	let sadrzaj
	if (isPending) {
		sadrzaj = <Ucitava tekst="Učitava se raspored za ovaj dan" />
	} else if (isError) {
		sadrzaj = <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	} else if (data.length === 0) {
		sadrzaj = <p className="prazno">Za ovaj dan nema rezervacija.</p>
	} else {
		sadrzaj = (
			<ul className="rezervacije" aria-label="Rezervacije">
				{data.map((r) => (
					<Red key={r.id} rezervacija={r} resursId={resursId} zona={zona} sada={sada} naUspjeh={promijenjeno} />
				))}
			</ul>
		)
	}

	return (
		<>
			<div className="raspored-dani">
				<button className="dugme-tekst" type="button" onClick={() => idiNaDan(dodajDane(dan, -1))}>
					Prethodni dan
				</button>
				<button className="dugme-tekst" type="button" onClick={() => idiNaDan(dodajDane(dan, 1))}>
					Sljedeći dan
				</button>
			</div>
			<h2>{nazivDana(dan, 'dugo')}</h2>
			<p role="status" className="poruka-rezervacije" ref={porukaRef} tabIndex={-1}>
				{poruka}
			</p>
			{sadrzaj}
		</>
	)
}

// Odbijanje i nedolazak se ne mogu vratiti, a klijent dobije obavijest, pa traže drugi klik.
// Potvrda i dolazak idu odmah.
const PITANJE = {
	odbij: { dugme: 'Da, odbij', pitanje: (r) => `Odbiti zahtjev: ${r.klijent}?` },
	nijeDosao: { dugme: 'Da, nije došla osoba', pitanje: (r) => `Upisati nedolazak: ${r.klijent}?` },
}

function Red({ rezervacija: r, resursId, zona, sada, naUspjeh }) {
	const queryClient = useQueryClient()
	const [pitanje, setPitanje] = useState(null)

	const promjena = useMutation({
		mutationFn: (akcija) => promijeniStatus(r.id, akcija),
		onSuccess: (_, akcija) => {
			setPitanje(null)
			queryClient.invalidateQueries({ queryKey: ['raspored', resursId] })
			// Odbijen zahtjev oslobađa termin u kalendaru
			queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
			naUspjeh(akcija, r)
		},
		onError: (greska) => {
			setPitanje(null)
			// 409: neko je u međuvremenu promijenio rezervaciju (klijent otkazao, drugi admin potvrdio)
			if (greska instanceof ApiError && greska.status === 409) {
				queryClient.invalidateQueries({ queryKey: ['raspored', resursId] })
			}
		},
	})

	const pocelo = Date.parse(r.pocetak) <= sada
	let akcije = null
	if (pitanje) {
		akcije = (
			<div className="potvrda-dugmad" role="group" aria-label={PITANJE[pitanje].pitanje(r)}>
				<p className="potvrda-pitanje">{PITANJE[pitanje].pitanje(r)}</p>
				<button className="dugme" type="button" onClick={() => promjena.mutate(pitanje)} disabled={promjena.isPending}>
					{PITANJE[pitanje].dugme}
				</button>
				<button className="dugme-tekst" type="button" onClick={() => setPitanje(null)}>
					Ne
				</button>
			</div>
		)
	} else if (r.status === 'PENDING') {
		akcije = (
			<div className="potvrda-dugmad">
				{/* Plaćenu potvrđuje uplata, a backend potvrdu osoblja odbija (409) */}
				{!r.cekaUplatu && (
					<button className="dugme" type="button" onClick={() => promjena.mutate('potvrdi')} disabled={promjena.isPending}>
						Potvrdi
					</button>
				)}
				<button className="dugme-tekst" type="button" onClick={() => setPitanje('odbij')}>
					Odbij
				</button>
			</div>
		)
	} else if (r.status === 'CONFIRMED' && pocelo) {
		akcije = (
			<div className="potvrda-dugmad">
				<button className="dugme" type="button" onClick={() => promjena.mutate('dosao')} disabled={promjena.isPending}>
					Upiši dolazak
				</button>
				<button className="dugme-tekst" type="button" onClick={() => setPitanje('nijeDosao')}>
					Upiši nedolazak
				</button>
			</div>
		)
	}

	return (
		<li className={`rezervacija rezervacija-${r.status.toLowerCase()}`}>
			<time dateTime={r.pocetak} className="rezervacija-sat">
				{opisTermina(r, zona, { bezDana: true })}
			</time>
			<p className="rezervacija-mjesto">{r.klijent}</p>
			<p className="rezervacija-status">{r.cekaUplatu ? 'čeka uplatu' : (STATUS[r.status] ?? r.status)}</p>
			{r.cekaUplatu && r.rokPotvrde && (
				<p className="napomena">
					Rok za uplatu: {opisTrenutka(r.rokPotvrde, zona)}. Klijent plaća karticom, a ako ne plati, termin se oslobodi.
				</p>
			)}
			{r.status === 'PENDING' && !r.cekaUplatu && r.rokPotvrde && (
				<p className="napomena">Rok za potvrdu: {opisTrenutka(r.rokPotvrde, zona)}. Poslije toga zahtjev istekne.</p>
			)}
			{r.status === 'CONFIRMED' && !pocelo && <p className="napomena">Dolazak se upisuje kad termin počne.</p>}
			{promjena.isError && (
				<p role="alert" className="greska-forme">
					{porukaGreske(promjena.error, PORUKE_PROMJENE)}
				</p>
			)}
			{akcije}
		</li>
	)
}
