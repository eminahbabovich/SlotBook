import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { mojeRezervacije } from '../api/rezervacije.js'
import { Karta } from '../karta/Karta.jsx'
import { SacuvajKartu } from '../karta/SacuvajKartu.jsx'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { Ucitava } from '../stanja/Ucitava.jsx'
import { opisTermina, opisTrenutka } from '../vrijeme/opis.js'
import { MojaListaCekanja } from './MojaListaCekanja.jsx'
import { Otkazivanje } from './Otkazivanje.jsx'
import { Plati } from './Plati.jsx'
import { QrDolaska } from './QrDolaska.jsx'
import { feningeUKm } from '../resursi/cijena.js'
import { DRZE_TERMIN, opisStatusa, placena } from './status.js'
import './rezervacije.css'

const PORUKE = {
	serverNeOdgovara: 'Rezervacije se nisu učitale, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Rezervacije se nisu učitale zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Stripe vraća ovamo poslije Checkouta (PaymentService: /rezervacije?uplata=...)
const POVRATAK = {
	uspjeh: 'Uplata je prošla. Rezervacija postaje potvrđena čim Stripe to javi serveru, obično za nekoliko sekundi.',
	odustao: 'Uplata nije završena i ništa nije naplaćeno. Termin čeka uplatu do roka, pa ga možeš platiti iz liste.',
}

// Poslije uplate potvrda stiže webhookom, ne s povratkom korisnika, pa lista sama pita ponovo.
// Najviše minutu: ako webhook ne stigne do tada, nešto nije u redu i čekanje ne pomaže.
const PROVJERA_UPLATE_MS = 2000
const PROVJERA_UPLATE_NAJVISE_MS = 60_000

export function MojeRezervacije() {
	const [stranica, setStranica] = useState(0)
	// Jednom, pri otvaranju: lista ne mijenja grupe dok korisnik čita
	const [sada] = useState(() => Date.now())
	const [otvorena, setOtvorena] = useState(null)
	const [povratak] = useSearchParams()
	const [uplata] = useState(() => povratak.get('uplata'))
	const [poruka, setPoruka] = useState(() => POVRATAK[uplata] ?? null)
	const queryClient = useQueryClient()
	const porukaRef = useRef(null)
	const listaRef = useRef(null)

	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['mojeRezervacije', stranica],
		queryFn: () => mojeRezervacije(stranica),
		refetchInterval: (upit) =>
			uplata === 'uspjeh' &&
			Date.now() - sada < PROVJERA_UPLATE_NAJVISE_MS &&
			upit.state.data?.rezervacije.some((r) => r.rokUplate)
				? PROVJERA_UPLATE_MS
				: false,
	})

	function zatvori() {
		const id = otvorena
		setOtvorena(null)
		listaRef.current?.querySelector(`button[data-rezervacija="${id}"]`)?.focus()
	}

	function otkazana(rezervacija) {
		setOtvorena(null)
		const povrat = placena(rezervacija) ? ` Vraća se ${feningeUKm(rezervacija.cijenaFeninga)} KM na karticu kojom je plaćeno.` : ''
		setPoruka(`Rezervacija je otkazana: ${rezervacija.resurs}, ${opisTermina(rezervacija, rezervacija.zona)}.${povrat}`)
		porukaRef.current?.focus()
	}

	function porukaListe(tekst) {
		setPoruka(tekst)
		porukaRef.current?.focus()
	}

	function nijeZaUplatu(tekst) {
		porukaListe(tekst)
		queryClient.invalidateQueries({ queryKey: ['mojeRezervacije'] })
	}

	function idiNaStranicu(nova) {
		setStranica(nova)
		setOtvorena(null)
		setPoruka(null)
	}

	// vrsta: 'prva' je najbliži termin (velika karta koja se naginje), 'arhiva' su prošle i otkazane
	const red = (r, vrsta) => (
		<Red
			key={r.id}
			vrsta={vrsta}
			rezervacija={r}
			sada={sada}
			otvorena={otvorena === r.id}
			naOtvori={() => setOtvorena(r.id)}
			naOdbijenuUplatu={nijeZaUplatu}
		>
			{otvorena === r.id && <Otkazivanje rezervacija={r} naZatvori={zatvori} naUspjeh={otkazana} />}
		</Red>
	)

	let sadrzaj
	if (isPending) {
		sadrzaj = <Ucitava tekst="Učitavaju se tvoje rezervacije" />
	} else if (isError) {
		sadrzaj = <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />
	} else if (data.rezervacije.length === 0 && stranica === 0) {
		sadrzaj = (
			<div className="prazno">
				<p>Još nemaš rezervacija. Termin biraš u kalendaru organizacije.</p>
				<Link to="/">Tvoje organizacije</Link>
			</div>
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
						<ul className="karte" aria-label="Nadolazeće">
							{nadolazece.map((r, i) => red(r, i === 0 && stranica === 0 ? 'prva' : 'nadolazeca'))}
						</ul>
					</>
				)}
				{ostale.length > 0 && (
					<>
						<h2>Prošle i otkazane</h2>
						<ul className="karte karte-arhiva" aria-label="Prošle i otkazane">
							{ostale.map((r) => red(r, 'arhiva'))}
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

function Red({ rezervacija: r, vrsta, sada, otvorena, naOtvori, naOdbijenuUplatu, children }) {
	const drziTermin = DRZE_TERMIN.includes(r.status) && Date.parse(r.pocetak) > sada
	const rokVazi = Date.parse(r.rokOtkazivanja) >= sada
	// Sliku ima smisla čuvati samo za potvrđen termin koji tek dolazi: s njom se ide na ulaz
	const zaSliku = r.status === 'CONFIRMED' && Date.parse(r.kraj) > sada

	const karta = (
		// Stanje je žig na kuponu, riječima, pa se čita i bez boje
		<Karta
			id={r.id}
			pocetak={r.pocetak}
			kraj={r.kraj}
			zona={r.zona}
			resurs={r.resurs}
			mjesto={r.organizacija}
			zig={opisStatusa(r)}
			nagib={vrsta === 'prva'}
		>
				{r.rokUplate && Date.parse(r.rokUplate) > sada && (
					<>
						<p className="napomena">Rok za uplatu: {opisTrenutka(r.rokUplate, r.zona)}. Poslije toga se termin oslobađa.</p>
						<Plati rezervacijaId={r.id} cijenaFeninga={r.cijenaFeninga} naOdbijeno={naOdbijenuUplatu} />
					</>
				)}
				{drziTermin && rokVazi && (
					<p className="napomena">
						{r.rokOtkazivanja === r.pocetak
							? 'Otkazati možeš do početka termina.'
							: // "do ponedjeljak" nije bosanski, a sklanjanje dana nije vrijedno koda, pa ide dvotačka
								`Rok za otkazivanje: ${opisTrenutka(r.rokOtkazivanja, r.zona)}.`}
					</p>
				)}
				{drziTermin && !rokVazi && (
					<p className="napomena">
						Rok za otkazivanje je bio {opisTrenutka(r.rokOtkazivanja, r.zona)}. Ako ne možeš doći, javi se osobi koja vodi termin.
					</p>
				)}
				{/* Radnje u jednom redu; QR kod i pitanje za otkazivanje se otvore ispod, preko cijele širine */}
				<div className="karta-radnje">
					{drziTermin && rokVazi && (
						<button className="dugme-tekst" type="button" data-rezervacija={r.id} aria-expanded={otvorena} onClick={naOtvori}>
							Otkaži
						</button>
					)}
					{r.status === 'CONFIRMED' && Date.parse(r.kraj) > sada && <QrDolaska rezervacija={r} />}
					{children}
				</div>
		</Karta>
	)

	return (
		<li className={`karta-stavka karta-${r.status.toLowerCase()} karta-stavka-${vrsta}`}>
			{zaSliku ? <SacuvajKartu id={r.id}>{karta}</SacuvajKartu> : karta}
		</li>
	)
}
