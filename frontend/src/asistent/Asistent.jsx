import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useId, useRef, useState } from 'react'
import { ApiError } from '../api/apiFetch.js'
import { odbaciPrijedlog, otvoriRazgovor, posaljiPoruku, potvrdiPrijedlog } from '../api/asistent.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { danUZoni, nazivDana, satUZoni } from '../vrijeme/vrijeme.js'
import './asistent.css'

const PORUKE = {
	serverNeOdgovara: 'Asistent nije odgovorio, server ne radi. Termin možeš rezervisati i u kalendaru.',
	opsta: 'Asistent nije odgovorio zbog greške na serveru. Termin možeš rezervisati i u kalendaru.',
}

const PRIMJERI = ['Šta je slobodno sutra ujutro?', 'Koje su moje rezervacije?']

const pun = (greska) => greska instanceof ApiError && greska.status === 409

// Razgovor s asistentom u jednoj organizaciji. Razgovor se otvara tek s prvom porukom i živi dok je
// stranica otvorena. Asistent samo predlaže: rezervacija nastane kad korisnik pritisne dugme na karti.
export function Asistent({ organizacija }) {
	const [razgovor, setRazgovor] = useState(null)
	const [poruke, setPoruke] = useState([])
	const [tekst, setTekst] = useState('')
	const naslovId = useId()
	const poljeId = useId()
	const polje = useRef(null)

	const slanje = useMutation({
		mutationFn: async (pitanje) => {
			const id = razgovor ?? (await otvoriRazgovor(organizacija.id))
			setRazgovor(id)
			return posaljiPoruku(id, pitanje)
		},
		onSuccess: (odgovor, pitanje) => {
			setPoruke((stare) => [...stare, { ko: 'ti', tekst: pitanje }, { ko: 'asistent', ...odgovor }])
		},
		// Pitanje se vrati u polje, da ga ne treba kucati ponovo
		onError: (_, pitanje) => setTekst(pitanje),
	})

	function posalji(pitanje) {
		const cisto = pitanje.trim()
		if (cisto === '' || slanje.isPending) return
		setTekst('')
		slanje.mutate(cisto)
	}

	function noviRazgovor() {
		setRazgovor(null)
		setPoruke([])
		slanje.reset()
		polje.current?.focus()
	}

	return (
		<section className="asistent ploca" aria-labelledby={naslovId}>
			<h2 id={naslovId}>Pitaj asistenta</h2>
			<p className="asistent-uvod">
				Asistent traži slobodne termine u organizaciji {organizacija.naziv} i predloži jedan. Rezervacija nastane tek kad je
				potvrdiš na karti ispod odgovora.
			</p>

			{poruke.length === 0 && !slanje.isPending && (
				<div className="prazno asistent-prazno">
					<p>Napiši kad ti treba termin, npr. sutra poslije 14 h ili ponedjeljak ujutro.</p>
					<div className="asistent-primjeri">
						{PRIMJERI.map((p) => (
							<button key={p} type="button" className="veza-pilula" onClick={() => posalji(p)}>
								{p}
							</button>
						))}
					</div>
				</div>
			)}

			<ol className="asistent-razgovor" role="log" aria-label="Razgovor s asistentom">
				{poruke.map((p, i) => (
					<li key={i} className={`asistent-poruka asistent-poruka-${p.ko}`}>
						<span className="asistent-ko">{p.ko === 'ti' ? 'Ti' : 'Asistent'}</span>
						<p>{p.tekst}</p>
						{p.ko === 'asistent' &&
							p.prijedlozi.map((prijedlog) => <Prijedlog key={prijedlog.id} prijedlog={prijedlog} zona={organizacija.zona} />)}
					</li>
				))}
				{slanje.isPending && (
					<li className="asistent-poruka asistent-poruka-ti asistent-ceka">
						<span className="asistent-ko">Ti</span>
						<p>{slanje.variables}</p>
					</li>
				)}
			</ol>
			{slanje.isPending && <p className="asistent-trazi">Asistent traži termine...</p>}

			{slanje.isError && (
				<div className="asistent-greska">
					<p role="alert" className="greska-forme">
						{porukaGreske(slanje.error, PORUKE)}
					</p>
					{pun(slanje.error) && (
						<button type="button" className="dugme" onClick={noviRazgovor}>
							Počni novi razgovor
						</button>
					)}
				</div>
			)}

			<form
				className="asistent-forma"
				onSubmit={(e) => {
					e.preventDefault()
					posalji(tekst)
				}}
			>
				<div className="polje">
					<label htmlFor={poljeId}>Poruka asistentu</label>
					<textarea
						id={poljeId}
						ref={polje}
						rows={2}
						maxLength={1000}
						value={tekst}
						onChange={(e) => setTekst(e.target.value)}
						onKeyDown={(e) => {
							// Enter šalje, Shift+Enter je novi red, kao u svakom chatu
							if (e.key === 'Enter' && !e.shiftKey) {
								e.preventDefault()
								posalji(tekst)
							}
						}}
					/>
				</div>
				<button type="submit" className="dugme" disabled={slanje.isPending}>
					{slanje.isPending ? 'Šaljem...' : 'Pošalji'}
				</button>
			</form>
		</section>
	)
}

