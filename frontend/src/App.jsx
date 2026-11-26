import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Suspense, lazy, useState } from 'react'
import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { Clanovi } from './clanovi/Clanovi.jsx'
import { DolazakSKodom } from './dolazak/DolazakSKodom.jsx'
import { Nalog } from './nalog/Nalog.jsx'
import { serverNeOdgovara } from './forma/porukaGreske.js'
import { KalendarResursa } from './kalendar/KalendarResursa.jsx'
import { Organizacija } from './organizacije/Organizacija.jsx'
import { Organizacije } from './organizacije/Organizacije.jsx'
import { PrihvatanjePozivnice } from './pozivnica/PrihvatanjePozivnice.jsx'
import { PotvrdaEmaila } from './prijava/PotvrdaEmaila.jsx'
import { Prijava } from './prijava/Prijava.jsx'
import { Registracija } from './prijava/Registracija.jsx'
import { NovaLozinka } from './prijava/NovaLozinka.jsx'
import { ZaboravljenaLozinka } from './prijava/ZaboravljenaLozinka.jsx'
import { RadnoVrijemeResursa } from './radnoVrijeme/RadnoVrijemeResursa.jsx'
import { RasporedResursa } from './raspored/RasporedResursa.jsx'
import { Statistika } from './statistika/Statistika.jsx'
import { IzmjenaResursa } from './resursi/IzmjenaResursa.jsx'
import { NoviResurs } from './resursi/NoviResurs.jsx'
import { MojeRezervacije } from './rezervacije/MojeRezervacije.jsx'
import { Cuvar } from './sesija/Cuvar.jsx'
import { useSesija } from './sesija/sesija.js'
import { SesijaProvider } from './sesija/SesijaProvider.jsx'
import { Zaglavlje } from './Zaglavlje.jsx'

// Ponovni pokušaj ima smisla samo kad server nije odgovorio. 403 ili 404 se ponavljanjem
// ne mijenjaju, a korisnik bi čekao grešku nekoliko sekundi.
function napraviQueryClient() {
	return new QueryClient({
		defaultOptions: {
			queries: { retry: (pokusaj, greska) => pokusaj < 1 && serverNeOdgovara(greska), retryDelay: 1000 },
		},
	})
}

export default function App() {
	const [queryClient] = useState(napraviQueryClient)

	return (
		<QueryClientProvider client={queryClient}>
			<SesijaProvider>
				<BrowserRouter>
					<Zaglavlje />
					<main>
						<Routes>
							<Route path="/" element={<Pocetna />} />
							<Route element={<Cuvar za="odjavljene" />}>
								<Route path="/prijava" element={<Prijava />} />
								<Route path="/registracija" element={<Registracija />} />
								<Route path="/zaboravljena-lozinka" element={<ZaboravljenaLozinka />} />
							</Route>
							<Route element={<Cuvar za="prijavljene" />}>
								<Route path="/o/:slug" element={<Organizacija />} />
								<Route path="/o/:slug/clanovi" element={<Clanovi />} />
								<Route path="/o/:slug/resursi/novi" element={<NoviResurs />} />
								<Route path="/o/:slug/r/:resursId/uredi" element={<IzmjenaResursa />} />
								<Route path="/o/:slug/r/:resursId/radno-vrijeme" element={<RadnoVrijemeResursa />} />
								<Route path="/o/:slug/statistika" element={<Statistika />} />
								<Route path="/o/:slug/r/:resursId" element={<KalendarResursa />} />
								<Route path="/o/:slug/r/:resursId/raspored" element={<RasporedResursa />} />
								<Route path="/rezervacije" element={<MojeRezervacije />} />
								<Route path="/check-in/:rezervacijaId" element={<DolazakSKodom />} />
								<Route path="/pozivnica" element={<PrihvatanjePozivnice />} />
								<Route path="/nalog" element={<Nalog />} />
							</Route>
							{/* Van čuvara: link iz maila otvara neko ko se još ne može prijaviti */}
							<Route path="/potvrda-emaila" element={<PotvrdaEmaila />} />
							<Route path="/nova-lozinka" element={<NovaLozinka />} />
							<Route path="*" element={<NePostoji />} />
						</Routes>
					</main>
				</BrowserRouter>
			</SesijaProvider>
		</QueryClientProvider>
	)
}

// Na naslovnoj se landing počne skidati odmah, uporedo s glavnim kodom, a ne tek kad React dođe do
// njega. Prijavljeni na drugim adresama ga i dalje ne skidaju. Samo u buildu: u razvoju i u
// testovima nema mreže na kojoj bi se išta dobilo, a testovi bi svaki put učitavali cijeli landing.
const ucitajLanding = () => import('./landing/Landing.jsx')
if (import.meta.env.PROD && window.location.pathname === '/') ucitajLanding()
const Landing = lazy(ucitajLanding)

// Početna: prijavljeni vide svoje organizacije, a svi ostali landing. Landing ne treba server,
// pa se vidi i dok se sesija još provjerava ili kad backend spava.
function Pocetna() {
	const { status } = useSesija()
	if (status === 'prijavljen') return <Organizacije />
	return (
		<Suspense fallback={<LandingKostur />}>
			<Landing />
		</Suspense>
	)
}

// Dok stiže kod landinga: obris hera (naslov u tri reda, rečenica, dva dugmeta i prozor), da se
// stranica ne pomjeri kad sadržaj dođe
function LandingKostur() {
	return (
		<div className="landing-kostur">
			<p className="skriveno" aria-live="polite">
				Učitava se početna stranica
			</p>
			<div className="landing-kostur-tekst" aria-hidden="true">
				<span />
				<span />
				<span />
				<span className="landing-kostur-recenica" />
				<span className="landing-kostur-dugmad" />
			</div>
			<span className="landing-kostur-prozor" aria-hidden="true" />
		</div>
	)
}

function NePostoji() {
	return (
		<section>
			<h1>Stranica ne postoji</h1>
			<div className="prazno prazno-izgubljen">
				<p>Ova adresa ne vodi nigdje u SlotBooku. Možda je link star ili ima grešku u kucanju.</p>
				<Link to="/">Na početak</Link>
			</div>
		</section>
	)
}
