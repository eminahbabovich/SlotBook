import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { serverNeOdgovara } from './forma/porukaGreske.js'
import { KalendarResursa } from './kalendar/KalendarResursa.jsx'
import { Organizacija } from './organizacije/Organizacija.jsx'
import { Organizacije } from './organizacije/Organizacije.jsx'
import { Prijava } from './prijava/Prijava.jsx'
import { Registracija } from './prijava/Registracija.jsx'
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
								<Route path="/o/:slug/r/:resursId" element={<KalendarResursa />} />
								<Route path="/rezervacije" element={<MojeRezervacije />} />
							</Route>
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
