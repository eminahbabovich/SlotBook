import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { ekran, pratiEkran } from '../api/ekran.js'
import { ApiError } from '../api/apiFetch.js'
import { drziVezu } from '../api/tok.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { QrSlika } from '../qr/QrSlika.jsx'
import { danUZoni, nazivDana, satUZoni } from '../vrijeme/vrijeme.js'
import { rasporedResursa, stanjeTermina } from './raspored.js'
import './ekran.css'

const PORUKE = {
	serverNeOdgovara: 'Server ne odgovara. Ekran pokušava ponovo sam, ništa ne treba dirati.',
	opsta: 'Termini se nisu učitali zbog greške na serveru. Ekran pokušava ponovo sam.',
}

// Signal s toka ne osvježi više od jednom u 10 s: zgrada s više ekrana iza iste IP adrese inače
// brzo potroši granicu od 30 zahtjeva u minuti (AuthRateLimitInterceptor)
const NAJCESCE_OSVJEZAVANJE = 10_000

// Stranica /ekran/{token}: tablet ili TV na vratima, bez prijave. Uvijek tamna, jer svijetao ekran u
// hodniku navečer bode oči, i nikad se ne gasi sam (Wake Lock).
export function Ekran() {
	const { token } = useParams()
	const { data, isPending, isError, error } = useQuery({
		queryKey: ['ekran', token],
		queryFn: () => ekran(token),
		// Svakih 5 minuta i bez signala: novi dan, a i promjena radnog vremena ne šalje signal
		refetchInterval: 5 * 60_000,
		retry: (pokusaj, greska) => !(greska instanceof ApiError && greska.status === 404),
		retryDelay: 30_000,
	})
	const veza = useZivEkran(token)
	const sada = useSat(data?.pomakSata ?? 0)
	useTamnaTema()
	useBudanEkran()

	if (isPending) {
		return (
			<div className="ekran ekran-poruka">
				<p role="status">Učitava se ekran</p>
			</div>
		)
	}
	if (isError && !data) {
		const ugasen = error instanceof ApiError && error.status === 404
		return (
			<div className="ekran ekran-poruka">
				<h1>{ugasen ? 'Ovaj ekran je ugašen' : 'Ekran se nije učitao'}</h1>
				<p>{ugasen ? 'Admin organizacije ga je ugasio ili napravio novi. Novi link je u postavkama organizacije.' : porukaGreske(error, PORUKE)}</p>
			</div>
		)
	}

	return (
		<div className="ekran">
			<header className="ekran-zaglavlje">
				<div>
					<h1>{data.naziv}</h1>
					<p className="ekran-dan">{nazivDana(danUZoni(sada, data.zona))}</p>
				</div>
				<p className="ekran-sat" aria-label={`Sada je ${satUZoni(sada, data.zona)}`}>
					{satUZoni(sada, data.zona)}
				</p>
			</header>
			<div className="ekran-sadrzaj">
				{data.resursi.length === 0 ? (
					<p className="ekran-prazno">Danas nema resursa za prikaz. Admin ih dodaje u aplikaciji.</p>
				) : (
					<ul className="ekran-resursi" aria-label="Resursi">
						{data.resursi.map((r) => (
							<Resurs key={r.id} resurs={r} sada={sada} zona={data.zona} />
						))}
					</ul>
				)}
				{data.pozivniLink && (
					<aside className="ekran-qr">
						<QrSlika tekst={data.pozivniLink} opis="QR kod pozivnog linka" />
						<p>Nisi član? Skeniraj kod, uđi i rezerviši s telefona.</p>
					</aside>
				)}
			</div>
			<footer className="ekran-dno">
				<span className={veza ? 'ekran-uzivo' : 'ekran-prekid'}>{veza ? 'Uživo' : 'Veza je prekinuta, spajam se ponovo'}</span>
				{isError && <span>Zadnje osvježavanje nije uspjelo, prikazano je ranije stanje.</span>}
				<UCijeliEkran />
			</footer>
		</div>
	)
}

