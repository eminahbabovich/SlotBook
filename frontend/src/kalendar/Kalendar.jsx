import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { kalendarResursa } from '../api/termini.js'
import { porukaGreske } from '../forma/porukaGreske.js'
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

const OZNAKA = { slobodno: 'slobodno', moje: 'moje', ceka: 'čeka potvrdu', zauzeto: 'zauzeto' }

// Stanje riječima, isto za oznaku u redu i za čitač ekrana
function oznaka(termin, zona) {
	if (termin.stanje === 'ponudjen') return `ponuđen tebi ${rokPonude(termin.rokPonude, zona)}`
	if (termin.stanje === 'zauzeto' && termin.upisId) return 'zauzeto, na listi si'
	return OZNAKA[termin.stanje]
}

// Slobodan i meni ponuđen termin se rezervišu, a na tuđi zauzet se upisuje na listu čekanja
const MOZE_REZERVISATI = ['slobodno', 'ponudjen']

// Jedan dan kao lista, i na telefonu i na desktopu. Sedmica se učitava odjednom (jedan poziv),
// a traka s danima bira koji se dan prikazuje.
export function Kalendar({ resursId, zona }) {
	// "Danas" je dan u zoni organizacije. U 00:30 po Sarajevu je za browser u Londonu još jučer.
	// Računa se jednom, pri otvaranju: kalendar otvoren preko ponoći ostaje na istoj sedmici.
	const [danas] = useState(() => danUZoni(new Date(), zona))
	const [pocetak, setPocetak] = useState(danas)
	const [dan, setDan] = useState(danas)
	// { termin, kljuc }: ključ nastaje kad korisnik izabere termin, jednom po namjeri
	const [izabran, setIzabran] = useState(null)
	const [poruka, setPoruka] = useState(null)
	const lista = useRef(null)
	const porukaRef = useRef(null)
	const dani = sedmicaOd(pocetak)
	const kljucKalendara = ['kalendar', resursId, pocetak]

	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: kljucKalendara,
		queryFn: () => kalendarResursa(resursId, pocetak, dodajDane(pocetak, 7)),
	})
	// Kad neko drugi rezerviše ili otkaže, termini se osvježe bez ponovnog učitavanja stranice
	const najavaPromjene = useZivKalendar(resursId)

	function izaberiDan(novi) {
		setDan(novi)
		setIzabran(null)
		setPoruka(null)
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
		porukaRef.current?.focus()
	}

	function uspjeh(rezervacije) {
		const [prva] = rezervacije
		const termin = opisTermina(izabran.termin, zona)
		const ceka = prva.status === 'PENDING'
		const zadnji = nazivDana(danUZoni(rezervacije.at(-1).pocetak, zona))
		setIzabran(null)
		if (rezervacije.length > 1) {
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
				{dani.map((d) => (
					<button
						key={d}
						type="button"
						className="dan"
						aria-pressed={d === dan}
						aria-label={nazivDana(d)}
						onClick={() => izaberiDan(d)}
					>
						{nazivDana(d, 'kratko').split(' ').map((dio) => (
							<span key={dio}>{dio}</span>
						))}
					</button>
				))}
			</div>

			{isPending && <Ucitavanje />}
			{isError && <NijeUcitano poruka={porukaGreske(error, PORUKE)} ponovo={() => refetch()} />}
			{data && (
				<Dan
					dan={dan}
					dani={dani}
					kalendar={data}
					lista={lista}
					naIzborDana={izaberiDan}
					naIzborTermina={izaberiTermin}
					potvrda={
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
							/>
						))
					}
					izabran={izabran?.termin}
				/>
			)}

			<p role="status" className="poruka-rezervacije" ref={porukaRef} tabIndex={-1}>
				{poruka}
			</p>
		</div>
	)
}

function Dan({ dan, dani, kalendar, lista, naIzborDana, naIzborTermina, potvrda, izabran }) {
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
					<Red key={t.pocetak} termin={t} zona={zona} naIzbor={naIzborTermina} potvrda={t.pocetak === izabran?.pocetak && potvrda} />
				))}
			</ul>
		</>
	)
}

// Potvrda ide u svoj red odmah ispod termina, da se na telefonu vidi bez skrolanja.
// data-pocetak imaju samo termini koji se rezervišu (po njima idu strelice), a data-termin svi
// koji se mogu otvoriti, da se fokus vrati na pravi red.
function Red({ termin, zona, naIzbor, potvrda }) {
	const rezervise = MOZE_REZERVISATI.includes(termin.stanje)
	return (
		<>
			<li className={`termin termin-${termin.stanje}`}>
				{rezervise || termin.stanje === 'zauzeto' ? (
					<button
						type="button"
						className="termin-red"
						data-pocetak={rezervise ? termin.pocetak : undefined}
						data-termin={termin.pocetak}
						aria-label={`${opisTermina(termin, zona)}, ${oznaka(termin, zona)}`}
						aria-expanded={Boolean(potvrda)}
						onClick={() => naIzbor(termin)}
					>
						<Sadrzaj termin={termin} zona={zona} />
					</button>
				) : (
					<div className="termin-red">
						<Sadrzaj termin={termin} zona={zona} />
					</div>
				)}
			</li>
			{potvrda && <li className="termin-potvrda">{potvrda}</li>}
		</>
	)
}

function Sadrzaj({ termin, zona }) {
	return (
		<>
			<time dateTime={termin.pocetak}>
				{satUZoni(termin.pocetak, zona)}–{satUZoni(termin.kraj, zona)}
			</time>
			<span className="termin-oznaka">{oznaka(termin, zona)}</span>
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
