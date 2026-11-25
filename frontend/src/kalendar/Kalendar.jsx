import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { kalendarResursa } from '../api/termini.js'
import { Karta } from '../karta/Karta.jsx'
import { porukaGreske } from '../forma/porukaGreske.js'
import { Plati } from '../rezervacije/Plati.jsx'
import { feningeUKm } from '../resursi/cijena.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { danUZoni, dodajDane, nazivDana, satUZoni, sedmicaOd, zonaUredjajaRazlicita } from '../vrijeme/vrijeme.js'
import { opisTermina, opisTrenutka, rokPonude } from '../vrijeme/opis.js'
import { ListaCekanja } from './ListaCekanja.jsx'
import { Potvrda } from './Potvrda.jsx'
import { useZivKalendar } from './useZivKalendar.js'

const PORUKE = {
	serverNeOdgovara: 'Termini se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Termini se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const OZNAKA = { slobodno: 'slobodno', moje: 'tvoje', ceka: 'čeka potvrdu', zauzeto: 'zauzeto' }
// Moja na čekanju kod plaćenog resursa ne čeka osoblje, nego moju uplatu
const CEKA_UPLATU = 'čeka uplatu'

// 1, 21, 31 mjesto, a 2, 5, 11, 12 mjesta
const mjesta = (broj) => `${broj} ${broj % 10 === 1 && broj % 100 !== 11 ? 'mjesto' : 'mjesta'}`

// Stanje riječima, isto za oznaku u redu i za čitač ekrana. Grupni termin kaže i koliko je mjesta
// ostalo, a pun je "popunjeno", jer "zauzeto" zvuči kao da ga je uzela jedna osoba.
function oznaka(termin, zona) {
	if (termin.stanje === 'ponudjen') return `ponuđen tebi ${rokPonude(termin.rokPonude, zona)}`
	if (termin.stanje === 'slobodno' && termin.mjesta) return `slobodno, još ${mjesta(termin.mjesta)}`
	const zauzeto = termin.pun ? 'popunjeno' : 'zauzeto'
	if (termin.stanje === 'zauzeto' && termin.upisId) return `${zauzeto}, na listi si`
	if (termin.stanje === 'zauzeto') return zauzeto
	if (termin.stanje === 'ceka' && termin.uplata) return CEKA_UPLATU
	return OZNAKA[termin.stanje]
}

// Slobodan i meni ponuđen termin se rezervišu, a na tuđi zauzet se upisuje na listu čekanja
const MOZE_REZERVISATI = ['slobodno', 'ponudjen']

// Jedan dan kao lista, i na telefonu i na desktopu. Sedmica se učitava odjednom (jedan poziv),
// a traka s danima bira koji se dan prikazuje.
// cijenaFeninga je cijena resursa: plaćen termin se ne ponavlja i čeka uplatu, ne potvrdu.
export function Kalendar({ resursId, naziv, zona, cijenaFeninga = 0 }) {
	const queryClient = useQueryClient()
	// "Danas" je dan u zoni organizacije. U 00:30 po Sarajevu je za browser u Londonu još jučer.
	// Računa se jednom, pri otvaranju: kalendar otvoren preko ponoći ostaje na istoj sedmici.
	const [danas] = useState(() => danUZoni(new Date(), zona))
	const [pocetak, setPocetak] = useState(danas)
	const [dan, setDan] = useState(danas)
	// { termin, kljuc }: ključ nastaje kad korisnik izabere termin, jednom po namjeri
	const [izabran, setIzabran] = useState(null)
	const [poruka, setPoruka] = useState(null)
	// Id upravo napravljene rezervacije koja čeka uplatu, za dugme ispod poruke
	const [zaUplatu, setZaUplatu] = useState(null)
	const [karta, setKarta] = useState(null)
	const lista = useRef(null)
	const porukaRef = useRef(null)
	const dani = sedmicaOd(pocetak)
	const kljucKalendara = ['kalendar', resursId, pocetak]

	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: kljucKalendara,
		queryFn: () => kalendarResursa(resursId, pocetak, dodajDane(pocetak, 7)),
	})
	// Dani u kojima imam termin (potvrđen ili na čekanju) dobiju tačkicu od žara u traci dana
	const mojiDani = new Set((data?.termini ?? []).filter((t) => ['moje', 'ceka'].includes(t.stanje)).map((t) => danUZoni(t.pocetak, zona)))
	// Kad neko drugi rezerviše ili otkaže, termini se osvježe bez ponovnog učitavanja stranice
	const najavaPromjene = useZivKalendar(resursId)

	function izaberiDan(novi) {
		setDan(novi)
		setIzabran(null)
		setPoruka(null)
		setZaUplatu(null)
		setKarta(null)
	}

	function pomjeriSedmicu(smjer) {
		const novi = dodajDane(pocetak, 7 * smjer)
		setPocetak(novi)
		izaberiDan(novi)
	}

	function izaberiTermin(termin) {
		setDan(danUZoni(termin.pocetak, zona))
		setIzabran({ termin, kljuc: crypto.randomUUID() })
		setPoruka(null)
		setZaUplatu(null)
		setKarta(null)
	}

	// Fokus se vraća na termin s kojeg je potvrda otvorena, da tastatura ne krene od vrha stranice
	function odustani() {
		const pocetakTermina = izabran.termin.pocetak
		setIzabran(null)
		lista.current?.querySelector(`button[data-termin="${pocetakTermina}"]`)?.focus()
	}

	function promjenaListe(tekst) {
		setIzabran(null)
		setPoruka(tekst)
		setZaUplatu(null)
		setKarta(null)
		porukaRef.current?.focus()
	}

	// Rok je prošao ili je već plaćena: poruka kaže zašto, a kalendar pokaže novo stanje
	function nijeZaUplatu(tekst) {
		promjenaListe(tekst)
		queryClient.invalidateQueries({ queryKey: ['kalendar', resursId] })
	}

	function uspjeh(rezervacije) {
		const [prva] = rezervacije
		const termin = opisTermina(izabran.termin, zona)
		const ceka = prva.status === 'PENDING'
		const zadnji = nazivDana(danUZoni(rezervacije.at(-1).pocetak, zona))
		setIzabran(null)
		// Karta se štampa za prvu rezervaciju. Kod serije poruka ispod kaže koliko ih je još.
		setKarta({ ...izabran.termin, id: prva.id, zig: ceka ? (cijenaFeninga > 0 ? CEKA_UPLATU : 'čeka potvrdu') : 'potvrđena' })
		if (ceka && cijenaFeninga > 0) {
			// Plaćen termin se ne ponavlja (Potvrda ne nudi seriju), pa je rezervacija uvijek jedna
			setPoruka(`Termin ${termin} čeka uplatu. Rok za uplatu: ${opisTrenutka(prva.rokPotvrde, zona)}. Poslije toga se termin oslobađa.`)
			setZaUplatu(prva.id)
		} else if (rezervacije.length > 1) {
			setPoruka(
				ceka
					? `Zahtjev je poslan za ${rezervacije.length} termina, svake sedmice u isto vrijeme, i svaki čeka potvrdu. Prvi: ${termin}. Zadnji: ${zadnji}.`
					: `Termini su tvoji, svake sedmice u isto vrijeme. Prvi: ${termin}. Zadnji: ${zadnji}.`,
			)
		} else {
			setPoruka(
				ceka
					? `Zahtjev je poslan, a termin ${termin} čeka potvrdu.${prva.rokPotvrde ? ` Rok za potvrdu: ${opisTrenutka(prva.rokPotvrde, zona)}.` : ''}`
					: `Termin je tvoj: ${termin}.`,
			)
		}
		// Dugme "Rezerviši" nestaje s potvrdom, pa fokus ide na poruku, ne na vrh stranice
		porukaRef.current?.focus()
	}

	return (
		<div className="kalendar">
			<p className="skriveno" aria-live="polite">
				{najavaPromjene}
			</p>
			<div className="sedmica">
				<button className="dugme-tekst" type="button" onClick={() => pomjeriSedmicu(-1)} disabled={pocetak <= danas}>
					Prethodnih 7 dana
				</button>
				<button className="dugme-tekst" type="button" onClick={() => pomjeriSedmicu(1)}>
					Sljedećih 7 dana
				</button>
			</div>
			<div className="dani">
				{dani.map((d) => {
					const imamTermin = mojiDani.has(d)
					return (
						<button
							key={d}
							type="button"
							className="dan"
							aria-pressed={d === dan}
							aria-label={imamTermin ? `${nazivDana(d)}, imaš termin` : nazivDana(d)}
							onClick={() => izaberiDan(d)}
						>
							{nazivDana(d, 'kratko').split(' ').map((dio) => (
								<span key={dio}>{dio}</span>
							))}
							{imamTermin && <span className="dan-tacka" />}
						</button>
					)
				})}
			</div>

			<div className="kalendar-tijelo">
				<div>
					{isPending && <Ucitavanje />}
					{isError && <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />}
					{data && (
						<Dan
							dan={dan}
							dani={dani}
							kalendar={cijenaFeninga > 0 ? saUplatom(data) : data}
							cijenaFeninga={cijenaFeninga}
							lista={lista}
							naIzborDana={izaberiDan}
							naIzborTermina={izaberiTermin}
							izabran={izabran?.termin}
						/>
					)}
				</div>
				{/* Karta je desno od liste (na telefonu prikovana za dno): prvo u nastajanju dok se termin
				    potvrđuje, pa odštampana kad server kaže da je rezervacija napravljena */}
				<div className={izabran ? 'kalendar-karta kalendar-karta-otvorena' : 'kalendar-karta'}>
					{data &&
						izabran &&
						(izabran.termin.stanje === 'zauzeto' ? (
							<ListaCekanja
								key={izabran.kljuc}
								resursId={resursId}
								termin={izabran.termin}
								zona={zona}
								naZatvori={odustani}
								naUspjeh={promjenaListe}
							/>
						) : (
							<Potvrda
								key={izabran.kljuc}
								resursId={resursId}
								kljucKalendara={kljucKalendara}
								// Trenutno stanje iz kalendara, ne ono u trenutku klika: preko SSE termin može postati zauzet
								termin={data.termini.find((t) => t.pocetak === izabran.termin.pocetak) ?? izabran.termin}
								kljuc={izabran.kljuc}
								zona={zona}
								termini={data.termini}
								naOdustani={odustani}
								naIzbor={izaberiTermin}
								naUspjeh={uspjeh}
								cijenaFeninga={cijenaFeninga}
								naziv={naziv}
							/>
						))}
					{karta && (
						<Karta
							id={karta.id}
							pocetak={karta.pocetak}
							kraj={karta.kraj}
							zona={zona}
							resurs={naziv}
							zig={karta.zig}
							className="karta-odstampana"
						/>
					)}
					<p role="status" className="poruka-rezervacije" ref={porukaRef} tabIndex={-1}>
						{poruka}
					</p>
					{zaUplatu && <Plati rezervacijaId={zaUplatu} cijenaFeninga={cijenaFeninga} naOdbijeno={nijeZaUplatu} />}
					{!izabran && !karta && !poruka && (
						<p className="karta-prazna">Izaberi slobodan termin, a ovdje se pojavi karta: dan, vrijeme i šta rezervišeš.</p>
					)}
				</div>
			</div>
		</div>
	)
}

