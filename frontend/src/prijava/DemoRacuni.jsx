import { useQuery } from '@tanstack/react-query'
import { demoRacuni } from '../api/demo.js'

// Na javnom demu posjetilac uđe jednim klikom. Van dema (404), dok se učitava i kad ne uspije, ovaj
// dio se ne vidi: prijava radi i bez njega, pa nema smisla pokazivati grešku za nešto sporedno.
export function DemoRacuni({ prijaviKao, salje }) {
	const { data } = useQuery({ queryKey: ['demoRacuni'], queryFn: demoRacuni, staleTime: Infinity })
	if (!data) return null

	return (
		<section className="demo" aria-labelledby="demo-naslov">
			<h2 id="demo-naslov">Probaj bez naloga</h2>
			<p>Ovo je demo. Podaci se jednom dnevno vrate na početak, a mailovi ne idu nikome.</p>
			<ul className="demo-racuni">
				{data.racuni.map((racun) => (
					<li key={racun.email}>
						<button type="button" className="demo-racun" disabled={salje} onClick={() => prijaviKao(racun.email, data.lozinka)}>
							<span className="demo-ime">Uđi kao {racun.ime}</span>
							<span className="demo-opis">{racun.opis}</span>
						</button>
					</li>
				))}
			</ul>
			<p className="napomena-blok">
				Lozinka za sva tri: <code>{data.lozinka}</code>
			</p>
		</section>
	)
}
