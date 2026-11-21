import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { Clanovi } from './clanovi/Clanovi.jsx'
import { Nalog } from './nalog/Nalog.jsx'
import { serverNeOdgovara } from './forma/porukaGreske.js'
import { KalendarResursa } from './kalendar/KalendarResursa.jsx'
import { Organizacija } from './organizacije/Organizacija.jsx'
import { Organizacije } from './organizacije/Organizacije.jsx'
import { PrihvatanjePozivnice } from './pozivnica/PrihvatanjePozivnice.jsx'
import { PotvrdaEmaila } from './prijava/PotvrdaEmaila.jsx'
import { Prijava } from './prijava/Prijava.jsx'
import { Registracija } from './prijava/Registracija.jsx'
import { RadnoVrijemeResursa } from './radnoVrijeme/RadnoVrijemeResursa.jsx'
import { RasporedResursa } from './raspored/RasporedResursa.jsx'
import { Statistika } from './statistika/Statistika.jsx'
import { IzmjenaResursa } from './resursi/IzmjenaResursa.jsx'
import { NoviResurs } from './resursi/NoviResurs.jsx'
import { MojeRezervacije } from './rezervacije/MojeRezervacije.jsx'
import { Cuvar } from './sesija/Cuvar.jsx'
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
							<Route element={<Cuvar za="odjavljene" />}>
								<Route path="/prijava" element={<Prijava />} />
								<Route path="/registracija" element={<Registracija />} />
							</Route>
							<Route element={<Cuvar za="prijavljene" />}>
								<Route path="/" element={<Organizacije />} />
								<Route path="/o/:slug" element={<Organizacija />} />
								<Route path="/o/:slug/clanovi" element={<Clanovi />} />
								<Route path="/o/:slug/resursi/novi" element={<NoviResurs />} />
								<Route path="/o/:slug/r/:resursId/uredi" element={<IzmjenaResursa />} />
								<Route path="/o/:slug/r/:resursId/radno-vrijeme" element={<RadnoVrijemeResursa />} />
								<Route path="/o/:slug/statistika" element={<Statistika />} />
								<Route path="/o/:slug/r/:resursId" element={<KalendarResursa />} />
								<Route path="/o/:slug/r/:resursId/raspored" element={<RasporedResursa />} />
								<Route path="/rezervacije" element={<MojeRezervacije />} />
								<Route path="/pozivnica" element={<PrihvatanjePozivnice />} />
								<Route path="/nalog" element={<Nalog />} />
							</Route>
							{/* Van čuvara: link iz maila otvara neko ko se još ne može prijaviti */}
							<Route path="/potvrda-emaila" element={<PotvrdaEmaila />} />
							<Route path="*" element={<NePostoji />} />
						</Routes>
					</main>
				</BrowserRouter>
			</SesijaProvider>
		</QueryClientProvider>
	)
}

function NePostoji() {
	return (
		<section>
			<h1>Stranica ne postoji</h1>
			<p>Ova adresa ne vodi nigdje u SlotBooku.</p>
			<Link to="/">Na početak</Link>
		</section>
	)
}