// Moje na čekanju kod plaćenog resursa čekaju uplatu. Kalendar ne zna rok ni stanje uplate, samo da
// je resurs plaćen, a to je dovoljno: plaćenu na čekanju osoblje ne potvrđuje.
function saUplatom(kalendar) {
	return { ...kalendar, termini: kalendar.termini.map((t) => (t.stanje === 'ceka' ? { ...t, uplata: true } : t)) }
}

function Dan({ dan, dani, kalendar, cijenaFeninga, lista, naIzborDana, naIzborTermina, izabran }) {
	const { zona, termini } = kalendar
	const zaDan = termini.filter((t) => danUZoni(t.pocetak, zona) === dan)

	if (zaDan.length === 0) {
		const slobodniDani = dani.filter((d) => termini.some((t) => MOZE_REZERVISATI.includes(t.stanje) && danUZoni(t.pocetak, zona) === d))
		const sljedeci = slobodniDani.find((d) => d > dan) ?? slobodniDani[0]
		return (
			<div className="prazan-dan">
				<p>Ovaj dan nema termina.</p>
				{sljedeci ? (
					<button className="dugme-tekst" type="button" onClick={() => naIzborDana(sljedeci)}>
						Prvi slobodan: {nazivDana(sljedeci)}
					</button>
				) : (
					<p>U ovih 7 dana nema slobodnih termina. Pogledaj sljedećih 7.</p>
				)}
			</div>
		)
	}

	return (
		<>
			{zonaUredjajaRazlicita(zaDan.map((t) => t.pocetak), zona) && (
				<p className="napomena-blok">Vremena su po zoni {zona}, a tvoj uređaj je u drugoj zoni.</p>
			)}
			<ul className="termini" aria-label={`Termini, ${nazivDana(dan)}`} onKeyDown={strelice} ref={lista}>
				{zaDan.map((t) => (
					<Red key={t.pocetak} termin={t} zona={zona} cijenaFeninga={cijenaFeninga} naIzbor={naIzborTermina} otvoren={t.pocetak === izabran?.pocetak} />
				))}
			</ul>
		</>
	)
}

