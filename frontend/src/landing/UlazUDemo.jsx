import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { demoRacuni } from '../api/demo.js'
import { porukaGreske } from '../forma/porukaGreske.js'
import { useSesija } from '../sesija/sesija.js'

const PORUKE = {
	serverNeOdgovara: 'Server se pokreće, pokušaj ponovo za minutu.',
	opsta: 'Ulaz u demo nije uspio zbog greške na serveru. Pokušaj ponovo za minutu.',
}

// Tri demo računa kao velika dugmad. Van dema (404) ili dok se server pokreće ostaju obična prijava i
// registracija, pa landing radi i kad backend ne odgovara.
export function UlazUDemo() {
	const { prijavi } = useSesija()
	const { data, isPending, isError, refetch } = useQuery({ queryKey: ['demoRacuni'], queryFn: demoRacuni, staleTime: Infinity, retry: false })
	const [salje, setSalje] = useState(null)
	const [greska, setGreska] = useState(null)

	async function udji(racun) {
		setSalje(racun.email)
		setGreska(null)
		try {
			await prijavi(racun.email, data.lozinka)
		} catch (g) {
			setGreska(g)
			setSalje(null)
		}
	}

	return (
		<section className="ulaz" id="demo" aria-labelledby="ulaz-naslov">
			<h2 id="ulaz-naslov" className="sekcija-naslov">
				Uđi i probaj
			</h2>
			{data ? (
				<>
					<p className="ulaz-uvod">
						Bez registracije. Podaci su izmišljeni, čitaonica i teretana s četiri sedmice rezervacija, i jednom dnevno se vrate na
						početak.
					</p>
					<ul className="ulaz-racuni">
						{data.racuni.map((racun, i) => (
							<li key={racun.email}>
								<button type="button" className={`ulaz-racun ulaz-racun-${i}`} disabled={salje !== null} onClick={() => udji(racun)}>
									<span className="ulaz-ime">{salje === racun.email ? 'Ulazi se…' : `Uđi kao ${racun.ime.split(' ')[0]}`}</span>
									<span className="ulaz-opis">{racun.opis}</span>
								</button>
							</li>
						))}
					</ul>
					{greska && (
						<p role="alert" className="greska-forme">
							{porukaGreske(greska, PORUKE)}
						</p>
					)}
				</>
			) : isPending ? (
				<p className="ulaz-uvod" aria-live="polite">
					Učitavaju se demo računi. Ako server dugo nije korišten, pokretanje traje do dvije minute.
				</p>
			) : isError ? (
				<div className="ulaz-uvod">
					<p role="alert">Server se još pokreće, pa demo računi nisu stigli. Osvježi stranicu za minutu.</p>
					<button type="button" className="dugme-tekst" onClick={() => refetch()}>
						Pokušaj ponovo
					</button>
				</div>
			) : (
				<p className="ulaz-uvod">Na ovom serveru demo nije uključen, pa se ulazi običnom prijavom.</p>
			)}
			<p className="ulaz-ostalo">
				Imaš nalog? <Link to="/prijava">Prijavi se</Link>. Nemaš? <Link to="/registracija">Napravi nalog</Link>.
			</p>
		</section>
	)
}