// Ponovljena potvrda vrati rezervaciju koja je već nastala iz istog prijedloga. Ako je u međuvremenu
// otkazana (npr. u Mojim rezervacijama, pa potvrda iz drugog taba), ne smije pisati da je termin tvoj.
function ishodPotvrde(otkazivanje, status) {
	if (otkazivanje) return 'otkazana'
	if (status === 'CONFIRMED') return 'rezervisana'
	if (status === 'PENDING') return 'ceka'
	return 'vecOtkazana'
}

// Karta prijedloga. Vrijeme je u zoni organizacije, isto kao u kalendaru.
function Prijedlog({ prijedlog, zona }) {
	const queryClient = useQueryClient()
	const [ishod, setIshod] = useState(null)
	const otkazivanje = prijedlog.akcija === 'CANCEL'
	const od = satUZoni(prijedlog.pocetak, zona)
	const dan = nazivDana(danUZoni(prijedlog.pocetak, zona))
	const opis = `${prijedlog.resurs}, ${dan}, ${od} do ${satUZoni(prijedlog.kraj, zona)}`

	const potvrda = useMutation({
		mutationFn: () => potvrdiPrijedlog(prijedlog.id),
		onSuccess: (rezultat) => {
			setIshod(ishodPotvrde(otkazivanje, rezultat.status))
			queryClient.invalidateQueries({ queryKey: ['kalendar', prijedlog.resursId] })
			queryClient.invalidateQueries({ queryKey: ['slobodno'] })
			queryClient.invalidateQueries({ queryKey: ['mojeRezervacije'] })
		},
	})
	const odbijanje = useMutation({
		mutationFn: () => odbaciPrijedlog(prijedlog.id),
		onSuccess: () => setIshod('odbacen'),
	})
	const greska = potvrda.error ?? odbijanje.error
	const salje = potvrda.isPending || odbijanje.isPending

	return (
		<article className={`asistent-prijedlog${otkazivanje ? ' asistent-prijedlog-otkazivanje' : ''}`} aria-label={`Prijedlog: ${opis}`}>
			<p className="asistent-prijedlog-sta">{otkazivanje ? 'Otkazivanje' : 'Prijedlog termina'}</p>
			<p className="asistent-prijedlog-resurs">{prijedlog.resurs}</p>
			<p className="asistent-prijedlog-vrijeme">
				{dan}, <time dateTime={prijedlog.pocetak}>{od}</time> do <time dateTime={prijedlog.kraj}>{satUZoni(prijedlog.kraj, zona)}</time>
			</p>
			{prijedlog.cekaPotvrdu && ishod === null && <p className="asistent-prijedlog-napomena">Ovaj termin još potvrđuje osoba koja vodi resurs.</p>}

			{ishod === null && (
				<div className="asistent-prijedlog-radnje">
					<button type="button" className="dugme" disabled={salje} onClick={() => potvrda.mutate()}>
						{potvrda.isPending ? (otkazivanje ? 'Otkazujem...' : 'Rezervišem...') : otkazivanje ? `Otkaži rezervaciju u ${od}` : `Rezerviši ${od}`}
					</button>
					<button type="button" className="dugme-tekst" disabled={salje} onClick={() => odbijanje.mutate()}>
						Ne treba
					</button>
				</div>
			)}
			<p role="status" className="asistent-prijedlog-ishod">
				{ishod === 'rezervisana' && `${prijedlog.resurs} je tvoj, ${dan}, od ${od}.`}
				{ishod === 'ceka' && `Rezervacija za ${od} je poslana i čeka potvrdu.`}
				{ishod === 'otkazana' && `Rezervacija u ${od} je otkazana.`}
				{ishod === 'odbacen' && 'Prijedlog je odbačen, ništa nije rezervisano.'}
				{ishod === 'vecOtkazana' && `Rezervacija iz ovog prijedloga je u međuvremenu otkazana. Za ${od} pitaj asistenta ponovo.`}
			</p>
			{greska && (
				<p role="alert" className="greska-forme">
					{porukaGreske(greska, PORUKE)}
				</p>
			)}
		</article>
	)
}