// Potvrda je u karti pored liste, a red samo kaže da je otvoren (aria-expanded).
// data-pocetak imaju samo termini koji se rezervišu (po njima idu strelice), a data-termin svi
// koji se mogu otvoriti, da se fokus vrati na pravi red.
function Red({ termin, zona, cijenaFeninga, naIzbor, otvoren }) {
	const rezervise = MOZE_REZERVISATI.includes(termin.stanje)
	return (
		<li className={`termin termin-${termin.stanje}`}>
			{rezervise || termin.stanje === 'zauzeto' ? (
				<button
					type="button"
					className="termin-red"
					data-pocetak={rezervise ? termin.pocetak : undefined}
					data-termin={termin.pocetak}
					aria-label={`${opisTermina(termin, zona)}, ${oznaka(termin, zona)}`}
					aria-expanded={otvoren}
					onClick={() => naIzbor(termin)}
				>
					<Sadrzaj termin={termin} zona={zona} cijena={rezervise ? cijenaFeninga : 0} />
				</button>
			) : (
				<div className="termin-red">
					<Sadrzaj termin={termin} zona={zona} />
				</div>
			)}
		</li>
	)
}

// Cijena se vidi samo na terminu koji se može rezervisati, da se zna koliko košta prije klika
function Sadrzaj({ termin, zona, cijena = 0 }) {
	return (
		<>
			<time dateTime={termin.pocetak}>
				{satUZoni(termin.pocetak, zona)}–{satUZoni(termin.kraj, zona)}
			</time>
			<span className="termin-desno">
				<span className="termin-oznaka">{oznaka(termin, zona)}</span>
				{cijena > 0 && <span className="termin-cijena">{feningeUKm(cijena)} KM</span>}
			</span>
		</>
	)
}

// Gore i dolje idu kroz slobodne termine (samo su oni dugmad), Home i End na prvi i zadnji.
// Tab i dalje radi, strelice su samo brži put kroz dug dan.
function strelice(dogadjaj) {
	const dugmad = [...dogadjaj.currentTarget.querySelectorAll('button[data-pocetak]')]
	const trenutno = dugmad.indexOf(document.activeElement)
	const novo = { ArrowDown: trenutno + 1, ArrowUp: trenutno - 1, Home: 0, End: dugmad.length - 1 }[dogadjaj.key]
	if (trenutno === -1 || novo === undefined || dugmad[novo] === undefined) return
	dogadjaj.preventDefault()
	dugmad[novo].focus()
}

// Kostur u obliku liste, da se raspored ne pomjeri kad termini stignu
function Ucitavanje() {
	return (
		<>
			<p className="skriveno" aria-live="polite">
				Učitavaju se termini
			</p>
			<ul className="termini" aria-hidden="true">
				{[1, 2, 3, 4].map((i) => (
					<li key={i} className="termin termin-kostur" />
				))}
			</ul>
		</>
	)
}
