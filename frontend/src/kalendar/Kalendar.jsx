import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { kalendarResursa } from '../api/termini.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { NijeUcitano } from '../stanja/NijeUcitano.jsx'
import { danUZoni, dodajDane, nazivDana, satUZoni, sedmicaOd, zonaUredjajaRazlicita } from '../vrijeme/vrijeme.js'

const PORUKE = {
	serverNeOdgovara: 'Termini se nisu učitali, server ne odgovara. Pokušaj ponovo za minutu.',
	opsta: 'Termini se nisu učitali zbog greške na serveru. Pokušaj ponovo za minutu.',
}

const OZNAKA = { slobodno: 'slobodno', moje: 'moje', ceka: 'čeka potvrdu', zauzeto: 'zauzeto' }

// Jedan dan kao lista, i na telefonu i na desktopu. Sedmica se učitava odjednom (jedan poziv),
// a traka s danima bira koji se dan prikazuje.
export function Kalendar({ resursId, zona }) {
	// "Danas" je dan u zoni organizacije. U 00:30 po Sarajevu je za browser u Londonu još jučer.
	// Računa se jednom, pri otvaranju: kalendar otvoren preko ponoći ostaje na istoj sedmici.
	const [danas] = useState(() => danUZoni(new Date(), zona))
	const [pocetak, setPocetak] = useState(danas)
	const [dan, setDan] = useState(danas)
	const [izabran, setIzabran] = useState(null)
	const dani = sedmicaOd(pocetak)

	const { data, isPending, isError, error, refetch } = useQuery({
		queryKey: ['kalendar', resursId, pocetak],
		queryFn: () => kalendarResursa(resursId, pocetak, dodajDane(pocetak, 7)),
	})

	function izaberiDan(novi) {
		setDan(novi)
		setIzabran(null)
	}

	function pomjeriSedmicu(smjer) {
		const novi = dodajDane(pocetak, 7 * smjer)
		setPocetak(novi)
		izaberiDan(novi)
	}

	return (
		<div className="kalendar">
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
			{data && <Dan dan={dan} dani={dani} kalendar={data} naIzborDana={izaberiDan} naIzborTermina={setIzabran} />}

			{izabran && (
				<p role="status" className="izabran">
					Izabran termin: {opisTermina(izabran, data.zona)}. Rezervacija iz kalendara još ne radi.
				</p>
			)}
		</div>
	)
}

function Dan({ dan, dani, kalendar, naIzborDana, naIzborTermina }) {
	const { zona, termini } = kalendar
	const zaDan = termini.filter((t) => danUZoni(t.pocetak, zona) === dan)

	if (zaDan.length === 0) {
		const slobodniDani = dani.filter((d) => termini.some((t) => t.stanje === 'slobodno' && danUZoni(t.pocetak, zona) === d))
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
			<ul className="termini" aria-label={`Termini, ${nazivDana(dan)}`} onKeyDown={strelice}>
				{zaDan.map((t) => (
					<li key={t.pocetak} className={`termin termin-${t.stanje}`}>
						{t.stanje === 'slobodno' ? (
							<button type="button" className="termin-red" aria-label={`${opisTermina(t, zona)}, slobodno`} onClick={() => naIzborTermina(t)}>
								<Sadrzaj termin={t} zona={zona} />
							</button>
						) : (
							<div className="termin-red">
								<Sadrzaj termin={t} zona={zona} />
							</div>
						)}
					</li>
				))}
			</ul>
		</>
	)
}

function Sadrzaj({ termin, zona }) {
	return (
		<>
			<time dateTime={termin.pocetak}>
				{satUZoni(termin.pocetak, zona)}–{satUZoni(termin.kraj, zona)}
			</time>
			<span className="termin-oznaka">{OZNAKA[termin.stanje]}</span>
		</>
	)
}

// "ponedjeljak, 19. novembar, 08:00 do 09:00": cijela rečenica za čitač ekrana.
function opisTermina(termin, zona) {
	return `${nazivDana(danUZoni(termin.pocetak, zona))}, ${satUZoni(termin.pocetak, zona)} do ${satUZoni(termin.kraj, zona)}`
}

// Gore i dolje idu kroz slobodne termine (samo su oni dugmad), Home i End na prvi i zadnji.
// Tab i dalje radi, strelice su samo brži put kroz dug dan.
function strelice(dogadjaj) {
	const dugmad = [...dogadjaj.currentTarget.querySelectorAll('button')]
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
