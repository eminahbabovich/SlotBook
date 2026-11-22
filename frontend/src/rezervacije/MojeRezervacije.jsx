import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { mojeRezervacije } from '../api/rezervacije.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { opisTermina, opisTrenutka } from '../vrijeme/opis.js'
import { danUZoni, nazivDana } from '../vrijeme/vrijeme.js'
import { MojaListaCekanja } from './MojaListaCekanja.jsx'
import { Otkazivanje } from './Otkazivanje.jsx'
import { DRZE_TERMIN, STATUS } from './status.js'
import './rezervacije.css'

const PORUKE = {
	serverNeOdgovara: 'Rezervacije se nisu učitale, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Rezervacije se nisu učitale zbog greške na serveru. Pokušaj ponovo za minutu.',
}

export function MojeRezervacije() {
	const [stranica, setStranica] = useState(0)
	// Jednom, pri otvaranju: lista ne mijenja grupe dok korisnik čita
	const [sada] = useState(() => Date.now())
	const [otvorena, setOtvorena] = useState(null)
	const [poruka, setPoruka] = useState(null)
	const porukaRef = useRef(null)
	const listaRef = useRef(null)

	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['mojeRezervacije', stranica],
		queryFn: () => mojeRezervacije(stranica),
	})

	function zatvori() {
		const id = otvorena
		setOtvorena(null)
		listaRef.current?.querySelector(`button[data-rezervacija="${id}"]`)?.focus()
	}

	function otkazana(rezervacija) {
		setOtvorena(null)
		setPoruka(`Rezervacija je otkazana: ${rezervacija.resurs}, ${opisTermina(rezervacija, rezervacija.zona)}.`)
		porukaRef.current?.focus()
	}

	function porukaListe(tekst) {
		setPoruka(tekst)
		porukaRef.current?.focus()
	}

	function idiNaStranicu(nova) {
		setStranica(nova)
		setOtvorena(null)
		setPoruka(null)
	}

	const red = (r) => (
		<Red key={r.id} rezervacija={r} sada={sada} otvorena={otvorena === r.id} naOtvori={() => setOtvorena(r.id)}>
			{otvorena === r.id && <Otkazivanje rezervacija={r} naZatvori={zatvori} naUspjeh={otkazana} />}
		</Red>
	)

	let sadrzaj
	if (isPending) {
		sadrzaj = <p aria-live="polite">Učitavaju se tvoje rezervacije</p>
	} else if (isError) {
		sadrzaj = <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	} else if (data.rezervacije.length === 0 && stranica === 0) {
		sadrzaj = (
			<>
				<p>Još nemaš rezervacija. Termin biraš u kalendaru organizacije.</p>
				<Link to="/">Tvoje organizacije</Link>
			</>
		)
	} else {
		// Backend šalje najkasniji termin prvo. Za nadolazeće je korisnije obrnuto: prvo ono što je najbliže.
		const nadolazece = data.rezervacije
			.filter((r) => DRZE_TERMIN.includes(r.status) && Date.parse(r.kraj) > sada)
			.sort((a, b) => Date.parse(a.pocetak) - Date.parse(b.pocetak))
		const ostale = data.rezervacije.filter((r) => !nadolazece.includes(r))
		sadrzaj = (
			<div ref={listaRef}>
				{nadolazece.length > 0 && (
					<>
						<h2>Nadolazeće</h2>
						<ul className="rezervacije" aria-label="Nadolazeće">
							{nadolazece.map(red)}
						</ul>
					</>
				)}
				{ostale.length > 0 && (
					<>
						<h2>Prošle i otkazane</h2>
						<ul className="rezervacije" aria-label="Prošle i otkazane">
							{ostale.map(red)}
						</ul>
					</>
				)}
				{data.ukupnoStranica > 1 && (
					<div className="stranice">
						<button className="dugme-tekst" type="button" onClick={() => idiNaStranicu(stranica - 1)} disabled={stranica === 0}>
							Novije
						</button>
						<button
							className="dugme-tekst"
							type="button"
							onClick={() => idiNaStranicu(stranica + 1)}
							disabled={stranica >= data.ukupnoStranica - 1}
						>
							Starije
						</button>
					</div>
				)}
			</div>
		)
	}

	return (
		<section>
			<h1>Moje rezervacije</h1>
			<p role="status" className="poruka-rezervacije" ref={porukaRef} tabIndex={-1}>
				{poruka}
			</p>
			{sadrzaj}
			<MojaListaCekanja sada={sada} naPoruku={porukaListe} />
		</section>
	)
}

function Red({ rezervacija: r, sada, otvorena, naOtvori, children }) {
	const drziTermin = DRZE_TERMIN.includes(r.status) && Date.parse(r.pocetak) > sada
	const rokVazi = Date.parse(r.rokOtkazivanja) >= sada

	return (
		<li className={`rezervacija rezervacija-${r.status.toLowerCase()}`}>
			<time dateTime={r.pocetak}>
				<span className="rezervacija-dan">{nazivDana(danUZoni(r.pocetak, r.zona))}, </span>
				<span className="rezervacija-sat">{opisTermina(r, r.zona, { bezDana: true })}</span>
			</time>
			<p className="rezervacija-mjesto">
				{r.resurs} · {r.organizacija}
			</p>
			<p className="rezervacija-status">{STATUS[r.status] ?? r.status}</p>
			{drziTermin && rokVazi && (
				<>
					<p className="napomena">
						{r.rokOtkazivanja === r.pocetak
							? 'Otkazati možeš do početka termina.'
							: // "do ponedjeljak" nije bosanski, a sklanjanje dana nije vrijedno koda, pa ide dvotačka
								`Rok za otkazivanje: ${opisTrenutka(r.rokOtkazivanja, r.zona)}.`}
					</p>
					<button className="dugme-tekst" type="button" data-rezervacija={r.id} aria-expanded={otvorena} onClick={naOtvori}>
						Otkaži
					</button>
				</>
			)}
			{drziTermin && !rokVazi && (
				<p className="napomena">
					Rok za otkazivanje je bio {opisTrenutka(r.rokOtkazivanja, r.zona)}. Ako ne možeš doći, javi se osobi koja vodi termin.
				</p>
			)}
			{children}
		</li>
	)
}