function Resurs({ resurs, sada, zona }) {
	const { trenutni, sljedeci, ostali } = rasporedResursa(resurs.termini, sada)
	const vrijeme = (t) => `${satUZoni(t.pocetak, zona)}–${satUZoni(t.kraj, zona)}`
	return (
		<li className="ekran-resurs">
			<h2>{resurs.naziv}</h2>
			<dl className="ekran-sada">
				<div className={trenutni ? stanjeKlasa(trenutni) : 'ekran-ne-radi'}>
					<dt>Sada</dt>
					<dd>{trenutni ? `${vrijeme(trenutni)}, ${stanjeTermina(trenutni, resurs.brojMjesta)}` : 'nema termina'}</dd>
				</div>
				<div className={sljedeci ? stanjeKlasa(sljedeci) : 'ekran-ne-radi'}>
					<dt>Sljedeći</dt>
					<dd>{sljedeci ? `${vrijeme(sljedeci)}, ${stanjeTermina(sljedeci, resurs.brojMjesta)}` : 'danas više nema'}</dd>
				</div>
			</dl>
			{ostali.length > 0 && (
				<ul className="ekran-ostatak" aria-label={`Ostatak dana: ${resurs.naziv}`}>
					{ostali.map((t) => (
						<li key={t.pocetak} className={stanjeKlasa(t)}>
							{satUZoni(t.pocetak, zona)}
							<span className="ekran-oznaka">{t.slobodnih === 0 ? 'puno' : resurs.brojMjesta > 1 ? `${t.slobodnih} mj.` : 'slobodno'}</span>
						</li>
					))}
				</ul>
			)}
		</li>
	)
}

const stanjeKlasa = (t) => (t.slobodnih === 0 ? 'ekran-zauzet' : 'ekran-slobodan')

// Sat ekrana ide po serveru: tablet s krivim satom bi inače termin koji je u toku prikazao kao prošli
function useSat(pomak) {
	const [sada, setSada] = useState(() => Date.now())
	useEffect(() => {
		const sat = setInterval(() => setSada(Date.now()), 15_000)
		return () => clearInterval(sat)
	}, [])
	return sada + pomak
}

function useZivEkran(token) {
	const queryClient = useQueryClient()
	const [veza, setVeza] = useState(true)
	const zadnje = useRef(0)
	useEffect(() => {
		const prekid = new AbortController()
		let odgodjeno = null
		const osvjezi = () => queryClient.invalidateQueries({ queryKey: ['ekran', token] })
		const naPromjenu = () => {
			const ceka = zadnje.current + NAJCESCE_OSVJEZAVANJE - Date.now()
			if (ceka <= 0) {
				zadnje.current = Date.now()
				osvjezi()
			} else if (odgodjeno === null) {
				odgodjeno = setTimeout(() => {
					odgodjeno = null
					zadnje.current = Date.now()
					osvjezi()
				}, ceka)
			}
		}
		drziVezu((opcije) => pratiEkran(token, opcije), { naPromjenu, naPonovnoSpajanje: osvjezi, naVezu: setVeza }, prekid.signal)
		return () => {
			prekid.abort()
			clearTimeout(odgodjeno)
		}
	}, [token, queryClient])
	return veza
}

// Tamna tema samo dok je ekran otvoren, bez pamćenja: izbor teme u ostatku aplikacije ostaje isti
function useTamnaTema() {
	useEffect(() => {
		const html = document.documentElement
		const ranija = html.dataset.tema
		html.dataset.tema = 'tamna'
		return () => {
			if (ranija === undefined) delete html.dataset.tema
			else html.dataset.tema = ranija
		}
	}, [])
}

// Ekran koji se ugasi poslije par minuta nije ekran na vratima. Wake Lock pusti browser kad se tab
// sakrije, pa se traži ponovo kad se vrati. Gdje ga nema (stariji iPad), uređaj treba podesiti ručno.
function useBudanEkran() {
	useEffect(() => {
		if (!('wakeLock' in navigator)) return undefined
		let brava = null
		const trazi = () => {
			if (document.visibilityState !== 'visible') return
			navigator.wakeLock.request('screen').then((b) => (brava = b)).catch(() => {})
		}
		trazi()
		document.addEventListener('visibilitychange', trazi)
		return () => {
			document.removeEventListener('visibilitychange', trazi)
			brava?.release().catch(() => {})
		}
	}, [])
}

// Cijeli ekran traži klik (browser ga ne da bez korisnika), pa je ovo dugme, a ne automatski
function UCijeliEkran() {
	const [cijeli, setCijeli] = useState(() => Boolean(document.fullscreenElement))
	useEffect(() => {
		const promjena = () => setCijeli(Boolean(document.fullscreenElement))
		document.addEventListener('fullscreenchange', promjena)
		return () => document.removeEventListener('fullscreenchange', promjena)
	}, [])
	if (cijeli || !document.documentElement.requestFullscreen) return null
	return (
		<button type="button" className="dugme-tekst" onClick={() => document.documentElement.requestFullscreen().catch(() => {})}>
			Cijeli ekran
		</button>
	)
}
